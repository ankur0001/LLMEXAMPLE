package com.projectmind.core.domain;

/**
 * A method invocation detected in source code.
 */
public record ParsedMethodCall(
        String callerType,
        String callerMethod,
        String targetType,
        String targetMethod,
        int line
) {
}
