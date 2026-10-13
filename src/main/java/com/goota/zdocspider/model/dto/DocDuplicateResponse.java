package com.goota.zdocspider.model.dto;

import com.goota.zdocspider.model.entity.Doc;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocDuplicateResponse {
    private String message;
    private String rootPath;
    private boolean deep;
    private int totalFilesFound;
    private int totalProcessed;
    private int totalDuplicatesFound;
    private int totalUniqueFiles;
    private int totalFailed;
    private List<Doc> docs;
}
