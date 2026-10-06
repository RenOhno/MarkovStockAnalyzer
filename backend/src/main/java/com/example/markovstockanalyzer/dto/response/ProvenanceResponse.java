package com.example.markovstockanalyzer.dto.response;

import java.util.Map;

public record ProvenanceResponse(
        String engineVersion,
        String gitCommit,
        Map<String, String> dependencyVersions,
        String normalizationVersion,
        String configurationVersion
) {
}
