package com.realizascend.modules.brewery;

import com.dre.brewery.api.events.brew.BrewDrinkEvent;
import com.realizascend.RealizAscend;
import com.realizascend.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class BreweryListener implements Listener {

    private final RealizAscend plugin;

    public BreweryListener(RealizAscend plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBrewDrink(BrewDrinkEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);

        int alcohol = event.getAddedAlcohol();
        int quality = event.getQuality();

        // 酒はストレスを大きく下げる (アルコール度数が高いほど効く)
        double stressReduction = alcohol * 2.0 + quality * 0.5;
        data.setStress(Math.max(0, data.getStress() - stressReduction));

        // 飲み過ぎると喉が渇く (水分低下もそれなりに)
        double hydrationLoss = 6.0 + alcohol * 0.5;
        data.setHydration(Math.max(0, data.getHydration() - hydrationLoss));

        player.sendMessage(ChatColor.LIGHT_PURPLE + "ストレス " + String.format("%.0f", -stressReduction)
            + ChatColor.GRAY + " | 水分 " + String.format("%.0f", -hydrationLoss)
            + ChatColor.GRAY + " (アルコール)");
    }
}
