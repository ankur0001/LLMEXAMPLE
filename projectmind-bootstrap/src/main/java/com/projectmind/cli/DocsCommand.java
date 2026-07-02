package com.projectmind.cli;

import com.projectmind.application.service.GenerateDocumentationUseCase;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "docs", description = "Generate HTML documentation")
public class DocsCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    private final GenerateDocumentationUseCase docsUseCase;

    public DocsCommand(GenerateDocumentationUseCase docsUseCase) {
        this.docsUseCase = docsUseCase;
    }

    @Override
    public Integer call() {
        var output = docsUseCase.execute(repositoryPath);
        System.out.println("Documentation generated: " + output);
        return 0;
    }
}
