package com.projectmind.core.port;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.SearchResult;
import com.projectmind.core.domain.VectorDocument;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for vector embedding storage and semantic search.
 */
public interface VectorIndexPort {

    /**
     * Indexes a batch of documents with their embeddings.
     */
    void index(Path repositoryPath, List<VectorDocument> documents);

    /**
     * Performs semantic search against indexed documents.
     *
     * @param repositoryPath repository context
     * @param query          natural language query
     * @param topK           maximum results to return
     * @return ranked search results
     */
    default List<SearchResult> search(Path repositoryPath, String query, int topK) {
        return search(repositoryPath, query, topK, null);
    }

    /**
     * Performs semantic search with optional file type filtering.
     */
    List<SearchResult> search(Path repositoryPath, String query, int topK, FileType fileTypeFilter);

    /**
     * Removes vectors for the given file paths.
     */
    void remove(Path repositoryPath, List<String> relativePaths);

    /**
     * Returns the total number of indexed documents.
     */
    long count(Path repositoryPath);
}
