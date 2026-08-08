package com.realizascend.core;

import com.realizascend.RealizAscend;

public abstract class RealizModule {

    protected final RealizAscend plugin;

    public RealizModule(RealizAscend plugin) {
        this.plugin = plugin;
    }

    public abstract void onEnable();
    public abstract void onDisable();

    public void onReload() {
        onDisable();
        onEnable();
    }

    public RealizAscend getPlugin() {
        return plugin;
    }
}
