package com.pmca.bridge.core;

public interface ModuleContract {
    String moduleId();

    boolean supports(String route);

    boolean start();

    boolean stop();

    boolean restart();

    String status();

    boolean health();

    boolean execute(ExecutionContext context);
}
