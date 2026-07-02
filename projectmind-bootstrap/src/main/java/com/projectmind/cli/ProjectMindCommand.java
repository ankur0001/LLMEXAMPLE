package com.projectmind.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Root CLI command for ProjectMind.
 */
@Component
@Command(
        name = "projectmind",
        mixinStandardHelpOptions = true,
        version = "ProjectMind 0.1.0",
        description = "Persistent AI memory system for software repositories",
        subcommands = {
                ScanCommand.class,
                UpdateCommand.class,
                DocsCommand.class,
                AskCommand.class,
                CleanCommand.class,
                StatusCommand.class,
                ResumeCommand.class,
                ExportCommand.class
        })
public class ProjectMindCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
