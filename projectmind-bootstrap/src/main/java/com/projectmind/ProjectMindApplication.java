package com.projectmind;

import com.projectmind.adapter.AdapterConfiguration;
import com.projectmind.cli.CliSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * ProjectMind application entry point.
 * Starts the REST API server by default, or runs Picocli commands when a subcommand is provided.
 */
@SpringBootApplication
@Import({AdapterConfiguration.class, UseCaseConfiguration.class})
public class ProjectMindApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ProjectMindApplication.class);
        if (CliSupport.isCliInvocation(args)) {
            application.setWebApplicationType(WebApplicationType.NONE);
            int exitCode = SpringApplication.exit(application.run(args));
            System.exit(exitCode);
        }
        application.run(args);
    }
}
