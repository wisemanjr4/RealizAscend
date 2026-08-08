package com.realizascend.modules.stealth;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StealthManager extends RealizModule implements Listener {

    private static final double REDUCED_FOLLOW_RANGE = 6.0;

    private final Map<UUID, Double> reducedFollow = new HashMap<>();
    private final Map<UUID, Long> sneakEndTime = new HashMap<>();
    private BukkitTask task;

    public StealthManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> protectedZoneMobs = new HashSet<>();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isStealthActive(player)) continue;
                    boolean strongDetect = plugin.getSkillManager().getAbilityEffectValue(player, "DETECT_RANGE") < 1.0;
                    int radius = strongDetect ? 14 : 8;

                    for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
                        if (!(e instanceof Monster mob)) continue;
                        protectedZoneMobs.add(mob.getUniqueId());
                        if (mob.getTarget() != null && mob.getTarget().equals(player)) {
                            mob.setTarget(null);
                        }
                        if (!reducedFollow.containsKey(mob.getUniqueId())) {
                            AttributeInstance attr = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
                            if (attr != null) {
                                reducedFollow.put(mob.getUniqueId(), attr.getBaseValue());
                                attr.setBaseValue(REDUCED_FOLLOW_RANGE);
                            }
                        }
                    }
                }

                restoreMobs(protectedZoneMobs);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void restoreMobs(Set<UUID> protectedZoneMobs) {
        reducedFollow.entrySet().removeIf(entry -> {
            if (protectedZoneMobs.contains(entry.getKey())) return false;
            Entity e = Bukkit.getEntity(entry.getKey());
            if (e instanceof Monster mob) {
                AttributeInstance attr = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
                if (attr != null) {
                    attr.setBaseValue(entry.getValue());
                }
            }
            return true;
        });
    }

    private boolean isStealthActive(Player player) {
        boolean hasStealth = plugin.getSkillManager().getAbilityEffectValue(player, "FOOTSTEP_REDUCE") > 1.0
            || plugin.getSkillManager().getAbilityEffectValue(player, "DETECT_RANGE") < 1.0;
        if (!hasStealth) return false;
        if (player.isSneaking()) return true;

        // 影武者: スニーク解除後も効果が持続する
        double lingerMult = plugin.getSkillManager().getAbilityEffectValue(player, "STEALTH_DURATION");
        if (lingerMult <= 1.0) return false;
        Long end = sneakEndTime.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() - end < lingerMult * 5000L;
    }

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            sneakEndTime.remove(player.getUniqueId());
        } else {
            sneakEndTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (task != null) {
            task.cancel();
        }
        restoreMobs(new HashSet<>());
        reducedFollow.clear();
        sneakEndTime.clear();
    }
}
