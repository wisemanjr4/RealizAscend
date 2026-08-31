package com.realizascend.modules.stress;

import com.realizascend.RealizAscend;
import com.realizascend.core.ConfigManager;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import com.realizascend.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class StressManager extends RealizModule implements Listener {

    private static final long STRESS_INTERVAL = 200L;

    private BukkitTask stressTask;
    private final java.util.Set<java.util.UUID> stressWarned = new java.util.HashSet<>();

    public StressManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        stressTask = new StressRunnable().runTaskTimer(plugin, STRESS_INTERVAL, STRESS_INTERVAL);
    }

    @Override
    public void onDisable() {
        if (stressTask != null) { stressTask.cancel(); stressTask = null; }
        stressWarned.clear();
        HandlerList.unregisterAll(this);
    }

    // 攻撃を受けるとストレスが上がる
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!com.realizascend.RealizAscend.isSurvival(player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.STARVATION) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.SUICIDE) return;
        double gain = 1.0 + event.getDamage() * 0.5;
        PlayerData data = plugin.getDataManager().getData(player);
        data.setStress(data.getStress() + gain);
    }

    private class StressRunnable extends BukkitRunnable {
        @Override
        public void run() {
            ConfigManager cfg = plugin.getConfigManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!com.realizascend.RealizAscend.isSurvival(player)) continue;
                PlayerData data = plugin.getDataManager().getData(player);
                // 常時低下は撤廃。ストレスは「快適な環境」(明るさ・火・栄養・睡眠・酒)で下がる
                double delta = 0;

                delta += evaluateDarkness(player);
                delta += evaluateNearbyMobs(player);
                delta += evaluateTemperature(data);
                delta += evaluateSleepDebt(data);
                delta += evaluateInjuries(data);
                delta += evaluateBleeding(data);
                delta += evaluateLowHealth(player);
                delta += evaluateLowStamina(data);
                delta += evaluateDangerDimension(player);
                delta += evaluateLowNourishment(data);
                delta += evaluateWellLit(player);
                delta += evaluateCampfire(player);
                delta += evaluateNutrition(data);
                delta += evaluateHydration(data);

                if (data.getSleepDebt() == 0) {
                    delta += evaluateSleeping(player);
                }

                double stressResist = plugin.getSkillManager().getAbilityEffectValue(player, "STRESS_RISE");
                if (delta > 0) {
                    delta *= stressResist;
                }
                double combatResist = plugin.getSkillManager().getAbilityEffectValue(player, "COMBAT_STRESS");
                if (hostileNear(player) && delta > 0) {
                    delta *= combatResist;
                }

                data.setStress(data.getStress() + delta);
                applyStressEffects(player, data, cfg);
                displayStress(player, data);

                // 初めてストレスが高まったらコーデックス解放
                if (data.getStress() > 70 && stressWarned.add(player.getUniqueId())) {
                    plugin.getCodexManager().unlockEntry(player, "stress_management");
                }
            }
        }
    }

    private double evaluateDarkness(Player player) {
        byte light = player.getLocation().getBlock().getLightLevel();
        return light < 7 ? 1.0 : 0.0;
    }

    private double evaluateNearbyMobs(Player player) {
        int hostileCount = 0;
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Monster) {
                hostileCount++;
            }
        }
        return hostileCount * 2.0;
    }

    private boolean hostileNear(Player player) {
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Monster) {
                return true;
            }
        }
        return false;
    }

    private double evaluateTemperature(PlayerData data) {
        switch (data.getTempZone()) {
            case "WARNING": return 1.0;
            case "CRITICAL": return 3.0;
            case "FATAL": return 5.0;
            default: return 0.0;
        }
    }

    private double evaluateSleepDebt(PlayerData data) {
        return Math.min(data.getSleepDebt(), 5) * 1.0;
    }

    private double evaluateLowHealth(Player player) {
        return player.getHealth() < player.getMaxHealth() * 0.3 ? 2.0 : 0.0;
    }

    private double evaluateLowStamina(PlayerData data) {
        return data.getStamina() < 20 ? 1.5 : 0.0;
    }

    private double evaluateDangerDimension(Player player) {
        org.bukkit.World.Environment env = player.getWorld().getEnvironment();
        if (env == org.bukkit.World.Environment.NETHER) return 2.0;
        if (env == org.bukkit.World.Environment.THE_END) return 2.5;
        return 0.0;
    }

    private double evaluateLowNourishment(PlayerData data) {
        return (data.getCalories() < 30 || data.getHydration() < 30) ? 1.0 : 0.0;
    }

    private double evaluateInjuries(PlayerData data) {
        int count = 0;
        if (data.isHeadInjured()) count++;
        if (data.isTorsoInjured()) count++;
        if (data.isLegsInjured()) count++;
        return count * 2.0;
    }

    private double evaluateBleeding(PlayerData data) {
        return data.getBlood() < 50.0 ? 3.0 : 0.0;
    }

    private double evaluateWellLit(Player player) {
        byte light = player.getLocation().getBlock().getLightLevel();
        return light > 10 ? -1.0 : 0.0;
    }

    private double evaluateCampfire(Player player) {
        Location loc = player.getLocation();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    Material block = loc.getWorld().getBlockAt(bx + x, by + y, bz + z).getType();
                    if (block == Material.CAMPFIRE || block == Material.SOUL_CAMPFIRE
                        || block == Material.FURNACE || block == Material.BLAST_FURNACE
                        || block == Material.SMOKER) {
                        return -2.0;
                    }
                }
            }
        }
        return 0.0;
    }

    private double evaluateNutrition(PlayerData data) {
        return data.getNutritionBalance() > 50.0 ? -1.0 : 0.0;
    }

    private double evaluateHydration(PlayerData data) {
        return data.getHydration() > 50.0 ? -1.0 : 0.0;
    }

    private double evaluateSleeping(Player player) {
        return player.isSleeping() ? -10.0 : 0.0;
    }

    private void applyStressEffects(Player player, PlayerData data, ConfigManager cfg) {
        double stress = data.getStress();

        if (stress < cfg.stressNormalRangeMin) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 0, false, false, true));
        }

        if (stress > cfg.stressNormalRangeMax) {
            if (Math.random() < 0.1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 100, 0, false, false, true));
            }
        }
    }

    private void displayStress(Player player, PlayerData data) {
        ChatColor color;
        if (data.getStress() < 20) color = ChatColor.GRAY;
        else if (data.getStress() < 40) color = ChatColor.GREEN;
        else if (data.getStress() < 60) color = ChatColor.YELLOW;
        else if (data.getStress() < 80) color = ChatColor.GOLD;
        else color = ChatColor.RED;

        MessageUtil.sendActionBar(player,
            color + "✕ ストレス " + String.format("%.0f", data.getStress()) + "/100");
    }
}
