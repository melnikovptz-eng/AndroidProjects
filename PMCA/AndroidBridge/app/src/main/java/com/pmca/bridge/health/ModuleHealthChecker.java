package com.pmca.bridge.health;

import com.pmca.bridge.core.ModuleContract;
import com.pmca.bridge.core.ModuleRegistry;
import com.pmca.bridge.logging.PmcaLogger;

public final class ModuleHealthChecker {
    private ModuleHealthChecker() {}

    public static String check(ModuleRegistry registry) {
        if (registry == null) {
            return "Registry: NOT_AVAILABLE";
        }

        StringBuilder report = new StringBuilder();

        for (ModuleContract module : registry.all()) {
            boolean ok = false;

            try {
                ok = module.health();
            } catch (Exception e) {
                PmcaLogger.log("HealthCheck: ошибка " + module.moduleId());
            }

            if (report.length() > 0) {
                report.append('\n');
            }

            report.append(module.moduleId())
                    .append(": ")
                    .append(ok ? "HEALTHY" : "UNHEALTHY");
        }

        return report.toString();
    }
}
