package com.projectmind.cli;

import com.projectmind.application.service.MemoryOverviewUseCase;
import com.projectmind.application.service.RepositoryStatusUseCase;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "status", description = "Show repository scan status")
public class StatusCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    @Option(names = {"-v", "--verbose"}, description = "Show memory overview statistics")
    boolean verbose;

    private final RepositoryStatusUseCase statusUseCase;
    private final MemoryOverviewUseCase overviewUseCase;

    public StatusCommand(RepositoryStatusUseCase statusUseCase, MemoryOverviewUseCase overviewUseCase) {
        this.statusUseCase = statusUseCase;
        this.overviewUseCase = overviewUseCase;
    }

    @Override
    public Integer call() {
        statusUseCase.execute(repositoryPath).ifPresentOrElse(
                meta -> {
                    System.out.printf(
                            "Project: %s%nStatus: %s%nFiles: %d/%d%nLast scanned: %s%nLast updated: %s%n",
                            meta.name(), meta.status(), meta.indexedFiles(),
                            meta.totalFiles(), meta.lastScannedAt(), meta.lastUpdatedAt());
                    if (verbose) {
                        overviewUseCase.execute(repositoryPath).ifPresent(overview ->
                                System.out.printf(
                                        "%nMemory overview:%n"
                                                + "  Graph nodes: %d%n"
                                                + "  Graph edges: %d%n"
                                                + "  File summaries: %d%n"
                                                + "  History entries: %d%n",
                                        overview.graphNodeCount(),
                                        overview.graphEdgeCount(),
                                        overview.fileSummaryCount(),
                                        overview.historyEntryCount()));
                    }
                },
                () -> System.out.println("No memory found for: " + repositoryPath));
        return 0;
    }
}
