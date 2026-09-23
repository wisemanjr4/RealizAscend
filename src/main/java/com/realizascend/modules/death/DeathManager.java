package com.realizascend.modules.death;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.realizascend.modules.skill.SkillManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadLocalRandom;

public class DeathManager extends RealizModule implements Listener {

    private final java.util.Map<java.util.UUID, Long> lastRespawn = new java.util.HashMap<>();
    private static final long RESPAWN_COOLDOWN = 3000L; // 二重死亡の2回目リスポーンはペナルティ適用しない

    // DeadLaw由来: 5分以内に再死亡すると「瀕死」→ 次回リスポーンはランダム地点
    private final java.util.Map<java.util.UUID, Long> lastDeath = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> dying = new java.util.HashSet<>();
    private static final long DYING_WINDOW_MS = 300000L; // 5分
    private static final long DEATH_DEBOUNCE_MS = 2500L; // 二重死亡のデバウンス
    private static final int RANDOM_RADIUS = 5000;
    private static final int RANDOM_ATTEMPTS = 20;

    public DeathManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private int calculateSkillPointsFromLevel(int level) {
        if (level <= 5) return level;
        return 5 + (level - 1) / 5;
    }

    private int calculateTotalPoints(Map<String, Integer> skillLevels) {
        int total = 0;
        for (int level : skillLevels.values()) {
            total += calculateSkillPointsFromLevel(level);
        }
        return total;
    }

