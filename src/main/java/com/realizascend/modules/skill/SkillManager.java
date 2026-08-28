package com.realizascend.modules.skill;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SkillManager extends RealizModule implements Listener {

    public static class SkillAbility {
        private final String id;
        private final String skillTree;
        private final String name;
        private final String description;
        private final int cost;
        private final List<String> prerequisites;
        private final boolean exclusive;
        private final String exclusiveGroup;
        private final int maxLevel;
        private final String effectType;
        private final double effectValue;

        public SkillAbility(String id, String skillTree, String name, String description, int cost,
                            List<String> prerequisites, boolean exclusive, String exclusiveGroup,
                            int maxLevel, String effectType, double effectValue) {
            this.id = id;
            this.skillTree = skillTree;
            this.name = name;
            this.description = description;
            this.cost = cost;
            this.prerequisites = prerequisites;
            this.exclusive = exclusive;
            this.exclusiveGroup = exclusiveGroup;
            this.maxLevel = maxLevel;
            this.effectType = effectType;
            this.effectValue = effectValue;
        }

        public String getId() { return id; }
        public String getSkillTree() { return skillTree; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getCost() { return cost; }
        public List<String> getPrerequisites() { return prerequisites; }
        public boolean isExclusive() { return exclusive; }
        public String getExclusiveGroup() { return exclusiveGroup; }
        public int getMaxLevel() { return maxLevel; }
        public String getEffectType() { return effectType; }
        public double getEffectValue() { return effectValue; }
    }

    public static final Map<String, SkillAbility> ALL_ABILITIES = new LinkedHashMap<>();
    public static final Map<String, List<SkillAbility>> ABILITIES_BY_TREE = new LinkedHashMap<>();
    public static final Map<String, String> SKILL_TREE_DISPLAY_NAMES = new LinkedHashMap<>();
    public static final String[] SKILL_TREES = {
        "ENDURANCE", "STRENGTH", "RESISTANCE", "METABOLISM",
        "COOKING", "MEDICAL", "BUILDING", "FARMING", "COMBAT"
    };

    private static void reg(String id, String tree, String name, String desc, int cost,
                            List<String> prereqs, boolean excl, String exclGrp,
                            int maxLvl, String effType, double effVal) {
        SkillAbility a = new SkillAbility(id, tree, name, desc, cost, prereqs, excl, exclGrp, maxLvl, effType, effVal);
        ALL_ABILITIES.put(id, a);
        ABILITIES_BY_TREE.computeIfAbsent(tree, k -> new ArrayList<>()).add(a);
    }

    static {
        SKILL_TREE_DISPLAY_NAMES.put("ENDURANCE", "\u6301\u4E45\u529B");
        SKILL_TREE_DISPLAY_NAMES.put("STRENGTH", "\u7B4B\u529B");
        SKILL_TREE_DISPLAY_NAMES.put("RESISTANCE", "\u8010\u6027");
        SKILL_TREE_DISPLAY_NAMES.put("METABOLISM", "\u4EE3\u8B1D");
        SKILL_TREE_DISPLAY_NAMES.put("COOKING", "\u6599\u7406");
        SKILL_TREE_DISPLAY_NAMES.put("MEDICAL", "\u533B\u7642");
        SKILL_TREE_DISPLAY_NAMES.put("BUILDING", "\u5EFA\u7BC9");
        SKILL_TREE_DISPLAY_NAMES.put("FARMING", "\u8FB2\u696D");
        SKILL_TREE_DISPLAY_NAMES.put("COMBAT", "\u6226\u95D8");

        List<String> empty = Collections.emptyList();

        // ===== ENDURANCE =====
        reg("endurance_1", "ENDURANCE", "持久力Ⅰ", "スタミナ上限+10%", 1, empty, false, null, 1, "MAX_STAMINA", 0.10);
        reg("endurance_2", "ENDURANCE", "持久力Ⅱ", "スタミナ上限+20%", 2, List.of("endurance_1"), false, null, 1, "MAX_STAMINA", 0.20);
        reg("endurance_3", "ENDURANCE", "持久力Ⅲ", "スタミナ上限+30%", 3, List.of("endurance_2"), false, null, 1, "MAX_STAMINA", 0.30);
        reg("efficient_run", "ENDURANCE", "省エネ走法", "走行スタミナ消費-15%", 2, empty, false, null, 1, "SPRINT_COST", -0.15);
        reg("efficient_run_2", "ENDURANCE", "省エネ走法Ⅱ", "走行スタミナ消費-30%", 3, List.of("efficient_run"), false, null, 1, "SPRINT_COST", -0.30);
        reg("iron_lung", "ENDURANCE", "鉄の肺", "水中スタミナ消費-30%", 2, empty, false, null, 1, "WATER_STAMINA_COST", -0.30);
        reg("iron_lung_2", "ENDURANCE", "鉄の肺Ⅱ", "水中スタミナ消費-60%", 3, List.of("iron_lung"), false, null, 1, "WATER_STAMINA_COST", -0.60);
        reg("second_wind", "ENDURANCE", "第二の風", "スタミナ切れ後に回復バースト", 5, empty, false, null, 1, "STAMINA_BURST", 1.0);
        reg("high_altitude", "ENDURANCE", "高地順応", "高標高でのスタミナ消費増加を軽減", 3, empty, false, null, 1, "ALTITUDE_STAMINA", 1.0);
        reg("indomitable", "ENDURANCE", "不屈", "スタミナ0でも行動可能", 8, empty, false, null, 1, "ZERO_STAMINA_ACTION", 1.0);
        reg("speed_sprint", "ENDURANCE", "二択A：短距離型", "瞬間回復速度UP・上限低め", 5, empty, true, "endurance_speed_type", 1, "STAMINA_INSTANT_REGEN", 0.30);
        reg("speed_marathon", "ENDURANCE", "二択B：長距離型", "上限大幅UP・回復ゆっくり", 5, empty, true, "endurance_speed_type", 1, "MAX_STAMINA", 0.30);
        reg("breath_control", "ENDURANCE", "呼吸制御", "疲労時のスタミナ回復+20%", 2, empty, false, null, 1, "FATIGUE_STAMINA_REGEN", 0.20);
        reg("climber", "ENDURANCE", "登山家", "登坂スタミナ消費-20%", 2, empty, false, null, 1, "CLIMB_COST", -0.20);
        reg("climber_2", "ENDURANCE", "登山家Ⅱ", "登坂スタミナ消費-30%", 3, List.of("climber"), false, null, 1, "CLIMB_COST", -0.30);
        reg("energy_save", "ENDURANCE", "体力温存", "歩行スタミナ消費-20%", 1, empty, false, null, 1, "WALK_COST", -0.20);
        reg("steel_legs", "ENDURANCE", "鋼の足腰", "落下ダメージ時のスタミナ消費なし", 2, empty, false, null, 1, "FALL_STAMINA_COST", -1.00);
        reg("combat_breathing", "ENDURANCE", "戦闘呼吸", "戦闘中スタミナ回復", 5, empty, false, null, 1, "COMBAT_STAMINA_REGEN", 1.0);
        reg("combat_breathing_2", "ENDURANCE", "戦闘呼吸Ⅱ", "戦闘中スタミナ回復速度+30%", 8, List.of("combat_breathing"), false, null, 1, "COMBAT_STAMINA_REGEN", 1.3);
        reg("super_recovery", "ENDURANCE", "超回復", "睡眠でスタミナ上限永続+5%", 10, empty, false, null, 1, "SLEEP_STAMINA_BOOST", 0.05);
        reg("no_mind", "ENDURANCE", "無我の境地", "スタミナ0時ペナルティ大幅軽減", 8, empty, false, null, 1, "ZERO_STAMINA_PENALTY_REDUCE", 0.5);
        reg("heart_lung", "ENDURANCE", "心肺強化", "体温警告ゾーンでのスタミナ消費増加を軽減", 3, empty, false, null, 1, "TEMP_WARNING_STAMINA", 0.5);
        reg("heart_lung_2", "ENDURANCE", "心肺強化Ⅱ", "危険ゾーンでもスタミナ消費増加を無効化", 5, List.of("heart_lung"), false, null, 1, "TEMP_WARNING_STAMINA", 1.0);

        // ===== STRENGTH =====
        reg("strength_1", "STRENGTH", "筋力Ⅰ", "重量上限+10%", 1, empty, false, null, 1, "MAX_WEIGHT", 0.10);
        reg("strength_2", "STRENGTH", "筋力Ⅱ", "重量上限+20%", 2, List.of("strength_1"), false, null, 1, "MAX_WEIGHT", 0.20);
        reg("strength_3", "STRENGTH", "筋力Ⅲ", "重量上限+30%", 3, List.of("strength_2"), false, null, 1, "MAX_WEIGHT", 0.30);
        reg("strong_arm", "STRENGTH", "剛腕", "近接攻撃ダメージ+10%", 2, empty, false, null, 1, "MELEE_DAMAGE", 0.10);
        reg("strong_arm_2", "STRENGTH", "剛腕Ⅱ", "近接攻撃ダメージ+20%", 3, List.of("strong_arm"), false, null, 1, "MELEE_DAMAGE", 0.20);
        reg("strong_arm_3", "STRENGTH", "剛腕Ⅲ", "近接攻撃ダメージ+30%", 5, List.of("strong_arm_2"), false, null, 1, "MELEE_DAMAGE", 0.30);
        reg("throwing", "STRENGTH", "投擲", "投擲距離・ダメージ+20%", 2, empty, false, null, 1, "THROW_DISTANCE", 0.20);
        reg("throwing_2", "STRENGTH", "投擲Ⅱ", "投擲物に貫通効果", 5, List.of("throwing"), false, null, 1, "THROW_PIERCE", 1.0);
        reg("heavy_adapt", "STRENGTH", "重装適性", "重量オーバーペナルティ軽減", 3, empty, false, null, 1, "OVERWEIGHT_PENALTY", -0.30);
        reg("heavy_adapt_2", "STRENGTH", "重装適性Ⅱ", "ジャンプ不可になるまでの閾値UP", 5, List.of("heavy_adapt"), false, null, 1, "OVERWEIGHT_JUMP_THRESHOLD", 0.30);
        reg("iron_grip", "STRENGTH", "鉄の握力", "武器耐久消費-15%", 2, empty, false, null, 1, "WEAPON_DURABILITY", -0.15);
        reg("iron_grip_2", "STRENGTH", "鉄の握力Ⅱ", "武器耐久消費-30%", 3, List.of("iron_grip"), false, null, 1, "WEAPON_DURABILITY", -0.30);
        reg("destruction", "STRENGTH", "破壊衝動", "工具耐久消費-10%", 1, empty, false, null, 1, "TOOL_DURABILITY", -0.10);
        reg("destruction_2", "STRENGTH", "破壊衝動Ⅱ", "採掘速度+15%", 2, List.of("destruction"), false, null, 1, "MINE_SPEED", 0.15);
        reg("jump_boost", "STRENGTH", "跳躍力", "ジャンプ高さ+1", 3, empty, false, null, 1, "JUMP_HEIGHT", 1.0);
        reg("sturdy", "STRENGTH", "踏ん張り", "ノックバック耐性+30%", 2, empty, false, null, 1, "KNOCKBACK_RESIST", 0.30);
        reg("sturdy_2", "STRENGTH", "踏ん張りⅡ", "ノックバック完全無効", 5, List.of("sturdy"), false, null, 1, "KNOCKBACK_RESIST", 1.0);
        reg("manual_labor", "STRENGTH", "肉体労働", "重労働カロリー消費-10%", 2, empty, false, null, 1, "HEAVY_WORK_CALORIE", -0.10);
        reg("super_strength", "STRENGTH", "怪力", "重いブロックを素手で運べる", 3, empty, false, null, 1, "HEAVY_BLOCK_CARRY", 1.0);
        reg("power_type", "STRENGTH", "二択A：パワー型", "近接ダメージ大幅UP", 8, empty, true, "strength_style", 1, "MELEE_DAMAGE", 0.30);
        reg("tech_type", "STRENGTH", "二択B：テクニック型", "近接スタミナ消費-20%", 8, empty, true, "strength_style", 1, "MELEE_STAMINA_COST", -0.20);
        reg("muscles_dont_lie", "STRENGTH", "筋肉は裏切らない", "筋力スキル効果+5%", 10, empty, false, null, 1, "STRENGTH_ALL_BOOST", 0.05);

        // ===== RESISTANCE =====
        reg("resistance_1", "RESISTANCE", "耐性Ⅰ", "病気確率-10%", 1, empty, false, null, 1, "DISEASE_CHANCE", -0.10);
        reg("resistance_2", "RESISTANCE", "耐性Ⅱ", "病気確率-20%", 2, List.of("resistance_1"), false, null, 1, "DISEASE_CHANCE", -0.20);
        reg("resistance_3", "RESISTANCE", "耐性Ⅲ", "病気確率-30%", 3, List.of("resistance_2"), false, null, 1, "DISEASE_CHANCE", -0.30);
        reg("infection_resist", "RESISTANCE", "感染耐性", "感染リスク-20%", 2, empty, false, null, 1, "INFECTION_CHANCE", -0.20);
        reg("infection_resist_2", "RESISTANCE", "感染耐性Ⅱ", "感染リスク-40%", 3, List.of("infection_resist"), false, null, 1, "INFECTION_CHANCE", -0.40);
        reg("steel_stomach", "RESISTANCE", "鋼の胃袋", "腐敗食・汚染水デバフ軽減", 3, empty, false, null, 1, "ROTTEN_DEBUFF", 0.50);
        reg("steel_stomach_2", "RESISTANCE", "鋼の胃袋Ⅱ", "腐敗食・汚染水デバフほぼ無効化", 5, List.of("steel_stomach"), false, null, 1, "ROTTEN_DEBUFF", 1.0);
        reg("temp_resist", "RESISTANCE", "体温耐性", "体温警告ゾーン移行が遅い", 2, empty, false, null, 1, "TEMP_WARNING_DELAY", 0.20);
        reg("temp_resist_2", "RESISTANCE", "体温耐性Ⅱ", "体温危険ゾーン移行が遅い", 3, List.of("temp_resist"), false, null, 1, "TEMP_CRITICAL_DELAY", 0.20);
        reg("poison_resist", "RESISTANCE", "毒耐性", "毒の継続時間-30%", 2, empty, false, null, 1, "POISON_DURATION", -0.30);
        reg("poison_resist_2", "RESISTANCE", "毒耐性Ⅱ", "毒の継続時間-50%", 5, List.of("poison_resist"), false, null, 1, "POISON_DURATION", -0.50);
        reg("toughness", "RESISTANCE", "打たれ強さ", "物理ダメージ-5%", 2, empty, false, null, 1, "PHYSICAL_DAMAGE", -0.05);
        reg("toughness_2", "RESISTANCE", "打たれ強さⅡ", "物理ダメージ-10%", 3, List.of("toughness"), false, null, 1, "PHYSICAL_DAMAGE", -0.10);
        reg("insomnia_resist", "RESISTANCE", "不眠耐性", "睡眠不足デバフが遅い", 2, empty, false, null, 1, "SLEEP_DEBT_DELAY", 1.0);
        reg("pain_dull", "RESISTANCE", "痛覚鈍化", "負傷デバフ強度-20%", 3, empty, false, null, 1, "INJURY_DEBUFF", -0.20);
        reg("pain_dull_2", "RESISTANCE", "痛覚鈍化Ⅱ", "負傷デバフ強度-40%", 5, List.of("pain_dull"), false, null, 1, "INJURY_DEBUFF", -0.40);
        reg("stress_resist", "RESISTANCE", "ストレス耐性", "ストレス上昇-15%", 2, empty, false, null, 1, "STRESS_RISE", -0.15);
        reg("stress_resist_2", "RESISTANCE", "ストレス耐性Ⅱ", "ストレス上昇-30%", 3, List.of("stress_resist"), false, null, 1, "STRESS_RISE", -0.30);
        reg("immune_special", "RESISTANCE", "二択A：免疫特化", "デバフ継続-30%", 8, empty, true, "resistance_type", 1, "DEBUFF_DURATION", -0.30);
        reg("body_special", "RESISTANCE", "二択B：肉体特化", "物理ダメージ-15%", 8, empty, true, "resistance_type", 1, "PHYSICAL_DAMAGE", -0.15);
        reg("immortal", "RESISTANCE", "不死身", "死を1度だけ防ぐ", 10, empty, false, null, 1, "DEATH_PREVENT", 1.0);

        // ===== METABOLISM =====
        reg("metabolism_1", "METABOLISM", "代謝促進Ⅰ", "カロリー消費効率+10%", 1, empty, false, null, 1, "CALORIE_EFFICIENCY", 0.10);
        reg("metabolism_2", "METABOLISM", "代謝促進Ⅱ", "カロリー消費効率+20%", 2, List.of("metabolism_1"), false, null, 1, "CALORIE_EFFICIENCY", 0.20);
        reg("metabolism_3", "METABOLISM", "代謝促進Ⅲ", "カロリー消費効率+30%", 3, List.of("metabolism_2"), false, null, 1, "CALORIE_EFFICIENCY", 0.30);
        reg("water_save", "METABOLISM", "節水", "水分消費-10%", 1, empty, false, null, 1, "HYDRATION_CONSUME", -0.10);
        reg("water_save_2", "METABOLISM", "節水Ⅱ", "水分消費-25%", 2, List.of("water_save"), false, null, 1, "HYDRATION_CONSUME", -0.25);
        reg("water_save_3", "METABOLISM", "節水Ⅲ", "水分消費-40%", 3, List.of("water_save_2"), false, null, 1, "HYDRATION_CONSUME", -0.40);
        reg("nutrient_absorb", "METABOLISM", "栄養吸収", "栄養獲得+15%", 2, empty, false, null, 1, "NUTRIENT_GAIN", 0.15);
        reg("nutrient_absorb_2", "METABOLISM", "栄養吸収Ⅱ", "栄養獲得+30%", 3, List.of("nutrient_absorb"), false, null, 1, "NUTRIENT_GAIN", 0.30);
        reg("fat_burn", "METABOLISM", "脂肪燃焼", "寒さのカロリー消費-20%", 2, empty, false, null, 1, "COLD_CALORIE_COST", -0.20);
        reg("fat_burn_2", "METABOLISM", "脂肪燃焼Ⅱ", "寒さのカロリー消費-40%", 3, List.of("fat_burn"), false, null, 1, "COLD_CALORIE_COST", -0.40);
        reg("sweat_control", "METABOLISM", "発汗制御", "暑さの水分消費-20%", 2, empty, false, null, 1, "HOT_HYDRATION_COST", -0.20);
        reg("sweat_control_2", "METABOLISM", "発汗制御Ⅱ", "暑さの水分消費-40%", 3, List.of("sweat_control"), false, null, 1, "HOT_HYDRATION_COST", -0.40);
        reg("small_eater", "METABOLISM", "少食家", "胃袋減少速度-15%", 2, empty, false, null, 1, "STOMACH_DECAY", -0.15);
        reg("small_eater_2", "METABOLISM", "少食家Ⅱ", "胃袋減少速度-30%", 3, List.of("small_eater"), false, null, 1, "STOMACH_DECAY", -0.30);
        reg("salt_balance", "METABOLISM", "塩分調整", "塩分デバフが遅い", 2, empty, false, null, 1, "SALT_DEBUFF_DELAY", 0.30);
        reg("recovery_boost", "METABOLISM", "回復促進", "食事後のHP回復+20%", 2, empty, false, null, 1, "FOOD_HP_REGEN", 0.20);
        reg("recovery_boost_2", "METABOLISM", "回復促進Ⅱ", "食事後のHP回復+40%", 3, List.of("recovery_boost"), false, null, 1, "FOOD_HP_REGEN", 0.40);
        reg("blood_make", "METABOLISM", "血液製造", "血液再生+15%", 3, empty, false, null, 1, "BLOOD_REGEN", 0.15);
        reg("blood_make_2", "METABOLISM", "血液製造Ⅱ", "血液再生+30%", 5, List.of("blood_make"), false, null, 1, "BLOOD_REGEN", 0.30);
        reg("energy_save_type", "METABOLISM", "二択A：省エネ型", "カロリー・水分消費大幅減", 8, empty, true, "metabolism_type", 1, "CALORIE_HYDRATION_SAVE", 0.20);
        reg("burn_type", "METABOLISM", "二択B：過燃焼型", "スタミナ上限大幅UP", 8, empty, true, "metabolism_type", 1, "MAX_STAMINA", 0.25);
        reg("super_metabolism", "METABOLISM", "超代謝", "全栄養素効率+10%", 10, empty, false, null, 1, "ALL_EFFICIENCY", 0.10);

        // ===== COOKING =====
        reg("cook_grill_1", "COOKING", "焼き料理Ⅰ", "基本焼き料理解放", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_grill_2", "COOKING", "焼き料理Ⅱ", "中級焼き料理解放", 2, List.of("cook_grill_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_grill_3", "COOKING", "焼き料理Ⅲ", "上級焼き料理解放", 5, List.of("cook_grill_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_stew_1", "COOKING", "煮込み料理Ⅰ", "基本煮込み料理解放", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_stew_2", "COOKING", "煮込み料理Ⅱ", "中級煮込み料理解放", 2, List.of("cook_stew_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_stew_3", "COOKING", "煮込み料理Ⅲ", "上級煮込み料理解放", 5, List.of("cook_stew_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_smoke_1", "COOKING", "燻製Ⅰ", "基本燻製解放", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_smoke_2", "COOKING", "燻製Ⅱ", "中級燻製解放・保存期間延長", 2, List.of("cook_smoke_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_smoke_3", "COOKING", "燻製Ⅲ", "上級燻製解放・栄養素+20%", 5, List.of("cook_smoke_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_preserve_1", "COOKING", "塩漬け・乾燥Ⅰ", "基本保存食解放", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_preserve_2", "COOKING", "塩漬け・乾燥Ⅱ", "中級保存食解放・腐敗速度低下", 2, List.of("cook_preserve_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_preserve_3", "COOKING", "塩漬け・乾燥Ⅲ", "上級保存食解放・保存期間延長", 5, List.of("cook_preserve_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_instant_1", "COOKING", "即席料理Ⅰ", "即席料理解放", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_instant_2", "COOKING", "即席料理Ⅱ", "即席料理の出来栄え上限UP", 2, List.of("cook_instant_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_instant_3", "COOKING", "即席料理Ⅲ", "即席料理の栄養素+20%", 5, List.of("cook_instant_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_prep", "COOKING", "下準備", "下処理速度+20%", 1, empty, false, null, 1, "COOK_SPEED", 0.20);
        reg("cook_prep_2", "COOKING", "下準備Ⅱ", "食材ロス-20%", 2, List.of("cook_prep"), false, null, 1, "COOK_WASTE", -0.20);
        reg("cook_eye", "COOKING", "食材目利き", "食材の品質・鮮度確認", 2, empty, false, null, 1, "COOK_QUALITY_SEE", 1.0);
        reg("cook_eye_2", "COOKING", "食材目利きⅡ", "腐敗食材を加工して延命", 3, List.of("cook_eye"), false, null, 1, "COOK_ROTTEN_USE", 1.0);
        reg("cook_seasoning", "COOKING", "調味師", "塩分調整の精度UP", 2, empty, false, null, 1, "COOK_SALT_PRECISION", 1.0);
        reg("cook_specialist", "COOKING", "二択A：専門料理人", "特定ジャンル+2ランク", 8, empty, true, "cook_type", 1, "COOK_SPECIALTY", 2.0);
        reg("cook_all_rounder", "COOKING", "二択B：万能料理人", "全ジャンル+1ランク", 8, empty, true, "cook_type", 1, "COOK_ALL", 1.0);
        reg("cook_supreme", "COOKING", "至高の一皿", "最高品質料理の栄養+50%", 10, empty, false, null, 1, "COOK_PERFECT_BONUS", 0.50);

        // ===== MEDICAL =====
        reg("med_first_aid_1", "MEDICAL", "応急処置Ⅰ", "包帯速度UP", 1, empty, false, null, 1, "BANDAGE_SPEED", 0.20);
        reg("med_first_aid_2", "MEDICAL", "応急処置Ⅱ", "骨折の応急処置可能", 2, List.of("med_first_aid_1"), false, null, 1, "FRACTURE_TREAT", 1.0);
        reg("med_sterilize", "MEDICAL", "滅菌消毒", "処置の感染リスク低下", 2, empty, false, null, 1, "INFECTION_TREAT_CHANCE", 0.30);
        reg("med_emergency", "MEDICAL", "救急隊の常識", "処置中移動可能", 3, empty, false, null, 1, "TREAT_MOVE", 1.0);
        reg("med_hemostasis", "MEDICAL", "止血術", "出血進行-30%", 2, empty, false, null, 1, "BLEED_SPEED", -0.30);
        reg("med_hemostasis_2", "MEDICAL", "止血術Ⅱ", "一時止血可能", 5, List.of("med_hemostasis"), false, null, 1, "BLEED_TEMP_STOP", 1.0);
        reg("med_surgery_1", "MEDICAL", "外科処置Ⅰ", "深い傷の処置可能", 3, empty, false, null, 1, "DEEP_WOUND_TREAT", 1.0);
        reg("med_surgery_2", "MEDICAL", "外科処置Ⅱ", "縫合速度UP・感染リスク低下", 5, List.of("med_surgery_1"), false, null, 1, "SUTURE_SPEED", 0.30);
        reg("med_pharma_1", "MEDICAL", "薬学Ⅰ", "基本薬品解放", 3, empty, false, null, 1, "BASIC_MEDS", 1.0);
        reg("med_pharma_2", "MEDICAL", "薬学Ⅱ", "中級薬品解放", 5, List.of("med_pharma_1"), false, null, 1, "MED_MEDS", 1.0);
        reg("med_pharma_3", "MEDICAL", "薬学Ⅲ", "上級薬品解放", 8, List.of("med_pharma_2"), false, null, 1, "ADVANCED_MEDS", 1.0);
        reg("med_fracture_1", "MEDICAL", "骨折治療Ⅰ", "固定精度UP・回復速度+20%", 2, empty, false, null, 1, "FRACTURE_FIX_QUALITY", 0.20);
        reg("med_fracture_2", "MEDICAL", "骨折治療Ⅱ", "ギプス解放・骨折デバフ軽減", 5, List.of("med_fracture_1"), false, null, 1, "CAST_CRAFT", 1.0);
        reg("med_infection_1", "MEDICAL", "感染症対処", "感染進行-30%", 2, empty, false, null, 1, "INFECTION_SPEED", -0.30);
        reg("med_infection_2", "MEDICAL", "感染症対処Ⅱ", "自然治癒確率UP", 5, List.of("med_infection_1"), false, null, 1, "INFECTION_NATURAL_CURE", 0.30);
        reg("med_triage", "MEDICAL", "トリアージ", "複数負傷の把握・優先順位表示", 3, empty, false, null, 1, "MULTI_INJURY_INFO", 1.0);
        reg("med_field_1", "MEDICAL", "野戦医療", "戦闘中の処置効果+20%", 3, empty, false, null, 1, "COMBAT_TREAT_EFFECT", 0.20);
        reg("med_field_2", "MEDICAL", "野戦医療Ⅱ", "戦闘中の処置速度+30%", 5, List.of("med_field_1"), false, null, 1, "COMBAT_TREAT_SPEED", 0.30);
        reg("med_surgery_spec", "MEDICAL", "二択A：外科特化", "外科効果+30%", 8, empty, true, "med_type", 1, "SURGERY_BOOST", 0.30);
        reg("med_pharma_spec", "MEDICAL", "二択B：薬学特化", "薬品効果+30%", 8, empty, true, "med_type", 1, "MEDS_BOOST", 0.30);
        reg("med_god_hand", "MEDICAL", "神の手", "全処置の効果大幅UP", 10, empty, false, null, 1, "ALL_TREAT_BOOST", 0.30);

        // ===== BUILDING =====
        reg("build_1", "BUILDING", "建築Ⅰ", "基本建材クラフト解放", 1, empty, false, null, 1, "BUILD_UNLOCK", 1.0);
        reg("build_2", "BUILDING", "建築Ⅱ", "中級建材クラフト解放", 2, List.of("build_1"), false, null, 1, "BUILD_UNLOCK", 2.0);
        reg("build_3", "BUILDING", "建築Ⅲ", "上級建材クラフト解放", 3, List.of("build_2"), false, null, 1, "BUILD_UNLOCK", 3.0);
        reg("structure_1", "BUILDING", "構造理解Ⅰ", "基本構造解放", 1, List.of("build_1"), false, null, 1, "STRUCTURE_UNLOCK", 1.0);
        reg("structure_2", "BUILDING", "構造理解Ⅱ", "上級構造解放", 3, List.of("structure_1"), false, null, 1, "STRUCTURE_UNLOCK", 2.0);
        reg("structure_3", "BUILDING", "構造理解Ⅲ", "設置ブロックが落下しない", 10, List.of("structure_2"), false, null, 1, "BLOCK_GRAVITY_IMMUNE", 1.0);
        reg("scaffold_1", "BUILDING", "足場師", "落下ダメージ-30%", 2, empty, false, null, 1, "FALL_DAMAGE", -0.30);
        reg("scaffold_2", "BUILDING", "足場師Ⅱ", "落下ダメージ-50%", 3, List.of("scaffold_1"), false, null, 1, "FALL_DAMAGE", -0.50);
        reg("masonry_1", "BUILDING", "石工", "石材消費-15%", 1, empty, false, null, 1, "STONE_MAT_COST", -0.15);
        reg("masonry_2", "BUILDING", "石工Ⅱ", "石材品質UP", 2, List.of("masonry_1"), false, null, 1, "STONE_QUALITY", 0.10);
        reg("woodwork_1", "BUILDING", "木工", "木材消費-15%", 1, empty, false, null, 1, "WOOD_MAT_COST", -0.15);
        reg("woodwork_2", "BUILDING", "木工Ⅱ", "木材品質UP", 2, List.of("woodwork_1"), false, null, 1, "WOOD_QUALITY", 0.10);
        reg("metalwork_1", "BUILDING", "金属加工", "金属建材解放", 3, empty, false, null, 1, "METAL_BUILD_UNLOCK", 1.0);
        reg("metalwork_2", "BUILDING", "金属加工Ⅱ", "金属耐久+20%・素材消費-10%", 5, List.of("metalwork_1"), false, null, 1, "METAL_DURABILITY", 0.20);
        reg("insulation_1", "BUILDING", "断熱設計", "拠点の気温変動軽減", 3, empty, false, null, 1, "BASE_TEMP_FLUCTUATION", -0.30);
        reg("insulation_2", "BUILDING", "断熱設計Ⅱ", "拠点の気温を快適ゾーンに保ちやすい", 5, List.of("insulation_1"), false, null, 1, "BASE_TEMP_STABLE", 0.50);
        reg("trap_1", "BUILDING", "罠師", "罠解放", 3, empty, false, null, 1, "TRAP_UNLOCK", 1.0);
        reg("trap_2", "BUILDING", "罠師Ⅱ", "罠効果+30%", 5, List.of("trap_1"), false, null, 1, "TRAP_EFFECT", 0.30);
        reg("fast_build", "BUILDING", "二択A：速築き", "設置スタミナ消費-40%", 8, empty, true, "build_type", 1, "BUILD_STAMINA_COST", -0.40);
        reg("artisan", "BUILDING", "二択B：職人気質", "建材品質上限+1ランク", 8, empty, true, "build_type", 1, "BUILD_QUALITY_CAP", 1.0);
        reg("master_builder", "BUILDING", "マスタービルダー", "全建材の素材消費-20%", 10, empty, false, null, 1, "BUILD_ALL_BOOST", 0.20);

        // ===== FARMING =====
        reg("farm_1", "FARMING", "農業Ⅰ", "基本作物栽培解放", 1, empty, false, null, 1, "FARM_UNLOCK", 1.0);
        reg("farm_2", "FARMING", "農業Ⅱ", "中級作物栽培解放", 2, List.of("farm_1"), false, null, 1, "FARM_UNLOCK", 2.0);
        reg("farm_3", "FARMING", "農業Ⅲ", "上級作物栽培解放", 3, List.of("farm_2"), false, null, 1, "FARM_UNLOCK", 3.0);
        reg("soil_knowledge", "FARMING", "土壌知識", "土の状態確認", 2, empty, false, null, 1, "SOIL_INFO", 1.0);
        reg("soil_knowledge_2", "FARMING", "土壌知識Ⅱ", "土壌改良アイテム解放", 3, List.of("soil_knowledge"), false, null, 1, "SOIL_IMPROVE", 1.0);
        reg("season_read", "FARMING", "季節読み", "季節の適性表示", 1, empty, false, null, 1, "SEASON_CROP_INFO", 1.0);
        reg("season_read_2", "FARMING", "季節読みⅡ", "季節外でも育つ", 5, List.of("season_read"), false, null, 1, "OFF_SEASON_GROW", 0.50);
        reg("greenhouse_1", "FARMING", "温室管理", "温室内水やり+20%", 3, empty, false, null, 1, "GREENHOUSE_WATER", 0.20);
        reg("greenhouse_2", "FARMING", "温室管理Ⅱ", "冬でも夏作物が育てられる", 5, List.of("greenhouse_1"), false, null, 1, "GREENHOUSE_WINTER", 1.0);
        reg("water_mgmt_1", "FARMING", "水管理", "水やり効果+20%", 2, empty, false, null, 1, "WATER_GROW_BOOST", 0.20);
        reg("water_mgmt_2", "FARMING", "水管理Ⅱ", "過湿耐性", 3, List.of("water_mgmt_1"), false, null, 1, "OVERWATER_RESIST", 0.50);
        reg("pest_1", "FARMING", "害虫対策", "害虫リスク低下", 2, empty, false, null, 1, "PEST_RESIST", 0.30);
        reg("pest_2", "FARMING", "害虫対策Ⅱ", "対策アイテム解放", 3, List.of("pest_1"), false, null, 1, "PEST_CRAFT", 1.0);
        reg("harvest_1", "FARMING", "収穫術", "収穫量+20%", 2, empty, false, null, 1, "HARVEST_DROP", 0.20);
        reg("harvest_2", "FARMING", "収穫術Ⅱ", "種追加ドロップ", 3, List.of("harvest_1"), false, null, 1, "HARVEST_SEED_DROP", 0.20);
        reg("preserve_farm_1", "FARMING", "保存農業", "作物腐敗-20%", 2, empty, false, null, 1, "CROP_DECAY", -0.20);
        reg("preserve_farm_2", "FARMING", "保存農業Ⅱ", "品質劣化-30%", 3, List.of("preserve_farm_1"), false, null, 1, "CROP_QUALITY_DECAY", -0.30);
        reg("breeding", "FARMING", "品種改良", "作物品質上限+1ランク", 5, empty, false, null, 1, "CROP_QUALITY_CAP", 1.0);
        reg("multi_harvest", "FARMING", "二択A：多収穫型", "収穫量+40%", 8, empty, true, "farm_type", 1, "HARVEST_DROP", 0.40);
        reg("quality_farm", "FARMING", "二択B：高品質型", "品質大幅UP", 8, empty, true, "farm_type", 1, "CROP_QUALITY", 0.30);
        reg("earth_blessing", "FARMING", "大地の恵み", "自然成長+20%", 10, empty, false, null, 1, "NATURAL_GROW", 0.20);

        // ===== COMBAT =====
        reg("combat_train_1", "COMBAT", "戦闘訓練Ⅰ", "全武器ダメージ+5%", 1, empty, false, null, 1, "ALL_DAMAGE", 0.05);
        reg("combat_train_2", "COMBAT", "戦闘訓練Ⅱ", "全武器ダメージ+10%", 2, List.of("combat_train_1"), false, null, 1, "ALL_DAMAGE", 0.10);
        reg("combat_train_3", "COMBAT", "戦闘訓練Ⅲ", "全武器ダメージ+15%", 3, List.of("combat_train_2"), false, null, 1, "ALL_DAMAGE", 0.15);
        reg("danger_sense", "COMBAT", "危機察知", "攻撃直前の警告", 2, empty, false, null, 1, "ATTACK_WARNING", 1.0);
        reg("danger_sense_2", "COMBAT", "危機察知Ⅱ", "回避時バフ", 5, List.of("danger_sense"), false, null, 1, "DODGE_BUFF", 1.0);
        reg("combat_adapt", "COMBAT", "戦闘適性", "戦闘ストレス-20%", 2, empty, false, null, 1, "COMBAT_STRESS", -0.20);
        reg("combat_adapt_2", "COMBAT", "戦闘適性Ⅱ", "戦闘ストレス-40%", 3, List.of("combat_adapt"), false, null, 1, "COMBAT_STRESS", -0.40);
        reg("melee_1", "COMBAT", "近接Ⅰ", "近接熟練度UP", 1, empty, false, null, 1, "MELEE_XP_RATE", 0.20);
        reg("melee_2", "COMBAT", "近接Ⅱ", "近接スタミナ-15%", 2, List.of("melee_1"), false, null, 1, "MELEE_STAMINA_COST", -0.15);
        reg("counter", "COMBAT", "カウンター", "ガード直後の攻撃+30%", 5, List.of("melee_2"), false, null, 1, "GUARD_COUNTER", 0.30);
        reg("hard_style", "COMBAT", "二択A：剛の型", "攻撃ダメージ+20%", 8, empty, true, "melee_style", 1, "MELEE_DAMAGE", 0.20);
        reg("soft_style", "COMBAT", "二択B：柔の型", "スタミナ消費-30%", 8, empty, true, "melee_style", 1, "MELEE_STAMINA_COST", -0.30);
        reg("ranged_1", "COMBAT", "遠距離Ⅰ", "遠距離熟練度UP", 1, empty, false, null, 1, "RANGED_XP_RATE", 0.20);
        reg("ranged_2", "COMBAT", "遠距離Ⅱ", "射撃スタミナ-15%", 2, List.of("ranged_1"), false, null, 1, "RANGED_STAMINA_COST", -0.15);
        reg("snipe", "COMBAT", "狙撃", "長距離精度+30%", 3, List.of("ranged_2"), false, null, 1, "LONG_RANGE_ACCURACY", 0.30);
        reg("rapid_fire", "COMBAT", "速射", "連射速度+20%", 5, List.of("ranged_2"), false, null, 1, "RANGED_SPEED", 0.20);
        reg("stealth_1", "COMBAT", "隠密", "足音軽減", 1, empty, false, null, 1, "FOOTSTEP_REDUCE", 1.0);
        reg("stealth_2", "COMBAT", "隠密Ⅱ", "索敵範囲-20%", 2, List.of("stealth_1"), false, null, 1, "DETECT_RANGE", -0.20);
        reg("ambush", "COMBAT", "奇襲", "隠密攻撃+50%", 5, List.of("stealth_2"), false, null, 1, "STEALTH_DAMAGE", 0.50);
        reg("assassin", "COMBAT", "二択A：暗殺者", "奇襲ダメージ+50%", 8, empty, true, "stealth_type", 1, "AMBUSH_DAMAGE", 0.50);
        reg("shadow", "COMBAT", "二択B：影武者", "隠密維持時間UP", 8, empty, true, "stealth_type", 1, "STEALTH_DURATION", 0.50);
    }

    public static SkillAbility getAbility(String id) {
        return ALL_ABILITIES.get(id);
    }

    private final Map<UUID, Double> sprintDistance = new HashMap<>();
    private final Map<UUID, Double> swimDistance = new HashMap<>();
    private final Map<UUID, Integer> blocksPlaced = new HashMap<>();

    public SkillManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        sprintDistance.clear();
        swimDistance.clear();
        blocksPlaced.clear();
        HandlerList.unregisterAll(this);
    }

    // ==================== Core Skill Methods ====================

    public int getXpForLevel(int level) {
        return (int) (100 * (level + 1) * 1.5);
    }

    public int getTotalPointsForLevel(int level) {
        if (level <= 5) return level;
        return 5 + (level - 1) / 5;
    }

    public int getSpentPoints(PlayerData data, String skillTree) {
        int spent = 0;
        List<SkillAbility> abilities = ABILITIES_BY_TREE.get(skillTree);
        if (abilities == null) return 0;
        for (SkillAbility ability : abilities) {
            int level = data.getAbilityLevel(ability.getId());
            if (level > 0) {
                spent += ability.getCost() * level;
            }
        }
        return spent;
    }

    public void addXp(Player player, String skillId, double amount) {
        PlayerData data = plugin.getDataManager().getData(player);
        if (data == null) return;

        double multiplier = plugin.getConfigManager().skillsXpGainMultiplier;
        amount *= multiplier;

        int currentLevel = data.getSkillLevel(skillId);
        double currentXp = data.getSkillXp(skillId) + amount;

        int oldLevel = currentLevel;
        while (true) {
            int xpNeeded = getXpForLevel(currentLevel);
            if (currentXp < xpNeeded) break;
            currentXp -= xpNeeded;
            currentLevel++;
        }

        data.setSkillLevel(skillId, currentLevel);
        data.setSkillXp(skillId, currentXp);

        if (currentLevel > oldLevel) {
            int pointsBefore = getTotalPointsForLevel(oldLevel);
            int pointsAfter = getTotalPointsForLevel(currentLevel);
            int gainedPoints = pointsAfter - pointsBefore;
            if (gainedPoints > 0) {
                data.addSkillPoints(gainedPoints);
            }

            String displayName = SKILL_TREE_DISPLAY_NAMES.getOrDefault(skillId, skillId);
            player.sendMessage(ChatColor.GREEN + displayName + " がレベルアップ! " + ChatColor.GRAY + oldLevel + " -> " + ChatColor.GOLD + currentLevel);
            if (gainedPoints > 0) {
                player.sendMessage(ChatColor.YELLOW + "スキルポイント +" + gainedPoints + " 獲得!");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
    }

    public boolean unlockAbility(Player player, String abilityId) {
        SkillAbility ability = ALL_ABILITIES.get(abilityId);
        if (ability == null) return false;
        return unlockAbility(player, ability);
    }

    public boolean unlockAbility(Player player, SkillAbility ability) {
        PlayerData data = plugin.getDataManager().getData(player);
        if (data == null) return false;

        int currentLevel = data.getAbilityLevel(ability.getId());

        if (currentLevel >= ability.getMaxLevel()) {
            player.sendMessage(ChatColor.RED + "このアビリティは最大レベル: " + ability.getName());
            return false;
        }

        int spent = getSpentPoints(data, ability.getSkillTree());
        if (spent + ability.getCost() > plugin.getConfigManager().skillsMaxPointsPerSkill) {
            player.sendMessage(ChatColor.RED + "スキルポイント上限到達: " + ability.getSkillTree() + " (" + plugin.getConfigManager().skillsMaxPointsPerSkill + "pt)");
            return false;
        }

        if (data.getSkillPoints() < ability.getCost()) {
            player.sendMessage(ChatColor.RED + "スキルポイント不足! 必要 " + ability.getCost() + "、所持 " + data.getSkillPoints());
            return false;
        }

        for (String prereqId : ability.getPrerequisites()) {
            if (data.getAbilityLevel(prereqId) <= 0) {
                SkillAbility prereq = ALL_ABILITIES.get(prereqId);
                String name = prereq != null ? prereq.getName() : prereqId;
                player.sendMessage(ChatColor.RED + "必要: " + name);
                return false;
            }
        }

        if (ability.isExclusive()) {
            String group = ability.getExclusiveGroup();
            List<SkillAbility> treeAbilities = ABILITIES_BY_TREE.get(ability.getSkillTree());
            if (treeAbilities != null) {
                for (SkillAbility other : treeAbilities) {
                    if (other.getId().equals(ability.getId())) continue;
                    if (group != null && group.equals(other.getExclusiveGroup()) && data.getAbilityLevel(other.getId()) > 0) {
                        player.sendMessage(ChatColor.RED + "すでに選択済み: " + other.getName() + " (二択スキル)");
                        return false;
                    }
                }
            }
        }

        data.setSkillPoints(data.getSkillPoints() - ability.getCost());
        data.setAbilityLevel(ability.getId(), currentLevel + 1);

        player.sendMessage(ChatColor.GREEN + "解放: " + ChatColor.GOLD + ability.getName() + ChatColor.GREEN + "!");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
        return true;
    }

    public void resetSkill(Player player, String skillId) {
        PlayerData data = plugin.getDataManager().getData(player);
        if (data == null) return;

        int spent = getSpentPoints(data, skillId);
        List<SkillAbility> abilities = ABILITIES_BY_TREE.get(skillId);
        if (abilities != null) {
            for (SkillAbility ability : abilities) {
                data.setAbilityLevel(ability.getId(), 0);
            }
        }

        int penalty = Math.min(spent / 5, 10);
        int newLevel = Math.max(0, data.getSkillLevel(skillId) - penalty);
        data.setSkillLevel(skillId, newLevel);
        data.setSkillXp(skillId, 0);

        String displayName = SKILL_TREE_DISPLAY_NAMES.getOrDefault(skillId, skillId);
        player.sendMessage(ChatColor.RED + "リセット: " + displayName + "。レベルが " + newLevel + " に低下 (ペナルティ: -" + penalty + ")");
        player.sendMessage(ChatColor.GRAY + "使用済みポイントは失われ返還されない。");
    }

    public void openSkillMenu(Player player) {
        SkillMenu.openMainMenu(player, this);
    }

    // ==================== Ability Effects ====================

    public Map<String, Double> getAbilityEffects(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        if (data == null) return Collections.emptyMap();

        Map<String, Double> effects = new HashMap<>();
        for (Map.Entry<String, Integer> entry : data.getUnlockedAbilities().entrySet()) {
            if (entry.getValue() > 0) {
                SkillAbility ability = ALL_ABILITIES.get(entry.getKey());
                if (ability != null && ability.getEffectType() != null && !ability.getEffectType().isEmpty()) {
                    int levels = entry.getValue();
                    double current = effects.getOrDefault(ability.getEffectType(), 1.0);
                    for (int i = 0; i < levels; i++) {
                        current *= (1.0 + ability.getEffectValue());
                    }
                    effects.put(ability.getEffectType(), current);
                }
            }
        }
        return effects;
    }

    public boolean hasAbilityEffect(Player player, String effectType) {
        Map<String, Double> effects = getAbilityEffects(player);
        return effects.containsKey(effectType);
    }

    public double getAbilityEffectValue(Player player, String effectType) {
        Map<String, Double> effects = getAbilityEffects(player);
        return effects.getOrDefault(effectType, 1.0);
    }

    // ==================== Event Handlers ====================

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().distanceSquared(event.getTo()) < 0.001) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        double dist = event.getFrom().distance(event.getTo());

        if (player.isSprinting()) {
            double total = sprintDistance.getOrDefault(uuid, 0.0) + dist;
            if (total >= 100.0) {
                int awards = (int) (total / 100.0);
                addXp(player, "ENDURANCE", awards * 5.0);
                sprintDistance.put(uuid, total - awards * 100.0);
            } else {
                sprintDistance.put(uuid, total);
            }
        } else {
            sprintDistance.put(uuid, 0.0);
        }

        if (player.isSwimming()) {
            double total = swimDistance.getOrDefault(uuid, 0.0) + dist;
            if (total >= 50.0) {
                int awards = (int) (total / 50.0);
                addXp(player, "ENDURANCE", awards * 3.0);
                swimDistance.put(uuid, total - awards * 50.0);
            } else {
                swimDistance.put(uuid, total);
            }
        } else {
            swimDistance.put(uuid, 0.0);
        }
    }

    @EventHandler
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        double damage = event.getDamage();

        double strengthXp = damage * 2.0;
        addXp(player, "STRENGTH", strengthXp);

        double combatXp = damage * 1.5;
        Map<String, Double> effects = getAbilityEffects(player);
        if (effects.containsKey("MELEE_XP_RATE")) {
            combatXp *= effects.get("MELEE_XP_RATE");
        }
        addXp(player, "COMBAT", combatXp);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material tool = player.getInventory().getItemInMainHand().getType();
        String toolName = tool.name();

        if (toolName.contains("PICKAXE") || toolName.contains("AXE")) {
            addXp(player, "STRENGTH", 2.0);
        }

        Material blockType = event.getBlock().getType();
        if (isCrop(blockType)) {
            addXp(player, "FARMING", 5.0);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        LivingEntity entity = event.getEntity();
        double xpAmount;
        if (entity instanceof Wither || entity instanceof EnderDragon) {
            xpAmount = 50.0;
        } else if (entity instanceof Monster || entity instanceof ElderGuardian) {
            xpAmount = 15.0;
        } else {
            xpAmount = 10.0;
        }

        // 近接/遠距離熟練度: 使用武器でXP率上昇
        Material weapon = killer.getInventory().getItemInMainHand().getType();
        String weaponName = weapon.name();
        if (weaponName.contains("SWORD") || weaponName.contains("AXE") || weapon == Material.TRIDENT) {
            xpAmount *= getAbilityEffectValue(killer, "MELEE_XP_RATE");
        } else if (weaponName.contains("BOW") || weaponName.contains("CROSSBOW")) {
            xpAmount *= getAbilityEffectValue(killer, "RANGED_XP_RATE");
        }

        addXp(killer, "COMBAT", xpAmount);
    }

    // 投擲: 投げたエンティティの飛距離上昇
    @EventHandler
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        double throwMult = getAbilityEffectValue(player, "THROW_DISTANCE");
        if (throwMult > 1.0) {
            event.getEntity().setVelocity(event.getEntity().getVelocity().multiply(throwMult));
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();
        if (type.isEdible()) {
            addXp(player, "METABOLISM", 3.0);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        int count = blocksPlaced.getOrDefault(uuid, 0) + 1;
        blocksPlaced.put(uuid, count);

        if (count >= 50) {
            addXp(player, "BUILDING", 5.0 * (count / 50.0));
            blocksPlaced.put(uuid, count % 50);
        }
    }

    @EventHandler
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        double damage = event.getFinalDamage();
        addXp(player, "RESISTANCE", damage * 1.5);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof FurnaceInventory)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (isCookedFood(event.getCurrentItem().getType())) {
            addXp(player, "COOKING", event.getCurrentItem().getAmount() * 2.0);
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player) {
            Player player = (Player) event.getBreeder();
            addXp(player, "FARMING", 8.0);
        }
    }

    @EventHandler
    public void onInteractPlant(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null) return;
        Material type = event.getItem().getType();
        if (isSeed(type)) {
            addXp(event.getPlayer(), "FARMING", 2.0);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(ChatColor.DARK_GRAY.toString())) return;
        SkillMenu.handleClick(event, this);
    }

    // 石工/木工: 石材・木材クラフト時の素材消費を軽減 (素材を1個返却)
    @EventHandler(ignoreCancelled = true)
    public void onCraftRefund(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        if (result == null) return;

        double saveChance = 0.0;
        if (isStoneCraft(result.getType())) {
            saveChance = 1.0 - getAbilityEffectValue(player, "STONE_MAT_COST");
        } else if (isWoodCraft(result.getType())) {
            saveChance = 1.0 - getAbilityEffectValue(player, "WOOD_MAT_COST");
        }
        if (saveChance <= 0.0) return;

        // クラフト数だけ抽選
        int crafted = event.isShiftClick()
            ? Math.min(event.getInventory().getMatrix().length, 64) : 1;
        for (int i = 0; i < crafted; i++) {
            if (ThreadLocalRandom.current().nextDouble() < saveChance) {
                ItemStack refund = pickIngredient(event.getInventory().getMatrix());
                if (refund != null) {
                    player.getInventory().addItem(refund.clone()).values()
                        .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
                }
                break;
            }
        }
    }

    private ItemStack pickIngredient(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item != null && item.getType() != Material.AIR) {
                ItemStack one = item.clone();
                one.setAmount(1);
                return one;
            }
        }
        return null;
    }

    private boolean isStoneCraft(Material mat) {
        String name = mat.name();
        return name.contains("STONE") || name.contains("BRICK") || name.contains("ANDESITE")
            || name.contains("DIORITE") || name.contains("GRANITE") || name.contains("SANDSTONE")
            || name.contains("COBBLE") || name.contains("DEEPSLATE") || name.contains("PRISMARINE")
            || name.contains("BLACKSTONE") || name.contains("BASALT") || name.contains("OBSIDIAN");
    }

    private boolean isWoodCraft(Material mat) {
        String name = mat.name();
        return name.contains("WOOD") || name.contains("PLANK") || name.contains("FENCE")
            || name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("STAIR")
            || name.contains("SLAB") || name.contains("GATE") || name.contains("SIGN")
            || name.contains("BOAT") || name.contains("LADDER") || name.contains("BOWL")
            || name.contains("ITEM_FRAME");
    }

    // ==================== Helpers ====================

    private boolean isCrop(Material material) {
        return material == Material.WHEAT || material == Material.CARROTS
            || material == Material.POTATOES || material == Material.BEETROOTS
            || material == Material.NETHER_WART || material == Material.COCOA
            || material == Material.SWEET_BERRY_BUSH || material == Material.MELON
            || material == Material.PUMPKIN || material == Material.BAMBOO
            || material == Material.SUGAR_CANE || material == Material.CACTUS
            || material == Material.KELP || material == Material.SEA_PICKLE;
    }

    private boolean isSeed(Material material) {
        return material == Material.WHEAT_SEEDS || material == Material.CARROT
            || material == Material.POTATO || material == Material.BEETROOT_SEEDS
            || material == Material.PUMPKIN_SEEDS || material == Material.MELON_SEEDS
            || material == Material.NETHER_WART || material == Material.COCOA_BEANS
            || material == Material.SWEET_BERRIES || material == Material.KELP
            || material == Material.BAMBOO || material == Material.SUGAR_CANE
            || material == Material.CACTUS || material == Material.SEA_PICKLE
            || material == Material.OAK_SAPLING || material == Material.SPRUCE_SAPLING
            || material == Material.BIRCH_SAPLING || material == Material.JUNGLE_SAPLING
            || material == Material.ACACIA_SAPLING || material == Material.DARK_OAK_SAPLING
            || material == Material.CHORUS_FLOWER || material == Material.CHORUS_FRUIT
            || material == Material.GLOW_BERRIES || material.name().contains("SEEDS");
    }

    private boolean isCookedFood(Material mat) {
        return mat == Material.COOKED_BEEF || mat == Material.COOKED_CHICKEN
            || mat == Material.COOKED_COD || mat == Material.COOKED_MUTTON
            || mat == Material.COOKED_PORKCHOP || mat == Material.COOKED_RABBIT
            || mat == Material.COOKED_SALMON || mat == Material.BAKED_POTATO
            || mat == Material.DRIED_KELP || mat == Material.BREAD
            || mat == Material.PUMPKIN_PIE || mat == Material.MUSHROOM_STEW
            || mat == Material.BEETROOT_SOUP || mat == Material.RABBIT_STEW;
    }
}
