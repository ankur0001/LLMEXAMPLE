package com.projectmind.cli;

import com.projectmind.adapter.scanner.ScanProgressFormatter;
import com.projectmind.application.service.ScanRepositoryUseCase;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "scan", description = "Scan a repository and build persistent memory")
public class ScanCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    private final ScanRepositoryUseCase scanUseCase;

    public ScanCommand(ScanRepositoryUseCase scanUseCase) {
        this.scanUseCase = scanUseCase;
    }

    @Override
    public Integer call() {
        var result = scanUseCase.execute(repositoryPath, ScanProgressFormatter.cliProgressBar());
        System.out.printf("Scan complete: %d files indexed (status: %s)%n",
                result.totalFiles(), result.status());
        return 0;
    }
}
