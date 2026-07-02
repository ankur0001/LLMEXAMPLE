package com.projectmind.cli;

import com.projectmind.application.service.ExportRepositoryUseCase;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "export", description = "Export .ai-memory to a directory")
public class ExportCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    @Parameters(index = "1", defaultValue = ".", description = "Output directory (default: current directory)")
    Path outputDirectory;

    private final ExportRepositoryUseCase exportUseCase;

    public ExportCommand(ExportRepositoryUseCase exportUseCase) {
        this.exportUseCase = exportUseCase;
    }

    @Override
    public Integer call() {
        Path exported = exportUseCase.execute(repositoryPath, outputDirectory);
        System.out.println("Memory exported to: " + exported);
        return 0;
    }
}
