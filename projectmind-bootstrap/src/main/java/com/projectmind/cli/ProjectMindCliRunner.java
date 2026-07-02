package com.projectmind.cli;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Executes Picocli commands when the process is started in CLI mode.
 */
@Component
@Order(0)
public class ProjectMindCliRunner implements ApplicationRunner {

    private final ProjectMindCommand rootCommand;
    private final IFactory factory;
    private final CliExitCode exitCode;

    public ProjectMindCliRunner(ProjectMindCommand rootCommand, IFactory factory, CliExitCode exitCode) {
        this.rootCommand = rootCommand;
        this.factory = factory;
        this.exitCode = exitCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] sourceArgs = args.getSourceArgs();
        if (!CliSupport.isCliInvocation(sourceArgs)) {
            return;
        }
        int code = new CommandLine(rootCommand, factory).execute(sourceArgs);
        exitCode.set(code);
    }
}
