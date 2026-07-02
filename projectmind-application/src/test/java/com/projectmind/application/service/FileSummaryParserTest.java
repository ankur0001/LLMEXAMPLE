package com.projectmind.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileSummaryParserTest {

    @Test
    void parsesStructuredSummaryResponse() {
        var summary = FileSummaryParser.parse("App.java", """
                1) SUMMARY: Handles user registration flow.
                2) PURPOSE: REST controller for signup.
                3) KEY_CONCEPTS: UserController, signup, validation
                """);

        assertThat(summary.relativePath()).isEqualTo("App.java");
        assertThat(summary.summary()).contains("registration");
        assertThat(summary.purpose()).contains("signup");
        assertThat(summary.keyConcepts()).contains("UserController");
    }
}
