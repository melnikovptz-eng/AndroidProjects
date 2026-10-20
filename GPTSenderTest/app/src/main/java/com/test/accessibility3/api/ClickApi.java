package com.test.accessibility3.api;

/** Public contract used by any project that needs the shared clicker module. */
public interface ClickApi {
    ClickResult click(ClickRequest request, StringBuilder diagnostics);
}
