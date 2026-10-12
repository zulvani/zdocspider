package com.goota.zdocspider;

import com.goota.zdocspider.model.dto.DocScanRequest;
import com.goota.zdocspider.model.dto.DocScanResponse;
import com.goota.zdocspider.model.dto.VectorSearchResult;
import com.goota.zdocspider.model.entity.Doc;
import com.goota.zdocspider.repository.DocRepository;
import com.goota.zdocspider.service.DocService;
import com.goota.zdocspider.service.SqliteVecService;
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
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SqliteVecIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private com.goota.zdocspider.controller.DocController docController;

    @Autowired
    private DocService docService;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private SqliteVecService sqliteVecService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(docController).build();
        docRepository.deleteAll();
        sqliteVecService.clearAll();
    }

    @AfterEach
    void tearDown() {
        docRepository.deleteAll();
        sqliteVecService.clearAll();
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
    void testSqliteVecAvailabilityAndVirtualTable() {
        assertTrue(sqliteVecService.isVectorAvailable(), "sqlite-vec extension should be loaded and available");
    }

    @Test
    void testScanPdfSavesVectorsAndAllowsVectorSearch() throws Exception {
        File samplePdf = tempDir.resolve("machine_learning_paper.pdf").toFile();
        createSamplePdf(samplePdf, "Artificial intelligence and neural networks research findings.");

        DocScanRequest request = DocScanRequest.builder()
                .rootPath(tempDir.toString())
                .deep(false)
                .build();

        DocScanResponse response = docService.scanAndSaveDocs(request);
        assertNotNull(response);
        assertEquals(1, response.getTotalProcessed());
        assertEquals(1, response.getTotalVectorsIndexed());
        assertEquals(1, response.getDocs().size());

        Doc savedDoc = response.getDocs().get(0);
        String docId = savedDoc.getId().toString();

        // 1. Search by content vector
        List<VectorSearchResult> contentMatches = docService.searchDocsByContentVector(
                "Artificial intelligence and neural networks research findings.", 5);
        assertFalse(contentMatches.isEmpty(), "Vector search on content should return matching document");
        assertEquals(docId, contentMatches.get(0).getDocId());
        assertTrue(contentMatches.get(0).getDistance() < 0.05, "Exact match should have near-zero distance");

        // 2. Search by file name vector
        List<VectorSearchResult> fileNameMatches = docService.searchDocsByFileNameVector("machine_learning_paper.pdf", 5);
        assertFalse(fileNameMatches.isEmpty(), "Vector search on file name should return matching document");
        assertEquals(docId, fileNameMatches.get(0).getDocId());
        assertTrue(fileNameMatches.get(0).getDistance() < 0.05, "Exact match should have near-zero distance");

        // 3. Test API endpoint for vector search
        mockMvc.perform(get("/api/docs/vector-search")
                        .param("q", "Artificial intelligence and neural networks research findings.")
                        .param("field", "content")
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value(docId))
                .andExpect(jsonPath("$[0].distance").isNumber());
    }
}
