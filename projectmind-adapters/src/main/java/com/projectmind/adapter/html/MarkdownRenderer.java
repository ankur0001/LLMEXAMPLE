package com.projectmind.adapter.html;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a subset of Markdown used by ProjectMind documentation sections to HTML.
 */
final class MarkdownRenderer {

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern UNORDERED = Pattern.compile("^[-*]\\s+(.+)$");
    private static final Pattern ORDERED = Pattern.compile("^\\d+\\.\\s+(.+)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\|[-| :]+\\|$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^>\\s?(.*)$");

    private MarkdownRenderer() {
    }

    static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String[] lines = markdown.split("\n", -1);
        var state = new RenderState();

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.startsWith("```")) {
                state.flushParagraph();
                state.flushList();
                state.flushTable();
                if (state.inCode) {
                    state.blocks.add("<pre><code class=\"language-text\">"
                            + escape(state.codeBlock.toString().trim())
                            + "</code></pre>");
                    state.codeBlock.setLength(0);
                    state.inCode = false;
                } else {
                    state.inCode = true;
                }
                continue;
            }

            if (state.inCode) {
                state.codeBlock.append(rawLine).append('\n');
                continue;
            }

            if (line.isEmpty()) {
                state.flushParagraph();
                state.flushList();
                state.flushTable();
                continue;
            }

            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                state.flushParagraph();
                state.flushList();
                state.flushTable();
                int level = heading.group(1).length();
                state.blocks.add("<h" + level + ">" + inline(heading.group(2)) + "</h" + level + ">");
                continue;
            }

            if (TABLE_ROW.matcher(line).matches()) {
                state.flushParagraph();
                state.flushList();
                if (!TABLE_SEP.matcher(line).matches()) {
                    state.tableRows.add(line);
                }
                continue;
            }

            Matcher quote = BLOCKQUOTE.matcher(line);
            if (quote.matches()) {
                state.flushParagraph();
                state.flushList();
                state.flushTable();
                state.blocks.add("<blockquote>" + inline(quote.group(1)) + "</blockquote>");
                continue;
            }

            Matcher unordered = UNORDERED.matcher(line);
            if (unordered.matches()) {
                state.flushParagraph();
                state.flushTable();
                if (!state.listItems.isEmpty() && state.orderedList) {
                    state.flushList();
                }
                state.orderedList = false;
                state.listItems.add(unordered.group(1));
                continue;
            }

            Matcher ordered = ORDERED.matcher(line);
            if (ordered.matches()) {
                state.flushParagraph();
                state.flushTable();
                if (!state.listItems.isEmpty() && !state.orderedList) {
                    state.flushList();
                }
                state.orderedList = true;
                state.listItems.add(ordered.group(1));
                continue;
            }

            state.flushList();
            state.flushTable();
            if (!state.paragraph.isEmpty()) {
                state.paragraph.append(' ');
            }
            state.paragraph.append(line);
        }

        state.flushParagraph();
        state.flushList();
        state.flushTable();
        if (state.inCode && !state.codeBlock.isEmpty()) {
            state.blocks.add("<pre><code class=\"language-text\">"
                    + escape(state.codeBlock.toString().trim())
                    + "</code></pre>");
        }

        return String.join("\n", state.blocks);
    }

    private static final class RenderState {
        private final List<String> blocks = new ArrayList<>();
        private final StringBuilder paragraph = new StringBuilder();
        private final List<String> listItems = new ArrayList<>();
        private boolean orderedList;
        private final List<String> tableRows = new ArrayList<>();
        private final StringBuilder codeBlock = new StringBuilder();
        private boolean inCode;

        private void flushParagraph() {
            if (!paragraph.isEmpty()) {
                blocks.add("<p>" + inline(paragraph.toString().trim()) + "</p>");
                paragraph.setLength(0);
            }
        }

        private void flushList() {
            if (listItems.isEmpty()) {
                return;
            }
            String tag = orderedList ? "ol" : "ul";
            var sb = new StringBuilder("<").append(tag).append(">");
            for (String item : listItems) {
                sb.append("<li>").append(inline(item)).append("</li>");
            }
            sb.append("</").append(tag).append(">");
            blocks.add(sb.toString());
            listItems.clear();
            orderedList = false;
        }

        private void flushTable() {
            if (tableRows.isEmpty()) {
                return;
            }
            blocks.add(renderTable(tableRows));
            tableRows.clear();
        }
    }

    private static String renderTable(List<String> rows) {
        var sb = new StringBuilder("<table>");
        boolean header = true;
        for (String row : rows) {
            String[] cells = splitCells(row);
            if (header) {
                sb.append("<thead><tr>");
            } else {
                sb.append("<tr>");
            }
            for (String cell : cells) {
                sb.append(header ? "<th>" : "<td>")
                        .append(inline(cell.trim()))
                        .append(header ? "</th>" : "</td>");
            }
            sb.append("</tr>");
            if (header) {
                sb.append("</thead><tbody>");
                header = false;
            }
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String[] splitCells(String row) {
        String inner = row.substring(1, row.length() - 1);
        return inner.split("\\|", -1);
    }

    private static String inline(String text) {
        String escaped = escape(text);
        escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        escaped = escaped.replaceAll("\\[(.+?)\\]\\((#.+?)\\)", "<a href=\"$2\">$1</a>");
        escaped = escaped.replaceAll("\\[(.+?)\\]\\((.+?)\\)", "<a href=\"$2\">$1</a>");
        return escaped;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
