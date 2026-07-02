package com.projectmind.cli;

import com.projectmind.adapter.scanner.ScanProgressFormatter;
import com.projectmind.application.service.UpdateRepositoryUseCase;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "update", description = "Incrementally update repository memory")
public class UpdateCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    private final UpdateRepositoryUseCase updateUseCase;

    public UpdateCommand(UpdateRepositoryUseCase updateUseCase) {
        this.updateUseCase = updateUseCase;
    }

    @Override
    public Integer call() {
        var changes = updateUseCase.execute(repositoryPath, ScanProgressFormatter.cliProgressBar());
        System.out.printf("Update complete: %d changes (%d added, %d modified, %d deleted)%n",
                changes.totalChanges(),
                changes.added().size(),
                changes.modified().size(),
                changes.deleted().size());
        return 0;
    }
}
