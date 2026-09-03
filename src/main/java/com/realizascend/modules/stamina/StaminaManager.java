package com.realizascend.modules.stamina;

import com.realizascend.RealizAscend;
import com.realizascend.core.ConfigManager;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import com.realizascend.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StaminaManager extends RealizModule implements Listener {

    private static final long TICK_INTERVAL = 20L;
    private static final long DAY_LENGTH = 24000L;

    private final Map<UUID, Long> lastJumpTick = new HashMap<>();
    private final Map<UUID, Long> bedEnterTime = new HashMap<>();
    private final Map<UUID, Integer> missedNights = new HashMap<>();
    private final Map<UUID, Long> burstUntilMap = new HashMap<>();
    private final Map<UUID, RestData> resting = new HashMap<>();
    private final Set<UUID> sleptThisNight = new HashSet<>();
    private long lastDayCheck = -1;

    private static final long REST_DURATION = 60000L; // 1分
    private static final double REST_RADIUS_SQ = 64.0; // ベッドから8ブロック以内

    private static class RestData {
        final Location bedLocation;
        final long startTime;
        RestData(Location bedLocation, long startTime) {
            this.bedLocation = bedLocation;
            this.startTime = startTime;
        }
    }

    private BukkitTask tickTask;

    public StaminaManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = new TickRunnable().runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
        lastDayCheck = -1;
    }

    @Override
    public void onDisable() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        lastJumpTick.clear();
        bedEnterTime.clear();
        sleptThisNight.clear();
        missedNights.clear();
        burstUntilMap.clear();
        resting.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        if (!com.realizascend.RealizAscend.isSurvival(player)) return;
        double deltaY = event.getTo().getY() - event.getFrom().getY();

        if (deltaY > 0.4 && !player.isInWater() && !player.isClimbing() && !player.isFlying()) {
            UUID uuid = player.getUniqueId();
            long currentTick = Bukkit.getCurrentTick();
            Long lastJump = lastJumpTick.get(uuid);

            if (lastJump != null && currentTick - lastJump < 10) return;

            lastJumpTick.put(uuid, currentTick);
            applyJumpCost(player);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        if (!com.realizascend.RealizAscend.isSurvival(player)) return;
        applyAttackCost(player);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!com.realizascend.RealizAscend.isSurvival(player)) return;
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();
        data.setFatigue(data.getFatigue() + 0.05);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!com.realizascend.RealizAscend.isSurvival(player)) return;
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();
        // 速築き: ブロック設置のスタミナ消費-40%
        double buildCost = plugin.getSkillManager().getAbilityEffectValue(player, "BUILD_STAMINA_COST");
        data.setStamina(data.getStamina() - cfg.staminaAttackCost * 0.5 * buildCost);
        data.setFatigue(data.getFatigue() + 0.02);
    }

    private boolean hostileNear(Player player) {
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(10, 10, 10)) {
            if (e instanceof org.bukkit.entity.Monster) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);
        if (data.getFatigue() < 30.0) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GRAY + "疲れが足りず眠れない。");
            return;
        }
        bedEnterTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Long enterTime = bedEnterTime.remove(uuid);
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();

        long worldTime = player.getWorld().getTime();
        boolean isDay = worldTime >= 0 && worldTime < 13000;

        if (isDay && enterTime != null) {
            applySleepRecovery(player);
        }
    }

    // 睡眠・休息共通の回復処理
    private void applySleepRecovery(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();
        data.setFatigue(Math.max(0, data.getFatigue() - cfg.fatigueSleepRecovery));
        data.setSleepDebt(0);
        data.setHealthLevel(Math.min(100, data.getHealthLevel() + 5.0));
        sleptThisNight.add(uuid);
        missedNights.remove(uuid);
        // 超回復: 睡眠でスタミナ上限が永続+5% (最大25%)
        if (plugin.getSkillManager().getAbilityEffectValue(player, "SLEEP_STAMINA_BOOST") > 1.0) {
            data.addStaminaBonusPercent(5);
        }
        player.sendMessage(ChatColor.GREEN + "ぐっすり眠れた。疲労が回復した!");
    }

    // 昼間にベッドを右クリック → 1分休憩で睡眠と同じ効果
    @EventHandler
    public void onRestStart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock() instanceof org.bukkit.block.Bed)) return;
        Player player = event.getPlayer();
        if (!com.realizascend.RealizAscend.isSurvival(player)) return;

        // 夜はバニラの睡眠に任せる
        long time = player.getWorld().getTime();
        if (time >= 12542 && time <= 23850) return;

        PlayerData data = plugin.getDataManager().getData(player);
        if (data.getFatigue() < 30.0) {
            player.sendMessage(ChatColor.GRAY + "疲れが足りず休めない。");
            return;
        }
        if (resting.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "すでに休息中だ。");
            return;
        }

        event.setCancelled(true);
        resting.put(player.getUniqueId(), new RestData(
            event.getClickedBlock().getLocation(), System.currentTimeMillis()));
        player.sendMessage(ChatColor.YELLOW + "ベッドで休息を始めた。1分待てば疲労が回復する。");
    }

    // 休息中の中断処理
    private void checkResting() {
        long now = System.currentTimeMillis();
        resting.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) return true;
            RestData data = entry.getValue();
            // ベッドから離れすぎたら中断
            if (data.bedLocation.getWorld() == null
                || !player.getLocation().getWorld().equals(data.bedLocation.getWorld())
                || player.getLocation().distanceSquared(data.bedLocation) > REST_RADIUS_SQ) {
                player.sendMessage(ChatColor.GRAY + "ベッドから離れたため休息が中断された。");
                return true;
            }
            if (now - data.startTime >= REST_DURATION) {
                applySleepRecovery(player);
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onDamageInterrupt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        RestData data = resting.remove(player.getUniqueId());
        if (data != null) {
            player.sendMessage(ChatColor.GRAY + "ダメージを受けて休息が中断された。");
        }
    }

    private void applyJumpCost(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();
        data.setStamina(data.getStamina() - cfg.staminaJumpCost);
        data.setFatigue(data.getFatigue() + 0.15);
    }

    private void applyAttackCost(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();
        double meleeCost = plugin.getSkillManager().getAbilityEffectValue(player, "MELEE_STAMINA_COST");
        data.setStamina(data.getStamina() - cfg.staminaAttackCost * meleeCost);
        data.setFatigue(data.getFatigue() + 0.08);
    }

    private class TickRunnable extends BukkitRunnable {
        private int tickCount = 0;

        @Override
        public void run() {
            tickCount++;
            checkResting();
            ConfigManager cfg = plugin.getConfigManager();
            long currentDay = getCurrentDay();

            if (lastDayCheck == -1) {
                lastDayCheck = currentDay;
            } else if (currentDay != lastDayCheck) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!sleptThisNight.remove(uuid)) {
                        PlayerData data = plugin.getDataManager().getData(player);
                        int missed = missedNights.getOrDefault(uuid, 0) + 1;
                        missedNights.put(uuid, missed);
                        // 不眠耐性: デバフ発生が遅くなる (2夜に1回だけ適用)
                        if (data.getAbilityLevel("insomnia_resist") > 0 && missed % 2 == 0) {
                            continue;
                        }
                        data.setSleepDebt(data.getSleepDebt() + 1);
                        data.setHealthLevel(data.getHealthLevel() - 2.0);
                    }
                }
                sleptThisNight.clear();
                lastDayCheck = currentDay;
            }

