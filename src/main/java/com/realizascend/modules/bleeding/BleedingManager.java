package com.realizascend.modules.bleeding;

import com.realizascend.RealizAscend;
import com.realizascend.core.ConfigManager;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import com.realizascend.util.MessageUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BleedingManager extends RealizModule implements Listener {

    private static final long REGEN_INTERVAL = 20L;
    private static final long HEAL_INTERVAL = 6000L;

    private static final EnumSet<EntityDamageEvent.DamageCause> COMBAT_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.ENTITY_ATTACK,
        EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,
        EntityDamageEvent.DamageCause.PROJECTILE
    );

    private final Random random = new Random();
    private final Map<UUID, Long> injuryTimestamps = new HashMap<>();
    private final Map<UUID, Long> counterReady = new HashMap<>();
    private final Map<UUID, Boolean> criticalInfectionWarned = new HashMap<>();
    private BukkitTask regenTask;
    private BukkitTask healTask;

    public BleedingManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        regenTask = new RegenRunnable().runTaskTimer(plugin, REGEN_INTERVAL, REGEN_INTERVAL);
        healTask = new HealRunnable().runTaskTimer(plugin, HEAL_INTERVAL, HEAL_INTERVAL);
    }

    @Override
    public void onDisable() {
        if (regenTask != null) { regenTask.cancel(); regenTask = null; }
        if (healTask != null) { healTask.cancel(); healTask = null; }
        injuryTimestamps.clear();
        counterReady.clear();
        criticalInfectionWarned.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        // バニラの飢餓ダメージは無効化 (栄養システム側で処理する)
        if (event.getCause() == EntityDamageEvent.DamageCause.STARVATION) {
            event.setCancelled(true);
            return;
        }
        Player player = (Player) event.getEntity();
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();
        double damage = event.getDamage();

        double physMult = plugin.getSkillManager().getAbilityEffectValue(player, "PHYSICAL_DAMAGE");
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            physMult *= plugin.getSkillManager().getAbilityEffectValue(player, "FALL_DAMAGE");
        }
        if (physMult != 1.0) {
            event.setDamage(damage * physMult);
            damage = event.getDamage();
        }

        if (damage < 2.0) {
            showBloodHud(player, data);
            return;
        }

        double bleedMult = plugin.getSkillManager().getAbilityEffectValue(player, "BLEED_SPEED");
        double bloodLoss;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            bloodLoss = damage * 2.0;
        } else if (COMBAT_CAUSES.contains(event.getCause())) {
            bloodLoss = damage * 4.0;
        } else {
            bloodLoss = damage * 3.0;
        }
        bloodLoss *= bleedMult;

        data.setBlood(data.getBlood() - bloodLoss);

        applyBodyPartDamage(player, data, damage);

        double survivalChance = damage * 4.0;
        double infectionResist = plugin.getSkillManager().getAbilityEffectValue(player, "INFECTION_CHANCE");
        if (data.isTorsoInjured() && random.nextDouble() * 100 < survivalChance * infectionResist) {
            double infectionBoost = 15.0 + (damage * 1.5);
            data.setInfectionProgress(data.getInfectionProgress() + infectionBoost);
            if (data.getInfectionProgress() >= 30.0 && !data.isInfected()) {
                data.setInfected(true);
                player.sendMessage(ChatColor.DARK_RED + "傷口が感染してしまった!");
            }
        }

        if (data.getBlood() <= cfg.bleedingFatalThreshold) {
            if (data.getAbilityLevel("immortal") > 0) {
                event.setDamage(0);
                player.setHealth(1.0);
                data.setBlood(5.0);
                player.sendMessage(ChatColor.GOLD + "不死身が死を防いだ!");
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 2, false, false, true));
                return;
            }
            // 失血死: ダメージイベントの二重処理を防ぐため元ダメージを0にしてから死亡させる
            event.setDamage(0);
            player.setHealth(0);
            return;
        }

        event.setDamage(Math.max(0.5, damage * 0.8));

        applyBloodEffects(player, data, cfg);
        showBloodHud(player, data);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        double mult = plugin.getSkillManager().getAbilityEffectValue(player, "ALL_DAMAGE")
            * plugin.getSkillManager().getAbilityEffectValue(player, "MELEE_DAMAGE")
            * plugin.getSkillManager().getAbilityEffectValue(player, "STRENGTH_ALL_BOOST");
        if (player.isSneaking()) {
            // 隠密/暗殺者: 不意打ちダメージ
            mult *= plugin.getSkillManager().getAbilityEffectValue(player, "STEALTH_DAMAGE")
                * plugin.getSkillManager().getAbilityEffectValue(player, "AMBUSH_DAMAGE");
        }
        // カウンター: ガード直後の攻撃ダメージ+30%
        Long counterUntil = counterReady.get(player.getUniqueId());
        if (counterUntil != null && System.currentTimeMillis() < counterUntil) {
            counterReady.remove(player.getUniqueId());
            mult *= plugin.getSkillManager().getAbilityEffectValue(player, "GUARD_COUNTER");
        }
        if (mult != 1.0) {
            event.setDamage(event.getDamage() * mult);
        }
    }

    @EventHandler
    public void onBlocked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.isBlocking()) return;
        if (plugin.getDataManager().getData(player).getAbilityLevel("counter") > 0) {
            counterReady.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        }
    }

    @EventHandler
    public void onHitTaken(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double resist = plugin.getSkillManager().getAbilityEffectValue(player, "KNOCKBACK_RESIST");
        if (resist == 1.0) return;
        // 踏ん張り: ノックバックを軽減 (サーバーのKB適用後に減衰)
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.util.Vector v = player.getVelocity();
            if (v.lengthSquared() > 0.001) {
                player.setVelocity(v.multiply(resist));
            }
        });
    }

    @EventHandler
    public void onConsumeMedicine(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item.getType() == Material.POTION && item.getItemMeta() instanceof PotionMeta) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta.getBasePotionData() != null) {
                if (meta.getBasePotionData().getType() == PotionType.REGEN) {
                    plugin.getRecoveryManager().addInfectionCure(player, 50, 25);
                    plugin.getRecoveryManager().addBloodRegen(player, 15, 25);
                    player.sendMessage(ChatColor.GREEN + "再生のポーション。感染が徐々に抑えられ、血液が回復していく...");
                } else if (meta.getBasePotionData().getType() == PotionType.INSTANT_HEAL) {
                    plugin.getRecoveryManager().addBloodRegen(player, 30, 20);
                    plugin.getRecoveryManager().addHealthRegen(player, 6, 20);
                    plugin.getRecoveryManager().healInjury(player, "HEAD", 20);
                    plugin.getRecoveryManager().healInjury(player, "TORSO", 20);
                    plugin.getRecoveryManager().healInjury(player, "LEGS", 20);
                    plugin.getRecoveryManager().healFracture(player, 20);
                    player.sendMessage(ChatColor.GREEN + "治療のポーション。傷がじわじわと癒えていく...");
                }
            }
        }

        if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            plugin.getRecoveryManager().addBloodRegen(player, 25, 25);
            plugin.getRecoveryManager().addInfectionCure(player, 100, 25);
            player.sendMessage(ChatColor.GREEN + "金のリンゴ。体力がじわじわと回復していく...");
        }
    }

    @EventHandler
    public void onNaturalRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getRegainReason() == RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    private void applyBodyPartDamage(Player player, PlayerData data, double damage) {
        double roll = random.nextDouble() * 100;
        long now = System.currentTimeMillis();
        // 痛覚鈍化/免疫特化: 負傷デバフの強度・継続時間を軽減
        double injuryDebuff = plugin.getSkillManager().getAbilityEffectValue(player, "INJURY_DEBUFF");
        double debuffDuration = plugin.getSkillManager().getAbilityEffectValue(player, "DEBUFF_DURATION");
        int blindTicks = (int) (140 * debuffDuration);
        int confusionTicks = (int) (160 * debuffDuration);
        int slowTicks = (int) (100 * debuffDuration);
        int legSlowLevel = Math.max(0, (int) Math.round(1 * injuryDebuff));
        int fractureSlowLevel = Math.max(0, (int) Math.round(2 * injuryDebuff));

        if (roll < 20) {
            if (!data.isHeadInjured()) {
                data.setHeadInjured(true);
                injuryTimestamps.put(player.getUniqueId(), now);
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, confusionTicks, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slowTicks, 0, false, false, true));
                player.sendMessage(ChatColor.RED + "頭部に負傷! 視界が悪い。");
            }
        } else if (roll < 70) {
            if (!data.isTorsoInjured()) {
                data.setTorsoInjured(true);
                injuryTimestamps.put(player.getUniqueId(), now);
                data.setBlood(data.getBlood() - 5.0);
                player.sendMessage(ChatColor.RED + "胴部に負傷! 内出血と感染のリスク。");
            }
        } else {
            if (!data.isLegsInjured()) {
                data.setLegsInjured(true);
                injuryTimestamps.put(player.getUniqueId(), now);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int) (400 * debuffDuration), legSlowLevel, false, false, true));
                player.sendMessage(ChatColor.RED + "脚に負傷! 動きが鈍い。");
                if (!data.isFractured() && random.nextDouble() < 0.3) {
                    data.setFractured(true);
                    player.sendMessage(ChatColor.DARK_RED + "バキッという音が...脚を骨折した!");
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int) (600 * debuffDuration), fractureSlowLevel, false, false, true));
                }
            }
        }
    }

    private void applyBloodEffects(Player player, PlayerData data, ConfigManager cfg) {
        if (data.getBlood() < 30.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
        }
    }

    private void showBloodHud(Player player, PlayerData data) {
        double blood = data.getBlood();
        ChatColor color;
        if (blood > 60) color = ChatColor.RED;
        else if (blood > 30) color = ChatColor.DARK_RED;
        else color = ChatColor.DARK_RED;

        String bar = buildBloodBar(blood / 100.0, 10);
        StringBuilder status = new StringBuilder();
        if (data.isHeadInjured()) status.append(" 頭");
        if (data.isTorsoInjured()) status.append(" 胴");
        if (data.isLegsInjured()) status.append(" 脚");
        if (data.isInfected()) status.append(" 感染");
        if (data.isFractured()) status.append(" 骨折");

        MessageUtil.sendActionBar(player,
            ChatColor.RED + "血液: " + bar + " " + String.format("%.0f%%", blood) +
            ChatColor.DARK_RED + status.toString());
    }

    private String buildBloodBar(double pct, int segments) {
        int filled = (int) Math.round(pct * segments);
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_RED);
        for (int i = 0; i < filled; i++) sb.append("\u2588");
        sb.append(ChatColor.GRAY);
        for (int i = filled; i < segments; i++) sb.append("\u2588");
        return sb.toString();
    }

    private class RegenRunnable extends BukkitRunnable {
        private int damageCounter = 0;

        @Override
        public void run() {
            ConfigManager cfg = plugin.getConfigManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.getDataManager().getData(player);

                if (data.getNutritionBalance() > 50.0 && data.getBlood() < cfg.bleedingMaxBlood) {
                    double regenMult = plugin.getSkillManager().getAbilityEffectValue(player, "BLOOD_REGEN");
                    data.setBlood(data.getBlood() + cfg.bleedingRegenerationRate * regenMult);
                }

                if (data.isTorsoInjured()) {
                    boolean inWater = player.getLocation().getBlock().getType() == Material.WATER;
                    boolean contactDirt = isOnDirt(player);
                    double infectionSpeed = plugin.getSkillManager().getAbilityEffectValue(player, "INFECTION_SPEED");
                    if (inWater || contactDirt) {
                        data.setInfectionProgress(data.getInfectionProgress() + 1.0 * infectionSpeed);
                    }
                }

                if (data.isInfected()) {
                    double infectionSpeed = plugin.getSkillManager().getAbilityEffectValue(player, "INFECTION_SPEED");
                    data.setInfectionProgress(Math.min(100.0, data.getInfectionProgress() + 0.5 * infectionSpeed));
                    // 自然治癒: 感染症対処Ⅱスキル、または十分に栄養と水分が満ちていれば体が抗う
                    double naturalCure = plugin.getSkillManager().getAbilityEffectValue(player, "INFECTION_NATURAL_CURE");
                    boolean wellNourished = data.getNutritionBalance() > 60 && data.getHydration() > 60;
                    if ((naturalCure > 1.0 && random.nextDouble() < naturalCure - 1.0)
                        || (wellNourished && random.nextDouble() < 0.08)) {
                        data.setInfectionProgress(Math.max(0, data.getInfectionProgress() - 10.0));
                        if (data.getInfectionProgress() <= 0) {
                            data.setInfected(false);
                            player.sendMessage(ChatColor.GREEN + "体が感染症に打ち勝った!");
                        }
                    }
                    if (data.getInfectionProgress() >= 100.0) {
                        // 重症化: 症状は治療まで続くが、警告は1回だけ
                        if (!criticalInfectionWarned.getOrDefault(player.getUniqueId(), false)) {
                            criticalInfectionWarned.put(player.getUniqueId(), true);
                            player.sendMessage(ChatColor.DARK_RED + "感染症が重症化した! 抗生物質や消毒液で治療しないと命に関わる!");
                        }
                        player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 220, 0, false, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 0, false, false, true));
                        // 直接ダメージは5秒に1回の軽いものに抑え、治療の猶予を与える (詰み防止)
                        damageCounter++;
                        if (damageCounter % 5 == 0) {
                            player.damage(0.5);
                        }
                    } else {
                        // 治療で重症化を脱したら再度警告できるようにする
                        criticalInfectionWarned.put(player.getUniqueId(), false);
                    }
                }

                applyBloodEffects(player, data, cfg);
            }
        }
    }

    private boolean isOnDirt(Player player) {
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        return blockBelow == Material.DIRT || blockBelow == Material.GRASS_BLOCK
            || blockBelow == Material.COARSE_DIRT || blockBelow == Material.PODZOL
            || blockBelow == Material.MYCELIUM || blockBelow == Material.ROOTED_DIRT;
    }

    private class HealRunnable extends BukkitRunnable {
        @Override
        public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.getDataManager().getData(player);

                if (data.isFractured() && data.getNutritionBalance() > 40.0) {
                    data.setFractured(false);
                    player.sendMessage(ChatColor.GREEN + "骨折が治った。");
                }
            }
        }
    }
}
