package com.realizascend.core;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {

    private final List<RealizModule> modules = new ArrayList<>();

    public void register(RealizModule module) {
        modules.add(module);
    }

    public void registerAll(RealizModule... mods) {
        for (RealizModule m : mods) {
            modules.add(m);
        }
    }

    public void enableAll() {
        for (RealizModule module : modules) {
            try {
                module.onEnable();
            } catch (Exception e) {
                module.getPlugin().getLogger().severe(
                    "Failed to enable module: " + module.getClass().getSimpleName() + " - " + e.getMessage()
                );
            }
        }
    }

    public void disableAll() {
        for (RealizModule module : modules) {
            try {
                module.onDisable();
            } catch (Exception e) {
                module.getPlugin().getLogger().severe(
                    "Failed to disable module: " + module.getClass().getSimpleName()
                );
            }
        }
    }

    public void reloadAll() {
        for (RealizModule module : modules) {
            try {
                module.onReload();
            } catch (Exception e) {
                module.getPlugin().getLogger().severe(
                    "Failed to reload module: " + module.getClass().getSimpleName()
                );
            }
        }
    }

    public List<RealizModule> getModules() {
        return modules;
    }
}
