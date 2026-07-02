package com.projectmind.cli;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures the Picocli exit code for {@link org.springframework.boot.SpringApplication#exit}.
 */
@Component
public class CliExitCode implements ExitCodeGenerator {

    private final AtomicInteger exitCode = new AtomicInteger(0);

    public void set(int code) {
        exitCode.set(code);
    }

    @Override
    public int getExitCode() {
        return exitCode.get();
    }
}
