package com.projectmind.core.port;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.ParsedFile;

import java.nio.file.Path;

/**
 * Port for parsing source files into structured AST representations.
 */
public interface LanguageParserPort {

    /**
     * Parses a single source file.
     *
     * @param filePath   absolute path to the file
     * @param fileType   detected file type
     * @param sourceCode file content
     * @return structured parse result
     */
    ParsedFile parse(Path filePath, FileType fileType, String sourceCode);

    /**
     * Returns true if this parser supports the given file type.
     */
    boolean supports(FileType fileType);
}
