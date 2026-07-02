package com.projectmind.adapter.scanner;

import com.projectmind.core.domain.ProgressCallback;

/**
 * Formats scan progress for CLI output including a simple progress bar.
 */
public final class ScanProgressFormatter {

    private static final int BAR_WIDTH = 30;

    private ScanProgressFormatter() {
    }

    /**
     * Returns a {@link ProgressCallback} that prints a progress bar to stdout.
     */
    public static ProgressCallback cliProgressBar() {
        return cliProgressBar(System.out::print, System.out::println);
    }

    static ProgressCallback cliProgressBar(java.util.function.Consumer<String> print, Runnable println) {
        final int[] lastPercent = {-1};
        return (phase, current, total, message) -> {
            if ("discover".equals(phase)) {
                println.run();
                print.accept(String.format("Discovered %d files%n", total));
                return;
            }
            if (total <= 0) {
                print.accept(String.format("[%s] %d - %s%n", phase, current, message));
                return;
            }
            int percent = (int) ((current * 100L) / total);
            if (percent == lastPercent[0] && current < total) {
                return;
            }
            lastPercent[0] = percent;
            int filled = (current * BAR_WIDTH) / total;
            String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
            print.accept(String.format("\r[%s] %3d%% |%s| %d/%d %s",
                    phase, percent, bar, current, total, truncate(message, 40)));
            if (current >= total) {
                println.run();
            }
        };
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value != null ? value : "";
        }
        return "..." + value.substring(value.length() - maxLen + 3);
    }
}