    private int calculateTotalSpent(Map<String, Integer> unlockedAbilities) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : unlockedAbilities.entrySet()) {
            SkillManager.SkillAbility ability = SkillManager.ALL_ABILITIES.get(entry.getKey());
            if (ability != null) {
                total += ability.getCost() * entry.getValue();
            }
        }
        return total;
    }

    // DeadLaw由来: 5分以内の再死亡で「瀕死」状態 → 次回リスポーンがランダム地点
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = lastDeath.get(uuid);

        // 二重死亡（外部要因で墓が2個出る等）をデバウンス — 瞬間2回死は瀕死扱いしない
        if (prev != null && now - prev < DEATH_DEBOUNCE_MS) {
            return;
        }

        if (prev != null && now - prev < DYING_WINDOW_MS) {
            dying.add(uuid);
            player.sendMessage(org.bukkit.ChatColor.RED + "" + org.bukkit.ChatColor.BOLD + "瀕死! "
                + org.bukkit.ChatColor.RED + "5分以内に再死亡したため次回リスポーンはランダム地点になります");
        }

        lastDeath.put(uuid, now);
    }

    private Location findRandomLand(Player player) {
        World world = player.getWorld();
        Location wSpawn = world.getSpawnLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < RANDOM_ATTEMPTS; i++) {
            int rx = rnd.nextInt(-RANDOM_RADIUS, RANDOM_RADIUS);
            int rz = rnd.nextInt(-RANDOM_RADIUS, RANDOM_RADIUS);
            int hy = world.getHighestBlockYAt(wSpawn.getBlockX() + rx, wSpawn.getBlockZ() + rz);
            if (hy <= world.getMinHeight()) continue;
            if (hy <= 50) continue; // Y50以下は対象外

            Location cand = new Location(world, wSpawn.getBlockX() + rx + 0.5, hy + 1.0, wSpawn.getBlockZ() + rz + 0.5);
            Material below = world.getBlockAt(cand.getBlockX(), hy, cand.getBlockZ()).getType();
            Material at = world.getBlockAt(cand.getBlockX(), cand.getBlockY(), cand.getBlockZ()).getType();
            Material above = world.getBlockAt(cand.getBlockX(), cand.getBlockY() + 1, cand.getBlockZ()).getType();

            // 海上/溶岩/粉雪を除外 — 陸地のみ
            if (below == Material.WATER || below == Material.LAVA
                || below == Material.SEAGRASS || below == Material.TALL_SEAGRASS
                || below == Material.KELP || below == Material.KELP_PLANT) continue;
            if (below.name().contains("POWDER_SNOW")) continue;
            if (world.getBlockAt(cand.getBlockX(), cand.getBlockY(), cand.getBlockZ()).getType().isSolid()
                || world.getBlockAt(cand.getBlockX(), cand.getBlockY() + 1, cand.getBlockZ()).getType().isSolid()) continue; // 上下が塞がっている

            // 海系バイオームを除外
            String bn = world.getBiome(cand.getBlockX(), hy, cand.getBlockZ()).name();
            if (bn.contains("OCEAN") || bn.contains("RIVER") || bn.contains("BEACH")) continue;

            return cand;
        }
        // フォールバック: ワールドスポーンの地表
        Location fallback = world.getSpawnLocation().clone();
        fallback.setY(world.getHighestBlockYAt(fallback) + 1);
        return fallback;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);

        // DeadLaw由来: 瀕死状態ならランダム地点へリスポーン
        if (dying.remove(player.getUniqueId())) {
            Location rnd = findRandomLand(player);
            event.setRespawnLocation(rnd);
            try { player.setBedSpawnLocation(null, true); } catch (Throwable ignored) {}
            final Location loc = rnd;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "瀕死状態: ベッド地点をリセットし、知らない土地へランダム転送されました "
                        + ChatColor.GRAY + "(5分経過で通常に戻ります)");
                }
            }, 10L);
        }

        // 二重死亡の2回目のリスポーンにはペナルティを適用しない (詰み防止)
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastRespawn.get(uuid);
        if (last != null && now - last < RESPAWN_COOLDOWN) {
            lastRespawn.put(uuid, now);
            return;
        }
        lastRespawn.put(uuid, now);

        player.setHealth(Math.max(6, player.getMaxHealth() * 0.7));
        player.setFoodLevel((int) Math.round(data.getNutritionBalance() / 100.0 * 20.0));
        player.setSaturation(0);

        data.setHealthLevel(70);
        data.setCalories(30);
        data.setProtein(30);
        data.setVitamins(30);
        data.setSalt(30);
        data.setHydration(40);
        data.setBlood(70);
        data.setStamina(50);
        data.setFatigue(40);

        // 死んでリスポーンすれば体はリセットされる (感染の詰み防止)
        data.setInfected(false);
        data.setInfectionProgress(0);
        data.setHeadInjured(false);
        data.setTorsoInjured(false);
        data.setLegsInjured(false);
        data.setFractured(false);

        Map<String, Integer> skillLevels = data.getSkillLevels();
        Map<String, Integer> newSkillLevels = new HashMap<>();
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
            String skill = entry.getKey();
            int currentLevel = entry.getValue();
            int reduction = Math.min(currentLevel, Math.max(1, currentLevel / 10));
            int newLevel = currentLevel - reduction;
            newSkillLevels.put(skill, Math.max(0, newLevel));
        }
        data.getSkillLevels().clear();
        data.getSkillLevels().putAll(newSkillLevels);

        int totalPoints = calculateTotalPoints(newSkillLevels);
        int totalSpent = calculateTotalSpent(data.getUnlockedAbilities());

        if (totalSpent > totalPoints) {
            int deficit = totalSpent - totalPoints;

            List<SkillManager.SkillAbility> sortedByCost = new ArrayList<>(SkillManager.ALL_ABILITIES.values());
            sortedByCost.sort((a, b) -> Integer.compare(b.getCost(), a.getCost()));

            player.sendMessage(ChatColor.RED + "死亡によるスキルロスが所持スキルポイントを超えている!");
            player.sendMessage(ChatColor.RED + "コストの高いアビリティが解除される...");

            for (SkillManager.SkillAbility ability : sortedByCost) {
                Integer currentLevel = data.getUnlockedAbilities().get(ability.getId());
                if (currentLevel == null || currentLevel <= 0) continue;

                while (currentLevel > 0 && deficit >= ability.getCost()) {
                    data.getUnlockedAbilities().put(ability.getId(), currentLevel - 1);
                    if (data.getUnlockedAbilities().get(ability.getId()) <= 0) {
                        data.getUnlockedAbilities().remove(ability.getId());
                    }
                    deficit -= ability.getCost();
                    currentLevel--;
                    player.sendMessage(ChatColor.GRAY + "  - " + ChatColor.RED + ability.getName()
                        + ChatColor.GRAY + " が解除された");
                }
                if (deficit <= 0) break;
            }
        }

        data.getSkillXp().clear();

        player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "=== 死亡しました ===");
        player.sendMessage(ChatColor.GRAY + "HP・栄養・水分・スキルにペナルティが発生した。");
        player.sendMessage(ChatColor.GRAY + "墓を探すか、仲間にアイテムを回収してもらおう。");
        player.sendMessage(ChatColor.GRAY + "現在HP: " + ChatColor.RED + "70"
            + ChatColor.GRAY + " | 血液: " + ChatColor.RED + "70"
            + ChatColor.GRAY + " | 空腹: " + ChatColor.RED + "30");
    }

    @Override
    public void onDisable() {
        PlayerRespawnEvent.getHandlerList().unregister(this);
        lastRespawn.clear();
        lastDeath.clear();
        dying.clear();
    }
}