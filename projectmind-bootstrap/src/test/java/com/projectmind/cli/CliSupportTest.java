package com.projectmind.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CliSupportTest {

    @Test
    void detectsKnownSubcommands() {
        assertThat(CliSupport.isCliInvocation(new String[] {"scan", "."})).isTrue();
        assertThat(CliSupport.isCliInvocation(new String[] {"export", "/tmp/repo", "/out"})).isTrue();
    }

    @Test
    void ignoresServerStartupWithoutSubcommand() {
        assertThat(CliSupport.isCliInvocation(new String[] {})).isFalse();
        assertThat(CliSupport.isCliInvocation(new String[] {"--spring.profiles.active=test"})).isFalse();
    }
}
