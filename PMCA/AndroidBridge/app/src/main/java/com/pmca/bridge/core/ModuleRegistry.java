package com.pmca.bridge.core;

import com.pmca.bridge.logging.PmcaLogger;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleRegistry {
    private final Map<String, ModuleContract> modules =
            new LinkedHashMap<>();

    public synchronized boolean register(ModuleContract module) {
        if (module == null || module.moduleId() == null) {
            PmcaLogger.log("Registry: регистрация отклонена");
            return false;
        }

        String id = module.moduleId();

        if (modules.containsKey(id)) {
            PmcaLogger.log("Registry: модуль уже зарегистрирован: " + id);
            return false;
        }

        try {
            if (!module.start()) {
                PmcaLogger.log("Registry: модуль не запущен: " + id);
                return false;
            }

            modules.put(id, module);
            PmcaLogger.log("Registry: зарегистрирован модуль " + id);
            return true;

        } catch (Exception e) {
            PmcaLogger.log("Registry: ошибка модуля " + id + ": "
                    + e.getClass().getSimpleName());
            return false;
        }
    }

    public synchronized ModuleContract get(String id) {
        return modules.get(id);
    }

    public synchronized ModuleContract findRoute(String route) {
        for (ModuleContract module : modules.values()) {
            try {
                if (module.supports(route)) {
                    return module;
                }
            } catch (Exception e) {
                PmcaLogger.log("Registry: ошибка проверки маршрута "
                        + module.moduleId());
            }
        }

        return null;
    }

    public synchronized Collection<ModuleContract> all() {
        return Collections.unmodifiableList(
                new java.util.ArrayList<>(modules.values()));
    }

    public synchronized String statusReport() {
        StringBuilder result = new StringBuilder();

        for (ModuleContract module : modules.values()) {
            if (result.length() > 0) {
                result.append('\n');
            }

            try {
                result.append(module.moduleId())
                        .append(": ")
                        .append(module.status())
                        .append(", health=")
                        .append(module.health() ? "OK" : "FAIL");
            } catch (Exception e) {
                result.append(module.moduleId()).append(": ERROR");
            }
        }

        return result.toString();
    }
}
