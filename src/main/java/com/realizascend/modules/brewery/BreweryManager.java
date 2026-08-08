package com.realizascend.modules.brewery;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public class BreweryManager extends RealizModule {

    private Listener breweryListener;

    public BreweryManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("BreweryX") != null) {
            try {
                Class<?> clazz = Class.forName("com.realizascend.modules.brewery.BreweryListener");
                breweryListener = (Listener) clazz.getDeclaredConstructor(RealizAscend.class).newInstance(plugin);
                Bukkit.getPluginManager().registerEvents(breweryListener, plugin);
                plugin.getLogger().info("BreweryX integration enabled!");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to init BreweryX integration: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("BreweryX not found, alcohol integration disabled.");
        }
    }

    @Override
    public void onDisable() {
        if (breweryListener != null) {
            HandlerList.unregisterAll(breweryListener);
        }
    }
}
