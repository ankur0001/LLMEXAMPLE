package com.projectmind.cli;

import com.projectmind.core.port.MemoryManagerPort;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "clean", description = "Remove .ai-memory directory")
public class CleanCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    private final MemoryManagerPort memoryManager;

    public CleanCommand(MemoryManagerPort memoryManager) {
        this.memoryManager = memoryManager;
    }

    @Override
    public Integer call() {
        memoryManager.clean(repositoryPath);
        System.out.println("Memory cleaned for: " + repositoryPath);
        return 0;
    }
}
