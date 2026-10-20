package com.test.accessibility3.api;

/** Immutable result of a click API request. A completed gesture does not prove app-level delivery. */
public final class ClickResult {
    private final boolean success;
    private final String status;
    private final String detail;
    private final int x;
    private final int y;

    public ClickResult(boolean success, String status, String detail, int x, int y) {
        this.success = success;
        this.status = status == null ? "UNKNOWN" : status;
        this.detail = detail == null ? "" : detail;
        this.x = x;
        this.y = y;
    }

    public boolean isSuccess() { return success; }
    public String getStatus() { return status; }
    public String getDetail() { return detail; }
    public int getX() { return x; }
    public int getY() { return y; }
}
