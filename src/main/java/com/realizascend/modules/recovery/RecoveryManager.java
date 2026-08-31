package com.realizascend.modules.recovery;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RecoveryManager extends RealizModule {

    private enum RecoveryType { BLOOD, HEALTH, INFECTION, INJURY_HEAD, INJURY_TORSO, INJURY_LEGS, FRACTURE }

    private static class RecoveryEffect {
        final RecoveryType type;
        final double amountPerTick;
        int ticksLeft;

        RecoveryEffect(RecoveryType type, double amountPerTick, int seconds) {
            this.type = type;
            this.amountPerTick = amountPerTick;
            this.ticksLeft = seconds;
        }
    }

    private final Map<UUID, List<RecoveryEffect>> active = new HashMap<>();
    private BukkitTask tickTask;

    public RecoveryManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, List<RecoveryEffect>> entry : new ArrayList<>(active.entrySet())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null) continue;
                    if (!com.realizascend.RealizAscend.isSurvival(player)) continue;
                    PlayerData data = plugin.getDataManager().getData(player);

                    List<RecoveryEffect> effects = entry.getValue();
                    Iterator<RecoveryEffect> it = effects.iterator();
                    while (it.hasNext()) {
                        RecoveryEffect eff = it.next();
                        eff.ticksLeft--;
                        switch (eff.type) {
                            case BLOOD:
                                data.setBlood(Math.min(100, data.getBlood() + eff.amountPerTick));
                                break;
                            case HEALTH:
                                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + eff.amountPerTick));
                                break;
                            case INFECTION:
                                data.setInfectionProgress(Math.max(0, data.getInfectionProgress() - eff.amountPerTick));
                                if (data.getInfectionProgress() <= 0) {
                                    data.setInfected(false);
                                }
                                break;
                            case INJURY_HEAD:
                                if (eff.ticksLeft <= 0) data.setHeadInjured(false);
                                break;
                            case INJURY_TORSO:
                                if (eff.ticksLeft <= 0) data.setTorsoInjured(false);
                                break;
                            case INJURY_LEGS:
                                if (eff.ticksLeft <= 0) data.setLegsInjured(false);
                                break;
                            case FRACTURE:
                                if (eff.ticksLeft <= 0) data.setFractured(false);
                                break;
                        }
                        if (eff.ticksLeft <= 0) {
                            it.remove();
                        }
                    }
                    if (effects.isEmpty()) {
                        active.remove(entry.getKey());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        active.clear();
    }

    public void addBloodRegen(Player player, double total, int seconds) {
        add(player, RecoveryType.BLOOD, total / seconds, seconds);
    }

    public void addHealthRegen(Player player, double total, int seconds) {
        add(player, RecoveryType.HEALTH, total / seconds, seconds);
    }

    public void addInfectionCure(Player player, double total, int seconds) {
        add(player, RecoveryType.INFECTION, total / seconds, seconds);
    }

    public void healInjury(Player player, String bodyPart, int seconds) {
        RecoveryType type;
        switch (bodyPart.toUpperCase()) {
            case "HEAD": type = RecoveryType.INJURY_HEAD; break;
            case "TORSO": type = RecoveryType.INJURY_TORSO; break;
            case "LEGS": type = RecoveryType.INJURY_LEGS; break;
            default: return;
        }
        add(player, type, 0, seconds);
    }

    public void healFracture(Player player, int seconds) {
        add(player, RecoveryType.FRACTURE, 0, seconds);
    }

    private void add(Player player, RecoveryType type, double amountPerTick, int seconds) {
        if (seconds <= 0) return;
        active.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
            .add(new RecoveryEffect(type, amountPerTick, seconds));
    }
}
