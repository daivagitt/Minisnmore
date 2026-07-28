package com.miniandmore.app.web.rest.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/import")
public class AdminImportResource {

    private static final Logger logger = LoggerFactory.getLogger(AdminImportResource.class);

    @PostMapping("/sku-wise-print")
    @PreAuthorize("hasAuthority(\"" + "ROLE_ADMIN" + "\")")
    public ResponseEntity<?> importSkuWisePrint(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("sku-wise-import-", ".xlsx");
            file.transferTo(tempFile);

            // Run Python script
            ProcessBuilder pb = new ProcessBuilder(
                "python3",
                "deploy/scripts/uat-sku-wise-print-import.py"
            );
            pb.directory(Path.of(System.getProperty("user.dir")).toFile());
            pb.environment().put("GATEWAY_URL", "http://127.0.0.1:8081");
            pb.environment().put("ADMIN_USER", "admin");
            pb.environment().put("ADMIN_PASS", "admin6");

            Process process = pb.start();
            int exitCode = process.waitFor();

            // Read output
            String output = new String(process.getInputStream().readAllBytes());
            String error = new String(process.getErrorStream().readAllBytes());

            // Clean up
            Files.delete(tempFile);

            if (exitCode != 0) {
                logger.error("SKU-wise import failed: {}", error);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                        "error", "Import failed",
                        "details", error
                    ));
            }

            logger.info("SKU-wise import completed successfully");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "SKU-wise print import completed successfully",
                "output", output
            ));

        } catch (IOException e) {
            logger.error("File upload error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "File upload failed: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Import process interrupted: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Import process interrupted"));
        }
    }
}
