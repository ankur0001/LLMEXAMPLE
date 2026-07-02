package com.projectmind.application.service;

import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.port.IncrementalUpdatePort;
import com.projectmind.core.port.MemoryManagerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateRepositoryUseCaseTest {

    @Mock
    IncrementalUpdatePort incrementalUpdate;
    @Mock
    MemoryManagerPort memoryManager;
    @Mock
    RepositoryGraphBuilder graphBuilder;
    @Mock
    RepositoryVectorIndexer vectorIndexer;
    @Mock
    RepositorySummaryGenerator summaryGenerator;
    @Mock
    GenerateDocumentationUseCase documentationUseCase;

    @InjectMocks
    UpdateRepositoryUseCase useCase;

    @Test
    void appliesIncrementalUpdatesWhenChangesDetected() {
        Path repo = Path.of("/tmp/repo");
        RepositoryIndex stored = new RepositoryIndex(
                repo, Instant.now(), 1, List.of(file("App.java", "a")));
        ProjectMetadata metadata = new ProjectMetadata(
                "demo", repo.toString(), ScanStatus.INDEXED,
                Instant.now(), Instant.now(), Instant.now(),
                1, 1, "test", Map.of());
        FileChangeSet changes = new FileChangeSet(List.of(), List.of(file("App.java", "b")), List.of());

        when(memoryManager.loadIndex(repo)).thenReturn(Optional.of(stored));
        when(memoryManager.loadMetadata(repo)).thenReturn(Optional.of(metadata));
        when(incrementalUpdate.detectChanges(repo, stored)).thenReturn(changes);
        when(memoryManager.loadDocumentationSections(repo)).thenReturn(Optional.of(List.of()));

        useCase.execute(repo, ProgressCallback.noop());

        verify(memoryManager).saveIndex(eq(repo), any(RepositoryIndex.class));
        verify(graphBuilder).updateChangedFiles(repo, changes);
        verify(summaryGenerator).summarizeChanges(repo, changes);
        verify(vectorIndexer).updateChangedFiles(repo, changes);
        verify(documentationUseCase).regenerateChanged(repo, changes.affectedPaths());
        verify(memoryManager).saveHistorySnapshot(eq(repo), any());
    }

    @Test
    void skipsProcessingWhenNoChanges() {
        Path repo = Path.of("/tmp/repo");
        RepositoryIndex stored = new RepositoryIndex(
                repo, Instant.now(), 1, List.of(file("App.java", "a")));
        ProjectMetadata metadata = new ProjectMetadata(
                "demo", repo.toString(), ScanStatus.INDEXED,
                Instant.now(), Instant.now(), Instant.now(),
                1, 1, "test", Map.of());

        when(memoryManager.loadIndex(repo)).thenReturn(Optional.of(stored));
        when(memoryManager.loadMetadata(repo)).thenReturn(Optional.of(metadata));
        when(incrementalUpdate.detectChanges(repo, stored)).thenReturn(
                new FileChangeSet(List.of(), List.of(), List.of()));

        useCase.execute(repo, ProgressCallback.noop());

        verify(graphBuilder, never()).updateChangedFiles(any(), any());
        verify(summaryGenerator, never()).summarizeChanges(any(), any());
        verify(vectorIndexer, never()).updateChangedFiles(any(), any());
    }

    private static RepositoryFile file(String path, String hash) {
        return new RepositoryFile(
                Path.of(path),
                Path.of("/tmp/repo").resolve(path),
                FileType.JAVA,
                hash,
                100,
                Instant.now());
    }
}
