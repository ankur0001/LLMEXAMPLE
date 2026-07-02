package com.projectmind.application.service;

import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.RepositoryScannerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanRepositoryUseCaseTest {

    @Mock
    RepositoryScannerPort scanner;

    @Mock
    MemoryManagerPort memoryManager;

    @Mock
    RepositoryGraphBuilder graphBuilder;

    @Mock
    RepositoryVectorIndexer vectorIndexer;

    @Mock
    com.projectmind.core.port.ConfigurationPort configuration;

    @InjectMocks
    ScanRepositoryUseCase useCase;

    @Test
    void executeScansAndPersistsIndex() {
        Path repoPath = Path.of("/tmp/test-repo");
        var file = new RepositoryFile(
                Path.of("App.java"), repoPath.resolve("App.java"),
                com.projectmind.core.domain.FileType.JAVA,
                "abc123", 100, Instant.now());
        var index = new RepositoryIndex(repoPath, Instant.now(), 1, List.of(file));

        when(memoryManager.loadMetadata(repoPath)).thenReturn(Optional.empty());
        when(scanner.scan(any(), any(), any(), any())).thenReturn(index);

        var result = useCase.execute(repoPath, ProgressCallback.noop());

        assertThat(result.status()).isEqualTo(ScanStatus.INDEXED);
        assertThat(result.totalFiles()).isEqualTo(1);
        verify(memoryManager).initializeMemory(repoPath);

        ArgumentCaptor<RepositoryIndex> indexCaptor = ArgumentCaptor.forClass(RepositoryIndex.class);
        verify(memoryManager).saveIndex(any(), indexCaptor.capture());
        assertThat(indexCaptor.getValue().totalFiles()).isEqualTo(1);
        verify(graphBuilder).buildAndPersist(repoPath);
        verify(vectorIndexer).indexRepository(repoPath);

        ArgumentCaptor<com.projectmind.core.domain.ProjectMetadata> captor =
                ArgumentCaptor.forClass(com.projectmind.core.domain.ProjectMetadata.class);
        verify(memoryManager, org.mockito.Mockito.atLeastOnce()).saveMetadata(any(), captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).status()).isEqualTo(ScanStatus.INDEXED);
    }
}
