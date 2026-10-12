package com.goota.zdocspider.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Duplicate document file paths response")
public class DocDuplicatePathResponse {

    @JsonProperty("file_name")
    @Schema(description = "File name of the document", example = "duplicate_doc.pdf")
    private String fileName;

    @JsonProperty("checksum")
    @Schema(description = "MD5 cryptographic checksum", example = "3b719468923a1a9f09199859f936a61e")
    private String checksum;

    @JsonProperty("paths")
    @Schema(description = "List of duplicate file paths", example = "[\"/path/to/canonical_doc.pdf\"]")
    private List<String> paths;
}