for (Player player : Bukkit.getOnlinePlayers()) {
                if (!com.realizascend.RealizAscend.isSurvival(player)) continue;
                PlayerData data = plugin.getDataManager().getData(player);

                double maxStaminaMult = plugin.getSkillManager().getAbilityEffectValue(player, "MAX_STAMINA");
                double sprintCostMult = plugin.getSkillManager().getAbilityEffectValue(player, "SPRINT_COST");
                double walkCostMult = plugin.getSkillManager().getAbilityEffectValue(player, "WALK_COST");
                double waterCostMult = plugin.getSkillManager().getAbilityEffectValue(player, "WATER_STAMINA_COST");
                double climbCostMult = plugin.getSkillManager().getAbilityEffectValue(player, "CLIMB_COST");
                // 体温ゾーンのスタミナ消費ペナルティを心肺強化で軽減
                double zonePenalty = 1.0;
                String zone = data.getTempZone();
                if (zone.equals("WARNING")) zonePenalty = 1.2;
                else if (zone.equals("CRITICAL")) zonePenalty = 1.5;
                double tempResist = plugin.getSkillManager().getAbilityEffectValue(player, "TEMP_WARNING_STAMINA");
                double reduction = Math.min(1.0, tempResist - 1.0);
                double tempZoneMult = 1.0 + (zonePenalty - 1.0) * (1.0 - reduction);

                if (tickCount % 5 == 0) {
                    if (player.isSprinting()) {
                        double cost = cfg.staminaSprintCost * sprintCostMult * tempZoneMult;
                        if (player.isSwimming()) cost *= waterCostMult;
                        data.setStamina(data.getStamina() - cost);
                        data.setFatigue(data.getFatigue() + 0.08);
                    } else if (player.isClimbing()) {
                        data.setStamina(data.getStamina() - cfg.staminaSprintCost * 0.15 * climbCostMult * tempZoneMult);
                    } else if (!player.isFlying() && !player.isSwimming() && player.isOnGround()) {
                        data.setStamina(data.getStamina() - cfg.staminaSprintCost * 0.1 * walkCostMult);
                    }
                }

                if (!player.isSprinting()) {
                    double regenMult = plugin.getSkillManager().getAbilityEffectValue(player, "STAMINA_INSTANT_REGEN");
                    if (data.getFatigue() > 50.0) {
                        regenMult *= plugin.getSkillManager().getAbilityEffectValue(player, "FATIGUE_STAMINA_REGEN");
                    }
                    // 戦闘呼吸: 戦闘中も回復が止まらない/加速
                    if (hostileNear(player)) {
                        regenMult *= plugin.getSkillManager().getAbilityEffectValue(player, "COMBAT_STAMINA_REGEN");
                    }
                    data.setStamina(data.getStamina() + cfg.staminaRegenerationRate * regenMult);
                }

                // 第二の風: スタミナ切れ直後に回復バースト
                long burstUntil = burstUntilMap.getOrDefault(player.getUniqueId(), 0L);
                if (data.getStamina() <= 0 && plugin.getSkillManager().getAbilityEffectValue(player, "STAMINA_BURST") > 1.0) {
                    burstUntil = System.currentTimeMillis() + 3000;
                    burstUntilMap.put(player.getUniqueId(), burstUntil);
                }
                if (System.currentTimeMillis() < burstUntil) {
                    data.setStamina(Math.min(data.getStamina() + cfg.staminaRegenerationRate * 4.0, cfg.staminaMaxValue));
                }

                double effectiveMaxStamina = cfg.staminaMaxValue * maxStaminaMult
                    * (1.0 + data.getStaminaBonusPercent() / 100.0);
                if (data.getCalories() < 30.0) {
                    double penalty = (30.0 - data.getCalories()) / 30.0 * 0.3;
                    effectiveMaxStamina = effectiveMaxStamina * (1.0 - penalty);
                }
                if (data.getStamina() > effectiveMaxStamina) {
                    data.setStamina(effectiveMaxStamina);
                }

                data.setFatigue(data.getFatigue() + cfg.fatigueAccumulationRate);

                applyStaminaEffects(player, data, cfg, effectiveMaxStamina);
                showStaminaHud(player, data, cfg);
            }
        }
    }

    private long getCurrentDay() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player.getWorld().getFullTime() / DAY_LENGTH;
        }
        return 0;
    }

    private void applyStaminaEffects(Player player, PlayerData data, ConfigManager cfg, double effectiveMaxStamina) {
        // 無我の境地: スタミナ0時のペナルティ大幅軽減
        boolean noMind = plugin.getSkillManager().hasAbilityEffect(player, "ZERO_STAMINA_PENALTY_REDUCE");
        if (data.getStamina() <= 0) {
            // 不屈: スタミナ0でも行動可能
            if (plugin.getSkillManager().getAbilityEffectValue(player, "ZERO_STAMINA_ACTION") <= 1.0) {
                player.setSprinting(false);
            }
            if (noMind) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 40, 0, false, false, true));
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 40, 0, false, false, true));
            }
        }

        // 疲労限度は健康度に連動 (健康なほど限度増)
        double effectiveFatigueMax = cfg.fatigueMaxValue * (0.6 + data.getHealthLevel() * 0.004);
        if (data.getFatigue() >= effectiveFatigueMax) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 40, 1, false, false, true));
        }

        double mineMult = plugin.getSkillManager().getAbilityEffectValue(player, "MINE_SPEED");
        if (mineMult > 1.0) {
            int hasteLevel = Math.max(0, Math.min(3, (int) Math.round((mineMult - 1.0) * 10) - 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 40, hasteLevel, false, false, true));
        } else {
            player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        }

        double jumpMult = plugin.getSkillManager().getAbilityEffectValue(player, "JUMP_HEIGHT");
        boolean notHeavy = data.getCurrentWeight() <= plugin.getConfigManager().weightOverLimit;
        if (jumpMult > 1.0 && notHeavy) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 40, Math.max(0, (int) Math.round(jumpMult - 1.0)), false, false, true));
        } else if (notHeavy) {
            player.removePotionEffect(PotionEffectType.JUMP);
        }
    }

    private void showStaminaHud(Player player, PlayerData data, ConfigManager cfg) {
        int segCount = 10;
        double stamPct = data.getStamina() / cfg.staminaMaxValue;
        String stamBar = MessageUtil.buildBar(stamPct, segCount);
        double fatPct = 1.0 - (data.getFatigue() / cfg.fatigueMaxValue);
        String fatBar = MessageUtil.buildBarYellow(fatPct, segCount);

        MessageUtil.sendActionBar(player,
            ChatColor.GREEN + "スタミナ: " + stamBar + " " + String.format("%.0f%%  ", data.getStamina()) +
            ChatColor.GOLD + "疲労: " + fatBar + " " + String.format("%.0f%%", data.getFatigue()));
    }
}
