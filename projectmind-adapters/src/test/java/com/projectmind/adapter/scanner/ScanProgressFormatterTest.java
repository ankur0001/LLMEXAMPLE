package com.projectmind.adapter.scanner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanProgressFormatterTest {

    @Test
    void rendersProgressBarAndCompletesOnFinish() {
        List<String> output = new ArrayList<>();
        var callback = ScanProgressFormatter.cliProgressBar(output::add, () -> output.add("\n"));

        callback.onProgress("scan", 5, 10, "file.java");
        callback.onProgress("scan", 10, 10, "done");

        assertThat(String.join("", output)).contains("50%");
        assertThat(String.join("", output)).contains("##########");
        assertThat(String.join("", output)).contains("10/10");
    }

    @Test
    void reportsDiscoveryPhase() {
        List<String> output = new ArrayList<>();
        var callback = ScanProgressFormatter.cliProgressBar(output::add, () -> output.add("\n"));

        callback.onProgress("discover", 0, 42, "found files");

        assertThat(String.join("", output)).contains("Discovered 42 files");
    }
}
