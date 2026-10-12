package com.goota.zdocspider.service;

import com.goota.zdocspider.model.dto.DocDuplicatePathResponse;
import com.goota.zdocspider.model.dto.DocDuplicateResponse;
import com.goota.zdocspider.model.dto.DocScanRequest;
import com.goota.zdocspider.model.dto.DocScanResponse;
import com.goota.zdocspider.model.entity.Doc;
import com.goota.zdocspider.repository.DocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocService {

    private final DocRepository docRepository;
    private final EmbeddingService embeddingService;
    private final SqliteVecService sqliteVecService;

    @Transactional
    public DocScanResponse scanAndSaveDocs(DocScanRequest request) {
        if (request == null || request.getRootPath() == null || request.getRootPath().trim().isEmpty()) {
            throw new IllegalArgumentException("root_path is required");
        }

        Path rootPath = Paths.get(request.getRootPath().trim());
        if (!Files.exists(rootPath)) {
            throw new IllegalArgumentException("Path does not exist: " + request.getRootPath());
        }
        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Path is not a directory: " + request.getRootPath());
        }

        boolean deep = Boolean.TRUE.equals(request.getDeep());
        List<Path> pdfFiles = findPdfFiles(rootPath, deep);

        List<Doc> savedDocs = new ArrayList<>();
        int failedCount = 0;
        int vectorsIndexedCount = 0;

        for (Path pdfPath : pdfFiles) {
            try {
                Doc doc = processAndSavePdf(pdfPath);
                savedDocs.add(doc);
                if (sqliteVecService.isVectorAvailable()) {
                    vectorsIndexedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to process PDF file: {}", pdfPath, e);
                failedCount++;
            }
        }

        return DocScanResponse.builder()
                .message("Scanning completed successfully")
                .rootPath(rootPath.toAbsolutePath().toString())
                .deep(deep)
                .totalFilesFound(pdfFiles.size())
                .totalProcessed(savedDocs.size())
                .totalFailed(failedCount)
                .totalVectorsIndexed(vectorsIndexedCount)
                .docs(savedDocs)
                .build();
    }

    @Transactional
    public DocDuplicateResponse scanAndDetectDuplicates(DocScanRequest request) {
        if (request == null || request.getRootPath() == null || request.getRootPath().trim().isEmpty()) {
            throw new IllegalArgumentException("root_path is required");
        }

        Path rootPath = Paths.get(request.getRootPath().trim());
        if (!Files.exists(rootPath)) {
            throw new IllegalArgumentException("Path does not exist: " + request.getRootPath());
        }
        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Path is not a directory: " + request.getRootPath());
        }

        boolean deep = Boolean.TRUE.equals(request.getDeep());
        List<Path> pdfFiles = findPdfFiles(rootPath, deep);

        // Tier 1: Group files by file size (0-cost I/O filter)
        Map<Long, List<Path>> filesBySize = new LinkedHashMap<>();
        for (Path pdfPath : pdfFiles) {
            try {
                long size = Files.size(pdfPath);
                filesBySize.computeIfAbsent(size, k -> new ArrayList<>()).add(pdfPath);
            } catch (IOException e) {
                log.warn("Could not read file size for {}: {}", pdfPath, e.getMessage());
                filesBySize.computeIfAbsent(-1L, k -> new ArrayList<>()).add(pdfPath);
            }
        }

        List<Doc> processedDocs = new ArrayList<>();
        int duplicatesCount = 0;
        int failedCount = 0;

        // In-memory cache for canonical checksum -> canonical filePath
        Map<String, String> canonicalMap = new HashMap<>();

        for (Map.Entry<Long, List<Path>> entry : filesBySize.entrySet()) {
            List<Path> pathsForSize = entry.getValue();

            for (Path pdfPath : pathsForSize) {
                try {
                    String fullPath = pdfPath.toAbsolutePath().toString();
                    String fileName = pdfPath.getFileName().toString();
                    String checksum = calculateMd5Checksum(pdfPath);

                    // Find if a canonical record exists in memory or in DB with the same checksum
                    String canonicalPath = canonicalMap.get(checksum);
                    if (canonicalPath == null) {
                        List<Doc> existingDocsWithChecksum = docRepository.findAllByChecksumOrderByCreatedAtAsc(checksum);
                        for (Doc d : existingDocsWithChecksum) {
                            if (!d.getFilePath().equals(fullPath)) {
                                canonicalPath = d.getFilePath();
                                break;
                            }
                        }
                    }

                    Doc doc = docRepository.findByFilePath(fullPath)
                            .orElseGet(() -> Doc.builder()
                                    .filePath(fullPath)
                                    .build());

                    doc.setFileName(fileName);
                    doc.setChecksum(checksum);

                    if (canonicalPath != null && !canonicalPath.equals(fullPath)) {
                        // This file is a duplicate of the canonical file
                        duplicatesCount++;
                        doc.setDuplicatePaths(new ArrayList<>(List.of(canonicalPath)));

                        // Populate doc content if needed
                        if (doc.getDocContent() == null) {
                            Optional<Doc> canonicalDoc = docRepository.findByFilePath(canonicalPath);
                            if (canonicalDoc.isPresent() && canonicalDoc.get().getDocContent() != null) {
                                doc.setDocContent(canonicalDoc.get().getDocContent());
                            } else {
                                doc.setDocContent(extractPdfContent(pdfPath.toFile()));
                            }
                        }

                        Doc savedDoc = docRepository.save(doc);
                        processedDocs.add(savedDoc);
                        log.info("Detected duplicate: {} -> primary {}", fullPath, canonicalPath);
                    } else {
                        // This file is the primary / canonical file
                        canonicalMap.put(checksum, fullPath);
                        doc.setDuplicatePaths(new ArrayList<>()); // primary file keeps an empty list

                        if (doc.getDocContent() == null) {
                            doc.setDocContent(extractPdfContent(pdfPath.toFile()));
                        }

                        Doc savedDoc = docRepository.save(doc);

                        // Generate vector embeddings and store in sqlite-vec for primary file
                        try {
                            float[] fileNameVector = embeddingService.generateEmbedding(fileName);
                            float[] contentVector = embeddingService.generateEmbedding(doc.getDocContent() != null ? doc.getDocContent() : "");
                            sqliteVecService.saveDocVectors(savedDoc.getId().toString(), fileNameVector, contentVector);
                        } catch (Exception e) {
                            log.warn("Failed to generate/store vectors for doc {}: {}", savedDoc.getId(), e.getMessage());
                        }

                        processedDocs.add(savedDoc);
                    }
                } catch (Exception e) {
                    log.error("Failed to process PDF duplicate detection for: {}", pdfPath, e);
                    failedCount++;
                }
            }
        }

        int uniqueCount = processedDocs.size() - duplicatesCount;

        return DocDuplicateResponse.builder()
                .message("Duplicate detection completed successfully")
                .rootPath(rootPath.toAbsolutePath().toString())
                .deep(deep)
                .totalFilesFound(pdfFiles.size())
                .totalProcessed(processedDocs.size())
                .totalDuplicatesFound(duplicatesCount)
                .totalUniqueFiles(uniqueCount)
                .totalFailed(failedCount)
                .docs(processedDocs)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DocDuplicatePathResponse> getDuplicateDocs() {
        List<Doc> docsWithDuplicates = docRepository.findAllWithDuplicatePaths();
        if (!docsWithDuplicates.isEmpty()) {
            return docsWithDuplicates.stream()
                    .filter(doc -> doc.getDuplicatePaths() != null && !doc.getDuplicatePaths().isEmpty())
                    .map(doc -> DocDuplicatePathResponse.builder()
                            .fileName(doc.getFileName())
                            .checksum(doc.getChecksum())
                            .paths(doc.getDuplicatePaths())
                            .build())
                    .toList();
        }

        // Fallback: If duplicate_paths column in DB was not yet populated, check for files sharing identical checksums in docs table
        List<Doc> allDocs = docRepository.findAll();
        Map<String, List<Doc>> groupedByChecksum = allDocs.stream()
                .filter(doc -> doc.getChecksum() != null && !doc.getChecksum().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(Doc::getChecksum, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<DocDuplicatePathResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<Doc>> entry : groupedByChecksum.entrySet()) {
            List<Doc> group = entry.getValue();
            if (group.size() > 1) {
                Doc primary = group.get(0);
                for (int i = 1; i < group.size(); i++) {
                    Doc duplicate = group.get(i);
                    result.add(DocDuplicatePathResponse.builder()
                            .fileName(duplicate.getFileName())
                            .checksum(duplicate.getChecksum())
                            .paths(List.of(primary.getFilePath()))
                            .build());
                }
            }
        }
        return result;
    }

    private List<Path> findPdfFiles(Path rootPath, boolean deep) {
        try {
            if (deep) {
                try (Stream<Path> stream = Files.walk(rootPath)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .filter(this::isPdfFile)
                            .toList();
                }
            } else {
                try (Stream<Path> stream = Files.list(rootPath)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .filter(this::isPdfFile)
                            .toList();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error scanning directory: " + rootPath, e);
        }
    }

    private boolean isPdfFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }

    private Doc processAndSavePdf(Path pdfPath) throws IOException, NoSuchAlgorithmException {
        File file = pdfPath.toFile();
        String fileName = pdfPath.getFileName().toString();
        String fullPath = pdfPath.toAbsolutePath().toString();

        String checksum = calculateMd5Checksum(pdfPath);
        String docContent = extractPdfContent(file);

        Doc doc = docRepository.findByFilePath(fullPath)
                .orElseGet(() -> Doc.builder()
                        .filePath(fullPath)
                        .build());

        doc.setFileName(fileName);
        doc.setChecksum(checksum);
        doc.setDocContent(docContent);

        Doc savedDoc = docRepository.save(doc);

        // Generate vector embeddings and store in sqlite-vec
        try {
            float[] fileNameVector = embeddingService.generateEmbedding(fileName);
            float[] contentVector = embeddingService.generateEmbedding(docContent != null ? docContent : "");
            sqliteVecService.saveDocVectors(savedDoc.getId().toString(), fileNameVector, contentVector);
            log.info("Stored sqlite-vec embeddings for doc id: {}", savedDoc.getId());
        } catch (Exception e) {
            log.warn("Failed to generate/store vectors for doc {}: {}", savedDoc.getId(), e.getMessage());
        }

        return savedDoc;
    }

    private String calculateMd5Checksum(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] digest = md.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    private String extractPdfContent(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Doc> searchDocs(String query, int page, int size) {
        // Enforce max page size of 100
        int validatedSize = size;
        if (validatedSize <= 0) {
            validatedSize = 10;
        } else if (validatedSize > 100) {
            validatedSize = 100;
        }

        int validatedPage = Math.max(page, 0);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                validatedPage,
                validatedSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );

        if (query == null || query.trim().isEmpty()) {
            return docRepository.findAll(pageable);
        }

        List<String> terms = parseSmartQueryTerms(query);
        if (terms.isEmpty()) {
            return docRepository.findAll(pageable);
        }

        org.springframework.data.jpa.domain.Specification<Doc> spec = buildSearchSpecification(terms);
        return docRepository.findAll(spec, pageable);
    }

    private List<String> parseSmartQueryTerms(String query) {
        List<String> terms = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"|(\\S+)").matcher(query.trim());
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                String phrase = matcher.group(1).trim();
                if (!phrase.isEmpty()) {
                    terms.add(phrase);
                }
            } else if (matcher.group(2) != null) {
                String token = matcher.group(2).trim();
                if (!token.isEmpty()) {
                    terms.add(token);
                }
            }
        }
        return terms;
    }

    private org.springframework.data.jpa.domain.Specification<Doc> buildSearchSpecification(List<String> terms) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> andPredicates = new ArrayList<>();

            for (String term : terms) {
                String pattern = "%" + term.toLowerCase() + "%";
                jakarta.persistence.criteria.Predicate fileNameMatch = cb.like(
                        cb.lower(root.get("fileName")),
                        pattern
                );
                jakarta.persistence.criteria.Predicate docContentMatch = cb.like(
                        cb.lower(root.get("docContent")),
                        pattern
                );
                andPredicates.add(cb.or(fileNameMatch, docContentMatch));
            }

            return cb.and(andPredicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public List<com.goota.zdocspider.model.dto.VectorSearchResult> searchDocsByContentVector(String queryText, int limit) {
        float[] queryVector = embeddingService.generateEmbedding(queryText);
        return sqliteVecService.searchByContentVector(queryVector, limit);
    }

    public List<com.goota.zdocspider.model.dto.VectorSearchResult> searchDocsByFileNameVector(String queryText, int limit) {
        float[] queryVector = embeddingService.generateEmbedding(queryText);
        return sqliteVecService.searchByFileNameVector(queryVector, limit);
    }
}
