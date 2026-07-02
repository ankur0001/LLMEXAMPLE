package com.projectmind.core.domain;

/**
 * AI-generated summary for a REST or RPC endpoint.
 */
public record ApiSummary(
        String path,
        String httpMethod,
        String summary,
        String controllerClass
) {
}
