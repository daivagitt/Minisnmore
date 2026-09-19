package com.miniandmore.app.web.rest.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Runs the SKU-wise print catalog import (deploy/scripts/prod-sku-wise-print-import.py) against an
 * uploaded spreadsheet. Reached only via the store service's internal proxy — never exposed directly.
 */
@RestController
@RequestMapping("/internal/v1/admin/import")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class InternalAdminImportResource {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAdminImportResource.class);
    private static final String SCRIPT_RELATIVE_PATH = "deploy/scripts/prod-sku-wise-print-import.py";

    @PostMapping("/sku-wise-print")
    public ResponseEntity<Map<String, Object>> importSkuWisePrint(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        Path repoRoot = resolveRepoRoot();
        if (repoRoot == null) {
            LOG.error("Could not locate {} from working directory {}", SCRIPT_RELATIVE_PATH, System.getProperty("user.dir"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Import script not found on server"));
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile("sku-wise-import-", ".xlsx");
            file.transferTo(tempFile);
        } catch (IOException e) {
            LOG.error("Failed to stage uploaded spreadsheet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Could not save uploaded file: " + e.getMessage()));
        }

        try {
            Path importScript = repoRoot.resolve(SCRIPT_RELATIVE_PATH);
            if (!Files.isRegularFile(importScript)) {
                importScript = repoRoot.resolve("deploy/scripts/uat-sku-wise-print-import.py");
            }
            ProcessBuilder pb = new ProcessBuilder(
                "python3",
                importScript.toString(),
                "--file",
                tempFile.toString()
            );
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);
            // pm2 (itself Node) sets these on the JVM's env for its own IPC; a nested `node` child
            // process inheriting them tries to init IPC on a stale fd and aborts. Strip before spawning.
            pb.environment().remove("NODE_CHANNEL_FD");
            pb.environment().remove("NODE_CHANNEL_SERIALIZATION_MODE");
            pb.environment().put("GATEWAY_URL", "http://127.0.0.1:9180");
            pb.environment().put("ADMIN_USER", "admin");
            pb.environment().put("ADMIN_PASS", "admin6");

            Process process = pb.start();
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(outputBuffer);
                } catch (IOException ignored) {
                    // process ended; stream closed
                }
            });
            reader.start();

            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
            reader.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(5));
            String output = outputBuffer.toString();

            if (!finished) {
                process.destroyForcibly();
                LOG.error("SKU-wise import timed out after 5 minutes");
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of("error", "Import timed out", "output", output));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                LOG.error("SKU-wise import failed (exit {}): {}", exitCode, output);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Import failed", "details", output));
            }

            LOG.info("SKU-wise import completed successfully");
            return ResponseEntity.ok(Map.of("success", true, "message", "SKU-wise print import completed successfully", "output", output));
        } catch (IOException e) {
            LOG.error("Failed to run import script", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to run import script: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Import process interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Import process interrupted"));
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                LOG.warn("Could not clean up temp file {}", tempFile, e);
            }
        }
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        for (Path candidate : new Path[] { cwd, cwd.getParent() }) {
            if (candidate == null) {
                continue;
            }
            if (Files.isRegularFile(candidate.resolve(SCRIPT_RELATIVE_PATH))) {
                return candidate;
            }
            if (Files.isRegularFile(candidate.resolve("deploy/scripts/uat-sku-wise-print-import.py"))) {
                return candidate;
            }
        }
        return null;
    }
}
