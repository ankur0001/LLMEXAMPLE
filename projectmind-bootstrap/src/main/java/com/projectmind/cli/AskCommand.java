package com.projectmind.cli;

import com.projectmind.application.service.AskQuestionUseCase;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "ask", description = "Ask a question about the repository")
public class AskCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository path")
    Path repositoryPath;

    @Parameters(index = "1", description = "Question")
    String question;

    @Option(names = {"-m", "--model"}, description = "Ollama model name (optional; auto-selects when omitted)")
    String model;

    @Option(names = {"-s", "--stream"}, description = "Stream answer tokens as they are generated")
    boolean stream;

    private final AskQuestionUseCase askUseCase;

    public AskCommand(AskQuestionUseCase askUseCase) {
        this.askUseCase = askUseCase;
    }

    @Override
    public Integer call() {
        if (stream) {
            System.out.print("\nAnswer: ");
            var response = askUseCase.execute(repositoryPath, question, model, System.out::print);
            System.out.println();
            printSources(response.sourceFiles());
            return 0;
        }

        var response = askUseCase.execute(repositoryPath, question, model);
        System.out.println("\nAnswer:\n" + response.answer());
        printSources(response.sourceFiles());
        return 0;
    }

    private static void printSources(java.util.List<String> sourceFiles) {
        if (!sourceFiles.isEmpty()) {
            System.out.println("\nSources: " + String.join(", ", sourceFiles));
        }
    }
}
