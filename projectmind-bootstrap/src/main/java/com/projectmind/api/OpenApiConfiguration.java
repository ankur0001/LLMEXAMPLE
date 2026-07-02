package com.projectmind.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger documentation for the ProjectMind REST API.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI projectMindOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ProjectMind API")
                        .description("Persistent AI memory system for software repositories using Ollama")
                        .version("0.1.0")
                        .contact(new Contact().name("ProjectMind")))
                .servers(List.of(new Server().url("/").description("Current host")));
    }
}
