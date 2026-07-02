package com.projectmind.cli;

import java.util.Set;

/**
 * Detects when the JVM was invoked in CLI mode versus server mode.
 */
public final class CliSupport {

    static final Set<String> COMMANDS = Set.of(
            "scan", "update", "docs", "ask", "clean", "status", "resume", "export");

    private CliSupport() {
    }

    public static boolean isCliInvocation(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        String first = stripLeadingFlags(args);
        return first != null && COMMANDS.contains(first);
    }

    private static String stripLeadingFlags(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("-")) {
                continue;
            }
            return arg;
        }
        return null;
    }
}
