package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(InternalAdminImportResource.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ROLE_ADMIN")
class InternalAdminImportResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void importSkuWisePrint_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]);

        mockMvc
            .perform(multipart("/internal/v1/admin/import/sku-wise-print").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    void resolveRepoRoot_returnsNullWhenScriptMissing() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            Path tempDir = Files.createTempDirectory("import-test-no-script");
            System.setProperty("user.dir", tempDir.toString());

            Method method = InternalAdminImportResource.class.getDeclaredMethod("resolveRepoRoot");
            method.setAccessible(true);
            Object result = method.invoke(null);

            assertThat(result).isNull();
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void importSkuWisePrint_scriptNotFound_returnsInternalServerError() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            Path tempDir = Files.createTempDirectory("import-test-no-repo");
            System.setProperty("user.dir", tempDir.toString());

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "catalog.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "not-empty".getBytes()
            );

            mockMvc
                .perform(multipart("/internal/v1/admin/import/sku-wise-print").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Import script not found on server"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void importSkuWisePrint_transferToIOException_returnsInternalServerError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes()) {
            @Override
            public void transferTo(java.io.File dest) throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void transferTo(java.nio.file.Path dest) throws IOException {
                throw new IOException("disk full");
            }
        };

        mockMvc
            .perform(multipart("/internal/v1/admin/import/sku-wise-print").file(file))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Could not save uploaded file: disk full"));
    }

    @Test
    void importSkuWisePrint_nonEmptyFile_runsImportWhenScriptPresent() throws Exception {
        if (!scriptExistsAtRepoRoot()) {
            return;
        }

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "minimal.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            minimalXlsxBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/internal/v1/admin/import/sku-wise-print").file(file)).andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();

        assertThat(status).isIn(200, 400);
        assertThat(body).doesNotContain("Import script not found on server");
        if (status == 200) {
            assertThat(body).contains("\"success\":true");
        } else {
            assertThat(body).contains("Import failed");
        }
    }

    private static boolean scriptExistsAtRepoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        for (Path candidate : new Path[] { cwd, cwd.getParent() }) {
            if (candidate != null && Files.isRegularFile(candidate.resolve("deploy/scripts/uat-sku-wise-print-import.py"))) {
                return true;
            }
        }
        return false;
    }

    /** Minimal valid empty xlsx (zip with required OOXML parts). */
    private static byte[] minimalXlsxBytes() throws IOException {
        Path temp = Files.createTempFile("minimal-import-", ".xlsx");
        try {
            try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(temp))) {
                writeZipEntry(zos, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    </Types>
                    """);
                writeZipEntry(zos, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
                writeZipEntry(zos, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/></sheets>
                    </workbook>
                    """);
                writeZipEntry(zos, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                    """);
            }
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeZipEntry(java.util.zip.ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new java.util.zip.ZipEntry(name));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
