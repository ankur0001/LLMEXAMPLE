package com.projectmind.cli;

import com.projectmind.adapter.scanner.ScanProgressFormatter;
import com.projectmind.application.service.ResumeScanRepositoryUseCase;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "resume", description = "Resume an interrupted repository scan")
public class ResumeCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    private final ResumeScanRepositoryUseCase resumeUseCase;

    public ResumeCommand(ResumeScanRepositoryUseCase resumeUseCase) {
        this.resumeUseCase = resumeUseCase;
    }

    @Override
    public Integer call() {
        var result = resumeUseCase.execute(repositoryPath, ScanProgressFormatter.cliProgressBar());
        System.out.printf("Resume complete: %d files indexed (status: %s)%n",
                result.totalFiles(), result.status());
        return 0;
    }
}
