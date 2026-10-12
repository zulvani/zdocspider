package com.goota.zdocspider;

import com.goota.zdocspider.model.dto.DocScanRequest;
import com.goota.zdocspider.model.dto.DocScanResponse;
import com.goota.zdocspider.model.entity.Doc;
import com.goota.zdocspider.repository.DocRepository;
import com.goota.zdocspider.service.DocService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class DocScanIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private com.goota.zdocspider.controller.DocController docController;

    @Autowired
    private DocService docService;

    @Autowired
    private DocRepository docRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(docController).build();
        docRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        docRepository.deleteAll();
    }

    private void createSamplePdf(File file, String textContent) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(textContent);
                contentStream.endText();
            }

            document.save(file);
        }
    }

    @Test
    void testScanNonDeep() throws IOException {
        File rootPdf = tempDir.resolve("sample1.pdf").toFile();
        createSamplePdf(rootPdf, "Hello Root World");

        Path subDir = tempDir.resolve("nested");
        Files.createDirectories(subDir);
        File nestedPdf = subDir.resolve("sample2.pdf").toFile();
        createSamplePdf(nestedPdf, "Hello Subdir World");

        DocScanRequest request = DocScanRequest.builder()
                .rootPath(tempDir.toString())
                .deep(false)
                .build();

        DocScanResponse response = docService.scanAndSaveDocs(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalFilesFound());
        assertEquals(1, response.getTotalProcessed());
        assertEquals(0, response.getTotalFailed());

        List<Doc> allDocs = docRepository.findAll();
        assertEquals(1, allDocs.size());
        Doc savedDoc = allDocs.get(0);
        assertEquals("sample1.pdf", savedDoc.getFileName());
        assertNotNull(savedDoc.getChecksum());
        assertTrue(savedDoc.getDocContent().contains("Hello Root World"));
    }

    @Test
    void testScanDeep() throws IOException {
        File rootPdf = tempDir.resolve("sample1.pdf").toFile();
        createSamplePdf(rootPdf, "Hello Root World");

        Path subDir = tempDir.resolve("nested");
        Files.createDirectories(subDir);
        File nestedPdf = subDir.resolve("sample2.pdf").toFile();
        createSamplePdf(nestedPdf, "Hello Subdir World");

        DocScanRequest request = DocScanRequest.builder()
                .rootPath(tempDir.toString())
                .deep(true)
                .build();

        DocScanResponse response = docService.scanAndSaveDocs(request);

        assertNotNull(response);
        assertEquals(2, response.getTotalFilesFound());
        assertEquals(2, response.getTotalProcessed());
        assertEquals(0, response.getTotalFailed());

        List<Doc> allDocs = docRepository.findAll();
        assertEquals(2, allDocs.size());
    }

    @Test
    void testInvalidPathThrowsException() {
        DocScanRequest request = DocScanRequest.builder()
                .rootPath("/non/existing/path/12345")
                .deep(false)
                .build();

        assertThrows(IllegalArgumentException.class, () -> docService.scanAndSaveDocs(request));
    }

    @Test
    void testScanApiEndpoint() throws Exception {
        File rootPdf = tempDir.resolve("api_sample.pdf").toFile();
        createSamplePdf(rootPdf, "API Endpoint PDF Content");

        String jsonPayload = String.format("{\"root_path\": \"%s\", \"deep\": false}", tempDir.toString().replace("\\", "\\\\"));

        mockMvc.perform(post("/api/doc/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFilesFound").value(1))
                .andExpect(jsonPath("$.totalProcessed").value(1))
                .andExpect(jsonPath("$.totalFailed").value(0))
                .andExpect(jsonPath("$.docs[0].fileName").value("api_sample.pdf"))
                .andExpect(jsonPath("$.docs[0].checksum").isNotEmpty());
    }

    @Test
    void testScanApiEndpointInvalidPath() throws Exception {
        String jsonPayload = "{\"root_path\": \"/non/existing/path/xyz\", \"deep\": false}";

        mockMvc.perform(post("/api/doc/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void testSearchDocsWithSmartQueryAndPagination() throws Exception {
        docRepository.save(Doc.builder()
                .fileName("financial_report_2024.pdf")
                .docContent("Annual financial statement containing revenue and expenses summary")
                .filePath("/tmp/financial_report_2024.pdf")
                .checksum("chk1")
                .build());

        docRepository.save(Doc.builder()
                .fileName("user_manual.pdf")
                .docContent("Instructions for operating the financial system software")
                .filePath("/tmp/user_manual.pdf")
                .checksum("chk2")
                .build());

        docRepository.save(Doc.builder()
                .fileName("hr_policy.pdf")
                .docContent("Company guidelines and health insurance procedures")
                .filePath("/tmp/hr_policy.pdf")
                .checksum("chk3")
                .build());

        // 1. Search in file_name
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/doc")
                        .param("q", "financial_report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("financial_report_2024.pdf"));

        // 2. Search in doc_content
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/doc")
                        .param("q", "guidelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("hr_policy.pdf"));

        // 3. Smart query with multiple keywords matching either fileName or docContent
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/doc")
                        .param("q", "financial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 4. Exact phrase matching with quotes
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/doc")
                        .param("q", "\"financial statement\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("financial_report_2024.pdf"));

        // 5. Pagination with page size and max page size cap (e.g. size=1, page=0)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/doc")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(1));

        // 6. Max page size capping at 100
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/doc")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(100));
    }

    @Test
    void testDuplicateDetectionEndpoint() throws Exception {
        File file1 = tempDir.resolve("original_doc.pdf").toFile();
        createSamplePdf(file1, "Duplicate Test PDF Unique Content");

        // Copy file1 content into file2 (exact duplicate)
        File file2 = tempDir.resolve("duplicate_doc.pdf").toFile();
        Files.copy(file1.toPath(), file2.toPath());

        // File 3: unique content
        File file3 = tempDir.resolve("unique_doc.pdf").toFile();
        createSamplePdf(file3, "Completely different content in third document");

        String jsonPayload = String.format("{\"root_path\": \"%s\", \"deep\": false}", tempDir.toString().replace("\\", "\\\\"));

        mockMvc.perform(post("/api/doc/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFilesFound").value(3))
                .andExpect(jsonPath("$.totalProcessed").value(3))
                .andExpect(jsonPath("$.totalDuplicatesFound").value(1))
                .andExpect(jsonPath("$.totalUniqueFiles").value(2));

        // Verify in database that duplicatePaths field contains the primary document's path
        List<Doc> allDocs = docRepository.findAll();
        assertEquals(3, allDocs.size());

        long duplicatesInDb = allDocs.stream().filter(d -> !d.getDuplicatePaths().isEmpty()).count();
        assertEquals(1, duplicatesInDb);

        Doc dupDoc = allDocs.stream().filter(d -> !d.getDuplicatePaths().isEmpty()).findFirst().orElseThrow();
        assertEquals(1, dupDoc.getDuplicatePaths().size());
        assertTrue(dupDoc.getDuplicatePaths().get(0).contains("original_doc.pdf") || dupDoc.getDuplicatePaths().get(0).contains("duplicate_doc.pdf"));
    }

    @Test
    void testDuplicateApiEndpointInvalidPath() throws Exception {
        String jsonPayload = "{\"root_path\": \"/invalid/nonexistent/path\", \"deep\": false}";

        mockMvc.perform(post("/api/doc/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void testGetDuplicateEndpointDirectlyFromDatabase() throws Exception {
        // Save records directly to database: one with duplicate_paths and one without
        docRepository.save(Doc.builder()
                .fileName("canonical_document.pdf")
                .filePath("/docs/canonical_document.pdf")
                .checksum("checksum-abc-123")
                .duplicatePaths(new java.util.ArrayList<>())
                .build());

        docRepository.save(Doc.builder()
                .fileName("duplicate_document.pdf")
                .filePath("/backup/duplicate_document.pdf")
                .checksum("checksum-abc-123")
                .duplicatePaths(new java.util.ArrayList<>(List.of("/docs/canonical_document.pdf")))
                .build());

        mockMvc.perform(get("/api/doc/duplicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].file_name").value("duplicate_document.pdf"))
                .andExpect(jsonPath("$[0].checksum").value("checksum-abc-123"))
                .andExpect(jsonPath("$[0].paths.length()").value(1))
                .andExpect(jsonPath("$[0].paths[0]").value("/docs/canonical_document.pdf"));
    }

    @Test
    void testGetDuplicateEndpointAfterPostScanAndDetect() throws Exception {
        File file1 = tempDir.resolve("master_file.pdf").toFile();
        createSamplePdf(file1, "Content for master and duplicate testing");

        File file2 = tempDir.resolve("clone_file.pdf").toFile();
        Files.copy(file1.toPath(), file2.toPath());

        File file3 = tempDir.resolve("distinct_file.pdf").toFile();
        createSamplePdf(file3, "Distinct and standalone file content");

        String jsonPayload = String.format("{\"root_path\": \"%s\", \"deep\": false}", tempDir.toString().replace("\\", "\\\\"));

        // First trigger duplicate detection scan
        mockMvc.perform(post("/api/doc/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDuplicatesFound").value(1));

        // Now test GET /api/doc/duplicate (reading from database without scanning)
        mockMvc.perform(get("/api/doc/duplicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].file_name").isNotEmpty())
                .andExpect(jsonPath("$[0].checksum").isNotEmpty())
                .andExpect(jsonPath("$[0].paths.length()").value(1));
    }
}

