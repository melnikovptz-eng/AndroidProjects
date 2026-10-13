package com.pmca.bridge.core;

public enum ExecutionState {
    NEW,
    PARSED,
    QUEUED,
    RUNNING,
    ACCEPTED,
    RESULT_RECEIVED,
    COMPLETED,
    SUCCESS,
    ERROR,
    REJECTED,
    TIMEOUT
}
