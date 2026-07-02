package com.projectmind.adapter.html;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    @Test
    void rendersHeadingsListsAndBold() {
        String html = MarkdownRenderer.toHtml("""
                # Title
                - item one
                - item two

                **bold** text
                """);

        assertThat(html).contains("<h1>Title</h1>");
        assertThat(html).contains("<ul>");
        assertThat(html).contains("<li>item one</li>");
        assertThat(html).contains("<strong>bold</strong>");
    }

    @Test
    void rendersTablesAndCodeBlocks() {
        String html = MarkdownRenderer.toHtml("""
                | Name | Role |
                |------|------|
                | App | main |

                ```java
                public class App {}
                ```
                """);

        assertThat(html).contains("<table>");
        assertThat(html).contains("<th>Name</th>");
        assertThat(html).contains("<td>App</td>");
        assertThat(html).contains("<pre><code");
        assertThat(html).contains("public class App");
    }

    @Test
    void rendersLinksAndBlockquotes() {
        String html = MarkdownRenderer.toHtml("""
                > quoted text
                See [Architecture](#architecture)
                """);

        assertThat(html).contains("<blockquote>quoted text</blockquote>");
        assertThat(html).contains("<a href=\"#architecture\">Architecture</a>");
    }
}
