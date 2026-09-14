package com.opencrm.rules.drools.helpers;

/**
 * One DRL payload written into a {@code KieFileSystem} at {@code path}.
 */
public record DrlResource(String path, String content) {
}
