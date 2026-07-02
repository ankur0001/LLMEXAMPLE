package com.projectmind.core.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaPromptTemplatesTest {

    @Test
    void fileSummaryIncludesPathAndSource() {
        String prompt = OllamaPromptTemplates.fileSummary("src/App.java", "public class App {}");

        assertThat(prompt).contains("src/App.java");
        assertThat(prompt).contains("public class App {}");
        assertThat(prompt).contains("SUMMARY");
        assertThat(prompt).contains("KEY_CONCEPTS");
    }

    @Test
    void questionAnswerIncludesContextAndQuestion() {
        String prompt = OllamaPromptTemplates.questionAnswer("What does App do?", "class App {}");

        assertThat(prompt).contains("What does App do?");
        assertThat(prompt).contains("class App {}");
        assertThat(prompt).contains("ANSWER");
    }

    @Test
    void repositoryOverviewPromptIncludesProjectName() {
        String prompt = OllamaPromptTemplates.repositoryOverview("demo-app", "Controllers: UserController");

        assertThat(prompt).contains("demo-app");
        assertThat(prompt).contains("UserController");
        assertThat(prompt).contains("MARKDOWN");
    }
}
