package com.projectmind.cli;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import picocli.CommandLine.IFactory;
import picocli.spring.PicocliSpringFactory;

/**
 * Picocli wiring for Spring-managed CLI commands.
 */
@Configuration
public class CliConfiguration {

    @Bean
    IFactory picocliFactory(ApplicationContext applicationContext) {
        return new PicocliSpringFactory(applicationContext);
    }
}
