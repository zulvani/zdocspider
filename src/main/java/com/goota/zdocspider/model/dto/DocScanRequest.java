package com.goota.zdocspider.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocScanRequest {

    @JsonProperty("root_path")
    @JsonAlias({"rootPath", "root_path"})
    private String rootPath;

    @JsonProperty("deep")
    private Boolean deep;
}
