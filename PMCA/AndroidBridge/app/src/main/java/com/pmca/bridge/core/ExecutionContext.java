package com.pmca.bridge.core;

import android.content.Context;

public final class ExecutionContext {
    private final Context context;
    private final String executionId;
    private final String command;

    private volatile ExecutionState state = ExecutionState.NEW;

    public ExecutionContext(
            Context context,
            String executionId,
            String command) {
        this.context = context.getApplicationContext();
        this.executionId = executionId;
        this.command = command;
    }

    public Context getContext() {
        return context;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getCommand() {
        return command;
    }

    public ExecutionState getState() {
        return state;
    }

    public void setState(ExecutionState state) {
        this.state = state;
    }
}
