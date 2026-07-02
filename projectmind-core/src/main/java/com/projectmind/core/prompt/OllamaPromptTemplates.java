package com.projectmind.core.prompt;

/**
 * Prompt templates for Ollama summarization and Q&amp;A tasks.
 */
public final class OllamaPromptTemplates {

    private OllamaPromptTemplates() {
    }

    /**
     * Prompt for generating a structured file summary.
     */
    public static String fileSummary(String relativePath, String sourceCode) {
        String truncated = truncate(sourceCode, 12000);
        return """
                You are ProjectMind, an expert software analyst. Summarize the following source file.
                Respond in plain text with three sections:
                1) SUMMARY: one concise paragraph
                2) PURPOSE: one sentence describing the file's role
                3) KEY_CONCEPTS: comma-separated list of important classes, patterns, or APIs

                File: %s

                SOURCE:
                %s

                ANALYSIS:""".formatted(relativePath, truncated);
    }

    /**
     * Prompt for answering a repository question from retrieved context.
     */
    public static String questionAnswer(String question, String context) {
        return """
                You are ProjectMind, an AI assistant with deep knowledge of a software repository.
                Answer the question based ONLY on the provided source code and summaries.
                If you cannot answer from the context, say so clearly.
                Cite specific file names when referencing code.

                CONTEXT:
                %s

                QUESTION: %s

                ANSWER:""".formatted(context, question);
    }

    /**
     * Prompt for generating a repository overview narrative section.
     */
    public static String repositoryOverview(String projectName, String context) {
        return """
                You are ProjectMind, an expert technical writer.
                Write a concise Repository Overview section in Markdown for the project "%s".
                Use ONLY the facts in the context below. Include:
                - What the project appears to do
                - Primary technologies and structure
                - How a new developer should orient themselves
                Keep it under 250 words. Do not invent features not supported by the context.

                CONTEXT:
                %s

                MARKDOWN:""".formatted(projectName, truncate(context, 8000));
    }

    /**
     * Prompt for generating an architecture narrative section.
     */
    public static String architectureNarrative(String projectName, String context) {
        return """
                You are ProjectMind, an expert software architect.
                Write a concise Architecture section in Markdown for "%s".
                Describe layers, major components, and data flow based ONLY on the context.
                Mention controllers, services, repositories, and integrations when present.
                Keep it under 300 words.

                CONTEXT:
                %s

                MARKDOWN:""".formatted(projectName, truncate(context, 8000));
    }

    /**
     * Prompt for generating a developer guide section.
     */
    public static String developerGuide(String projectName, String context) {
        return """
                You are ProjectMind, a senior developer mentor.
                Write a Developer Guide section in Markdown for "%s".
                Include setup hints, where to find key code, and suggested reading order.
                Base your answer ONLY on the context. Keep it under 250 words.

                CONTEXT:
                %s

                MARKDOWN:""".formatted(projectName, truncate(context, 6000));
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text != null ? text : "";
        }
        return text.substring(0, maxLength) + "\n... (truncated)";
    }
}
