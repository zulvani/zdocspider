package com.goota.zdocspider.controller;

import com.goota.zdocspider.model.dto.DocDuplicatePathResponse;
import com.goota.zdocspider.model.dto.DocDuplicateResponse;
import com.goota.zdocspider.model.dto.DocScanRequest;
import com.goota.zdocspider.model.dto.DocScanResponse;
import com.goota.zdocspider.service.DocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
@Tag(name = "Document Controller", description = "APIs for scanning and managing documents")
public class DocController {

    private final DocService docService;

    @PostMapping("/scan")
    @Operation(summary = "Scan documents", description = "Scan all PDF files starting from root_path and extract content, file name, and checksum into docs table")
    public ResponseEntity<DocScanResponse> scanDocuments(@RequestBody DocScanRequest request) {
        DocScanResponse response = docService.scanAndSaveDocs(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/duplicate")
    @Operation(summary = "Detect duplicate documents", description = "Scan all PDF files starting from root_path, detect duplicates using two-tier fast checksum matching, and store duplicate paths in docs table")
    public ResponseEntity<DocDuplicateResponse> detectDuplicates(@RequestBody DocScanRequest request) {
        DocDuplicateResponse response = docService.scanAndDetectDuplicates(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/duplicate")
    @Operation(summary = "Get duplicate documents from database", description = "Return duplicate file paths read directly from the database without filesystem scanning")
    public ResponseEntity<List<DocDuplicatePathResponse>> getDuplicates() {
        List<DocDuplicatePathResponse> response = docService.getDuplicateDocs();
        return ResponseEntity.ok(response);
    }

    @GetMapping("")
    @Operation(summary = "Search documents", description = "Search documents by smart query in file_name and/or doc_content with pagination")
    public ResponseEntity<com.goota.zdocspider.model.dto.PageResponse<com.goota.zdocspider.model.entity.Doc>> searchDocuments(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        org.springframework.data.domain.Page<com.goota.zdocspider.model.entity.Doc> result = docService
                .searchDocs(query, page, size);
        return ResponseEntity.ok(com.goota.zdocspider.model.dto.PageResponse.from(result));
    }

    @GetMapping("/vector-search")
    @Operation(summary = "Vector search documents", description = "Perform semantic vector search using sqlite-vec")
    public ResponseEntity<java.util.List<com.goota.zdocspider.model.dto.VectorSearchResult>> searchVector(
            @RequestParam("q") String query,
            @RequestParam(value = "field", defaultValue = "content") String field,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        if ("file_name".equalsIgnoreCase(field) || "filename".equalsIgnoreCase(field)) {
            return ResponseEntity.ok(docService.searchDocsByFileNameVector(query, limit));
        }
        return ResponseEntity.ok(docService.searchDocsByContentVector(query, limit));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<DocScanResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(DocScanResponse.builder()
                .message(e.getMessage())
                .build());
    }
}
