package com.projectmind.adapter.vector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * JSON DTOs for the ChromaDB HTTP API (v1).
 */
final class ChromaApiModels {

    private ChromaApiModels() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateCollectionRequest(String name, Boolean getOrCreate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateCollectionResponse(String id, String name) {
    }

    record AddRequest(
            List<String> ids,
            List<String> documents,
            List<List<Float>> embeddings,
            List<Map<String, Object>> metadatas) {
    }

    record QueryRequest(
            List<List<Float>> queryEmbeddings,
            int nResults,
            Map<String, Object> where) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QueryResponse(
            List<List<String>> ids,
            List<List<String>> documents,
            List<List<Double>> distances,
            List<List<Map<String, Object>>> metadatas) {
    }

    record DeleteRequest(List<String> ids) {
    }
}
