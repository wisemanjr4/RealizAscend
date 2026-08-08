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
        SKILL_TREE_DISPLAY_NAMES.put("ENDURANCE", "Endurance (\u6301\u4E45\u529B)");
        SKILL_TREE_DISPLAY_NAMES.put("STRENGTH", "Strength (\u7B4B\u529B)");
        SKILL_TREE_DISPLAY_NAMES.put("RESISTANCE", "Resistance (\u8010\u6027)");
        SKILL_TREE_DISPLAY_NAMES.put("METABOLISM", "Metabolism (\u4EE3\u8B1D)");
        SKILL_TREE_DISPLAY_NAMES.put("COOKING", "Cooking (\u6599\u7406)");
        SKILL_TREE_DISPLAY_NAMES.put("MEDICAL", "Medical (\u533B\u7642)");
        SKILL_TREE_DISPLAY_NAMES.put("BUILDING", "Building (\u5EFA\u7BC9)");
        SKILL_TREE_DISPLAY_NAMES.put("FARMING", "Farming (\u8FB2\u696D)");
        SKILL_TREE_DISPLAY_NAMES.put("COMBAT", "Combat (\u6226\u95D8)");

        List<String> empty = Collections.emptyList();

        // ===== ENDURANCE =====
        reg("endurance_1", "ENDURANCE", "Endurance I", "Max Stamina +10%", 1, empty, false, null, 1, "MAX_STAMINA", 0.10);
        reg("endurance_2", "ENDURANCE", "Endurance II", "Max Stamina +20%", 2, List.of("endurance_1"), false, null, 1, "MAX_STAMINA", 0.20);
        reg("endurance_3", "ENDURANCE", "Endurance III", "Max Stamina +30%", 3, List.of("endurance_2"), false, null, 1, "MAX_STAMINA", 0.30);
        reg("efficient_run", "ENDURANCE", "Efficient Running I", "Sprint Stamina Cost -15%", 2, empty, false, null, 1, "SPRINT_COST", -0.15);
        reg("efficient_run_2", "ENDURANCE", "Efficient Running II", "Sprint Stamina Cost -30%", 3, List.of("efficient_run"), false, null, 1, "SPRINT_COST", -0.30);
        reg("iron_lung", "ENDURANCE", "Iron Lung I", "Water Stamina Cost -30%", 2, empty, false, null, 1, "WATER_STAMINA_COST", -0.30);
        reg("iron_lung_2", "ENDURANCE", "Iron Lung II", "Water Stamina Cost -60%", 3, List.of("iron_lung"), false, null, 1, "WATER_STAMINA_COST", -0.60);
        reg("second_wind", "ENDURANCE", "Second Wind", "Burst of stamina when depleted", 5, empty, false, null, 1, "STAMINA_BURST", 1.0);
        reg("high_altitude", "ENDURANCE", "High Altitude Adaptation", "Reduced altitude stamina penalty", 3, empty, false, null, 1, "ALTITUDE_STAMINA", 1.0);
        reg("indomitable", "ENDURANCE", "Indomitable", "Can act at zero stamina", 8, empty, false, null, 1, "ZERO_STAMINA_ACTION", 1.0);
        reg("speed_sprint", "ENDURANCE", "Sprinter Type (A)", "Instant stamina regen +30%", 5, empty, true, "endurance_speed_type", 1, "STAMINA_INSTANT_REGEN", 0.30);
        reg("speed_marathon", "ENDURANCE", "Marathon Type (B)", "Max Stamina +30%", 5, empty, true, "endurance_speed_type", 1, "MAX_STAMINA", 0.30);
        reg("breath_control", "ENDURANCE", "Breath Control", "Fatigue stamina regen +20%", 2, empty, false, null, 1, "FATIGUE_STAMINA_REGEN", 0.20);
        reg("climber", "ENDURANCE", "Climber I", "Climb Cost -20%", 2, empty, false, null, 1, "CLIMB_COST", -0.20);
        reg("climber_2", "ENDURANCE", "Climber II", "Climb Cost -30%", 3, List.of("climber"), false, null, 1, "CLIMB_COST", -0.30);
        reg("energy_save", "ENDURANCE", "Energy Conservation", "Walk Cost -20%", 1, empty, false, null, 1, "WALK_COST", -0.20);
        reg("steel_legs", "ENDURANCE", "Steel Legs", "No fall stamina cost", 2, empty, false, null, 1, "FALL_STAMINA_COST", -1.00);
        reg("combat_breathing", "ENDURANCE", "Combat Breathing I", "Combat stamina regen active", 5, empty, false, null, 1, "COMBAT_STAMINA_REGEN", 1.0);
        reg("combat_breathing_2", "ENDURANCE", "Combat Breathing II", "Combat stamina regen faster", 8, List.of("combat_breathing"), false, null, 1, "COMBAT_STAMINA_REGEN", 1.3);
        reg("super_recovery", "ENDURANCE", "Super Recovery", "Sleep stamina boost +5%", 10, empty, false, null, 1, "SLEEP_STAMINA_BOOST", 0.05);
        reg("no_mind", "ENDURANCE", "No Mind", "Zero stamina penalty -50%", 8, empty, false, null, 1, "ZERO_STAMINA_PENALTY_REDUCE", 0.5);
        reg("heart_lung", "ENDURANCE", "Heart-Lung I", "Temp warning stamina +50%", 3, empty, false, null, 1, "TEMP_WARNING_STAMINA", 0.5);
        reg("heart_lung_2", "ENDURANCE", "Heart-Lung II", "Temp warning stamina nullified", 5, List.of("heart_lung"), false, null, 1, "TEMP_WARNING_STAMINA", 1.0);

        // ===== STRENGTH =====
        reg("strength_1", "STRENGTH", "Strength I", "Max Weight +10%", 1, empty, false, null, 1, "MAX_WEIGHT", 0.10);
        reg("strength_2", "STRENGTH", "Strength II", "Max Weight +20%", 2, List.of("strength_1"), false, null, 1, "MAX_WEIGHT", 0.20);
        reg("strength_3", "STRENGTH", "Strength III", "Max Weight +30%", 3, List.of("strength_2"), false, null, 1, "MAX_WEIGHT", 0.30);
        reg("strong_arm", "STRENGTH", "Strong Arm I", "Melee Damage +10%", 2, empty, false, null, 1, "MELEE_DAMAGE", 0.10);
        reg("strong_arm_2", "STRENGTH", "Strong Arm II", "Melee Damage +20%", 3, List.of("strong_arm"), false, null, 1, "MELEE_DAMAGE", 0.20);
        reg("strong_arm_3", "STRENGTH", "Strong Arm III", "Melee Damage +30%", 5, List.of("strong_arm_2"), false, null, 1, "MELEE_DAMAGE", 0.30);
        reg("throwing", "STRENGTH", "Throwing I", "Throw Distance +20%", 2, empty, false, null, 1, "THROW_DISTANCE", 0.20);
        reg("throwing_2", "STRENGTH", "Throwing II", "Piercing throws", 5, List.of("throwing"), false, null, 1, "THROW_PIERCE", 1.0);
        reg("heavy_adapt", "STRENGTH", "Heavy Load Adaptation I", "Overweight Penalty -30%", 3, empty, false, null, 1, "OVERWEIGHT_PENALTY", -0.30);
        reg("heavy_adapt_2", "STRENGTH", "Heavy Load Adaptation II", "Overweight jump threshold +30%", 5, List.of("heavy_adapt"), false, null, 1, "OVERWEIGHT_JUMP_THRESHOLD", 0.30);
        reg("iron_grip", "STRENGTH", "Iron Grip I", "Weapon Durability Loss -15%", 2, empty, false, null, 1, "WEAPON_DURABILITY", -0.15);
        reg("iron_grip_2", "STRENGTH", "Iron Grip II", "Weapon Durability Loss -30%", 3, List.of("iron_grip"), false, null, 1, "WEAPON_DURABILITY", -0.30);
        reg("destruction", "STRENGTH", "Destruction Impulse I", "Tool Durability Loss -10%", 1, empty, false, null, 1, "TOOL_DURABILITY", -0.10);
        reg("destruction_2", "STRENGTH", "Destruction Impulse II", "Mine Speed +15%", 2, List.of("destruction"), false, null, 1, "MINE_SPEED", 0.15);
        reg("jump_boost", "STRENGTH", "Jump Boost", "Jump height increased", 3, empty, false, null, 1, "JUMP_HEIGHT", 1.0);
        reg("sturdy", "STRENGTH", "Sturdy I", "Knockback Resist +30%", 2, empty, false, null, 1, "KNOCKBACK_RESIST", 0.30);
        reg("sturdy_2", "STRENGTH", "Sturdy II", "Full knockback immunity", 5, List.of("sturdy"), false, null, 1, "KNOCKBACK_RESIST", 1.0);
        reg("manual_labor", "STRENGTH", "Manual Labor", "Heavy work calorie cost -10%", 2, empty, false, null, 1, "HEAVY_WORK_CALORIE", -0.10);
        reg("super_strength", "STRENGTH", "Super Strength", "Can carry heavy blocks", 3, empty, false, null, 1, "HEAVY_BLOCK_CARRY", 1.0);
        reg("power_type", "STRENGTH", "Power Type (A)", "Melee Damage +30%", 8, empty, true, "strength_style", 1, "MELEE_DAMAGE", 0.30);
        reg("tech_type", "STRENGTH", "Technique Type (B)", "Melee Stamina Cost -20%", 8, empty, true, "strength_style", 1, "MELEE_STAMINA_COST", -0.20);
        reg("muscles_dont_lie", "STRENGTH", "Muscles Don't Lie", "All strength effects +5%", 10, empty, false, null, 1, "STRENGTH_ALL_BOOST", 0.05);

        // ===== RESISTANCE =====
        reg("resistance_1", "RESISTANCE", "Resistance I", "Disease Chance -10%", 1, empty, false, null, 1, "DISEASE_CHANCE", -0.10);
        reg("resistance_2", "RESISTANCE", "Resistance II", "Disease Chance -20%", 2, List.of("resistance_1"), false, null, 1, "DISEASE_CHANCE", -0.20);
        reg("resistance_3", "RESISTANCE", "Resistance III", "Disease Chance -30%", 3, List.of("resistance_2"), false, null, 1, "DISEASE_CHANCE", -0.30);
        reg("infection_resist", "RESISTANCE", "Infection Resistance I", "Infection Chance -20%", 2, empty, false, null, 1, "INFECTION_CHANCE", -0.20);
        reg("infection_resist_2", "RESISTANCE", "Infection Resistance II", "Infection Chance -40%", 3, List.of("infection_resist"), false, null, 1, "INFECTION_CHANCE", -0.40);
        reg("steel_stomach", "RESISTANCE", "Steel Stomach I", "Rotten food debuff -50%", 3, empty, false, null, 1, "ROTTEN_DEBUFF", 0.50);
        reg("steel_stomach_2", "RESISTANCE", "Steel Stomach II", "Rotten food immunity", 5, List.of("steel_stomach"), false, null, 1, "ROTTEN_DEBUFF", 1.0);
        reg("temp_resist", "RESISTANCE", "Temperature Resistance I", "Temp warning delay +20%", 2, empty, false, null, 1, "TEMP_WARNING_DELAY", 0.20);
        reg("temp_resist_2", "RESISTANCE", "Temperature Resistance II", "Temp critical delay +20%", 3, List.of("temp_resist"), false, null, 1, "TEMP_CRITICAL_DELAY", 0.20);
        reg("poison_resist", "RESISTANCE", "Poison Resistance I", "Poison Duration -30%", 2, empty, false, null, 1, "POISON_DURATION", -0.30);
        reg("poison_resist_2", "RESISTANCE", "Poison Resistance II", "Poison Duration -50%", 5, List.of("poison_resist"), false, null, 1, "POISON_DURATION", -0.50);
        reg("toughness", "RESISTANCE", "Toughness I", "Physical Damage -5%", 2, empty, false, null, 1, "PHYSICAL_DAMAGE", -0.05);
        reg("toughness_2", "RESISTANCE", "Toughness II", "Physical Damage -10%", 3, List.of("toughness"), false, null, 1, "PHYSICAL_DAMAGE", -0.10);
        reg("insomnia_resist", "RESISTANCE", "Insomnia Resistance", "Sleep debt accumulates slower", 2, empty, false, null, 1, "SLEEP_DEBT_DELAY", 1.0);
        reg("pain_dull", "RESISTANCE", "Pain Dulling I", "Injury Debuff -20%", 3, empty, false, null, 1, "INJURY_DEBUFF", -0.20);
        reg("pain_dull_2", "RESISTANCE", "Pain Dulling II", "Injury Debuff -40%", 5, List.of("pain_dull"), false, null, 1, "INJURY_DEBUFF", -0.40);
        reg("stress_resist", "RESISTANCE", "Stress Resistance I", "Stress Rise -15%", 2, empty, false, null, 1, "STRESS_RISE", -0.15);
        reg("stress_resist_2", "RESISTANCE", "Stress Resistance II", "Stress Rise -30%", 3, List.of("stress_resist"), false, null, 1, "STRESS_RISE", -0.30);
        reg("immune_special", "RESISTANCE", "Immune Specialized (A)", "Debuff Duration -30%", 8, empty, true, "resistance_type", 1, "DEBUFF_DURATION", -0.30);
        reg("body_special", "RESISTANCE", "Body Specialized (B)", "Physical Damage -15%", 8, empty, true, "resistance_type", 1, "PHYSICAL_DAMAGE", -0.15);
        reg("immortal", "RESISTANCE", "Immortal", "Prevent death once", 10, empty, false, null, 1, "DEATH_PREVENT", 1.0);

        // ===== METABOLISM =====
        reg("metabolism_1", "METABOLISM", "Metabolism I", "Calorie Efficiency +10%", 1, empty, false, null, 1, "CALORIE_EFFICIENCY", 0.10);
        reg("metabolism_2", "METABOLISM", "Metabolism II", "Calorie Efficiency +20%", 2, List.of("metabolism_1"), false, null, 1, "CALORIE_EFFICIENCY", 0.20);
        reg("metabolism_3", "METABOLISM", "Metabolism III", "Calorie Efficiency +30%", 3, List.of("metabolism_2"), false, null, 1, "CALORIE_EFFICIENCY", 0.30);
        reg("water_save", "METABOLISM", "Water Conservation I", "Hydration Consumption -10%", 1, empty, false, null, 1, "HYDRATION_CONSUME", -0.10);
        reg("water_save_2", "METABOLISM", "Water Conservation II", "Hydration Consumption -25%", 2, List.of("water_save"), false, null, 1, "HYDRATION_CONSUME", -0.25);
        reg("water_save_3", "METABOLISM", "Water Conservation III", "Hydration Consumption -40%", 3, List.of("water_save_2"), false, null, 1, "HYDRATION_CONSUME", -0.40);
        reg("nutrient_absorb", "METABOLISM", "Nutrient Absorption I", "Nutrient Gain +15%", 2, empty, false, null, 1, "NUTRIENT_GAIN", 0.15);
        reg("nutrient_absorb_2", "METABOLISM", "Nutrient Absorption II", "Nutrient Gain +30%", 3, List.of("nutrient_absorb"), false, null, 1, "NUTRIENT_GAIN", 0.30);
        reg("fat_burn", "METABOLISM", "Fat Burn I", "Cold Calorie Cost -20%", 2, empty, false, null, 1, "COLD_CALORIE_COST", -0.20);
        reg("fat_burn_2", "METABOLISM", "Fat Burn II", "Cold Calorie Cost -40%", 3, List.of("fat_burn"), false, null, 1, "COLD_CALORIE_COST", -0.40);
        reg("sweat_control", "METABOLISM", "Sweat Control I", "Hot Hydration Cost -20%", 2, empty, false, null, 1, "HOT_HYDRATION_COST", -0.20);
        reg("sweat_control_2", "METABOLISM", "Sweat Control II", "Hot Hydration Cost -40%", 3, List.of("sweat_control"), false, null, 1, "HOT_HYDRATION_COST", -0.40);
        reg("small_eater", "METABOLISM", "Small Eater I", "Stomach Decay -15%", 2, empty, false, null, 1, "STOMACH_DECAY", -0.15);
        reg("small_eater_2", "METABOLISM", "Small Eater II", "Stomach Decay -30%", 3, List.of("small_eater"), false, null, 1, "STOMACH_DECAY", -0.30);
        reg("salt_balance", "METABOLISM", "Salt Balance", "Salt Debuff Delay +30%", 2, empty, false, null, 1, "SALT_DEBUFF_DELAY", 0.30);
        reg("recovery_boost", "METABOLISM", "Recovery Boost I", "Food HP Regen +20%", 2, empty, false, null, 1, "FOOD_HP_REGEN", 0.20);
        reg("recovery_boost_2", "METABOLISM", "Recovery Boost II", "Food HP Regen +40%", 3, List.of("recovery_boost"), false, null, 1, "FOOD_HP_REGEN", 0.40);
        reg("blood_make", "METABOLISM", "Blood Production I", "Blood Regen +15%", 3, empty, false, null, 1, "BLOOD_REGEN", 0.15);
        reg("blood_make_2", "METABOLISM", "Blood Production II", "Blood Regen +30%", 5, List.of("blood_make"), false, null, 1, "BLOOD_REGEN", 0.30);
        reg("energy_save_type", "METABOLISM", "Energy Save Type (A)", "Calorie & Hydration Save +20%", 8, empty, true, "metabolism_type", 1, "CALORIE_HYDRATION_SAVE", 0.20);
        reg("burn_type", "METABOLISM", "Burn Type (B)", "Max Stamina +25%", 8, empty, true, "metabolism_type", 1, "MAX_STAMINA", 0.25);
        reg("super_metabolism", "METABOLISM", "Super Metabolism", "All Efficiency +10%", 10, empty, false, null, 1, "ALL_EFFICIENCY", 0.10);

        // ===== COOKING =====
        reg("cook_grill_1", "COOKING", "Grilling I", "Unlock basic grilling recipes", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_grill_2", "COOKING", "Grilling II", "Unlock advanced grilling recipes", 2, List.of("cook_grill_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_grill_3", "COOKING", "Grilling III", "Unlock master grilling recipes", 5, List.of("cook_grill_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_stew_1", "COOKING", "Stewing I", "Unlock basic stewing recipes", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_stew_2", "COOKING", "Stewing II", "Unlock advanced stewing recipes", 2, List.of("cook_stew_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_stew_3", "COOKING", "Stewing III", "Unlock master stewing recipes", 5, List.of("cook_stew_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_smoke_1", "COOKING", "Smoking I", "Unlock basic smoking recipes", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_smoke_2", "COOKING", "Smoking II", "Unlock advanced smoking recipes", 2, List.of("cook_smoke_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_smoke_3", "COOKING", "Smoking III", "Unlock master smoking recipes", 5, List.of("cook_smoke_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_preserve_1", "COOKING", "Preserving I", "Unlock basic preserving recipes", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_preserve_2", "COOKING", "Preserving II", "Unlock advanced preserving recipes", 2, List.of("cook_preserve_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_preserve_3", "COOKING", "Preserving III", "Unlock master preserving recipes", 5, List.of("cook_preserve_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_instant_1", "COOKING", "Instant Cooking I", "Unlock basic instant cooking", 1, empty, false, null, 1, "COOK_UNLOCK", 1.0);
        reg("cook_instant_2", "COOKING", "Instant Cooking II", "Unlock advanced instant cooking", 2, List.of("cook_instant_1"), false, null, 1, "COOK_UNLOCK", 2.0);
        reg("cook_instant_3", "COOKING", "Instant Cooking III", "Unlock master instant cooking", 5, List.of("cook_instant_2"), false, null, 1, "COOK_UNLOCK", 3.0);
        reg("cook_prep", "COOKING", "Preparation I", "Cooking Speed +20%", 1, empty, false, null, 1, "COOK_SPEED", 0.20);
        reg("cook_prep_2", "COOKING", "Preparation II", "Cooking Waste -20%", 2, List.of("cook_prep"), false, null, 1, "COOK_WASTE", -0.20);
        reg("cook_eye", "COOKING", "Ingredient Eye I", "See ingredient quality", 2, empty, false, null, 1, "COOK_QUALITY_SEE", 1.0);
        reg("cook_eye_2", "COOKING", "Ingredient Eye II", "Can use slightly rotten ingredients", 3, List.of("cook_eye"), false, null, 1, "COOK_ROTTEN_USE", 1.0);
        reg("cook_seasoning", "COOKING", "Seasoning", "Precise salt control", 2, empty, false, null, 1, "COOK_SALT_PRECISION", 1.0);
        reg("cook_specialist", "COOKING", "Specialist Chef (A)", "Specialty cooking bonus x2", 8, empty, true, "cook_type", 1, "COOK_SPECIALTY", 2.0);
        reg("cook_all_rounder", "COOKING", "All-Rounder Chef (B)", "All cooking categories bonus", 8, empty, true, "cook_type", 1, "COOK_ALL", 1.0);
        reg("cook_supreme", "COOKING", "Supreme Dish", "Perfect dish bonus +50%", 10, empty, false, null, 1, "COOK_PERFECT_BONUS", 0.50);

        // ===== MEDICAL =====
        reg("med_first_aid_1", "MEDICAL", "First Aid I", "Bandage Speed +20%", 1, empty, false, null, 1, "BANDAGE_SPEED", 0.20);
        reg("med_first_aid_2", "MEDICAL", "First Aid II", "Can treat fractures", 2, List.of("med_first_aid_1"), false, null, 1, "FRACTURE_TREAT", 1.0);
        reg("med_sterilize", "MEDICAL", "Sterilization", "Infection Treatment Chance +30%", 2, empty, false, null, 1, "INFECTION_TREAT_CHANCE", 0.30);
        reg("med_emergency", "MEDICAL", "Emergency Response", "Can treat while moving", 3, empty, false, null, 1, "TREAT_MOVE", 1.0);
        reg("med_hemostasis", "MEDICAL", "Hemostasis I", "Bleed Speed -30%", 2, empty, false, null, 1, "BLEED_SPEED", -0.30);
        reg("med_hemostasis_2", "MEDICAL", "Hemostasis II", "Can temporarily stop bleeding", 5, List.of("med_hemostasis"), false, null, 1, "BLEED_TEMP_STOP", 1.0);
        reg("med_surgery_1", "MEDICAL", "Surgery I", "Can treat deep wounds", 3, empty, false, null, 1, "DEEP_WOUND_TREAT", 1.0);
        reg("med_surgery_2", "MEDICAL", "Surgery II", "Suture Speed +30%", 5, List.of("med_surgery_1"), false, null, 1, "SUTURE_SPEED", 0.30);
        reg("med_pharma_1", "MEDICAL", "Pharmacology I", "Can craft basic medicines", 3, empty, false, null, 1, "BASIC_MEDS", 1.0);
        reg("med_pharma_2", "MEDICAL", "Pharmacology II", "Can craft medium medicines", 5, List.of("med_pharma_1"), false, null, 1, "MED_MEDS", 1.0);
        reg("med_pharma_3", "MEDICAL", "Pharmacology III", "Can craft advanced medicines", 8, List.of("med_pharma_2"), false, null, 1, "ADVANCED_MEDS", 1.0);
        reg("med_fracture_1", "MEDICAL", "Fracture Treatment I", "Fracture Fix Quality +20%", 2, empty, false, null, 1, "FRACTURE_FIX_QUALITY", 0.20);
        reg("med_fracture_2", "MEDICAL", "Fracture Treatment II", "Can craft casts", 5, List.of("med_fracture_1"), false, null, 1, "CAST_CRAFT", 1.0);
        reg("med_infection_1", "MEDICAL", "Infection Control I", "Infection Spread -30%", 2, empty, false, null, 1, "INFECTION_SPEED", -0.30);
        reg("med_infection_2", "MEDICAL", "Infection Control II", "Natural infection cure +30%", 5, List.of("med_infection_1"), false, null, 1, "INFECTION_NATURAL_CURE", 0.30);
        reg("med_triage", "MEDICAL", "Triage", "See multiple injury details", 3, empty, false, null, 1, "MULTI_INJURY_INFO", 1.0);
        reg("med_field_1", "MEDICAL", "Field Medicine I", "Combat Treatment Effect +20%", 3, empty, false, null, 1, "COMBAT_TREAT_EFFECT", 0.20);
        reg("med_field_2", "MEDICAL", "Field Medicine II", "Combat Treatment Speed +30%", 5, List.of("med_field_1"), false, null, 1, "COMBAT_TREAT_SPEED", 0.30);
        reg("med_surgery_spec", "MEDICAL", "Surgery Specialized (A)", "Surgery effectiveness +30%", 8, empty, true, "med_type", 1, "SURGERY_BOOST", 0.30);
        reg("med_pharma_spec", "MEDICAL", "Pharmacology Specialized (B)", "Medicine effectiveness +30%", 8, empty, true, "med_type", 1, "MEDS_BOOST", 0.30);
        reg("med_god_hand", "MEDICAL", "God Hand", "All treatment effectiveness +30%", 10, empty, false, null, 1, "ALL_TREAT_BOOST", 0.30);

        // ===== BUILDING =====
        reg("build_1", "BUILDING", "Building I", "Unlock basic building recipes", 1, empty, false, null, 1, "BUILD_UNLOCK", 1.0);
        reg("build_2", "BUILDING", "Building II", "Unlock intermediate building", 2, List.of("build_1"), false, null, 1, "BUILD_UNLOCK", 2.0);
        reg("build_3", "BUILDING", "Building III", "Unlock advanced building", 3, List.of("build_2"), false, null, 1, "BUILD_UNLOCK", 3.0);
        reg("structure_1", "BUILDING", "Structural Understanding I", "Unlock basic structures", 1, List.of("build_1"), false, null, 1, "STRUCTURE_UNLOCK", 1.0);
        reg("structure_2", "BUILDING", "Structural Understanding II", "Unlock advanced structures", 3, List.of("structure_1"), false, null, 1, "STRUCTURE_UNLOCK", 2.0);
        reg("structure_3", "BUILDING", "Structural Understanding III", "Block gravity immunity", 10, List.of("structure_2"), false, null, 1, "BLOCK_GRAVITY_IMMUNE", 1.0);
        reg("scaffold_1", "BUILDING", "Scaffolding I", "Fall Damage -30%", 2, empty, false, null, 1, "FALL_DAMAGE", -0.30);
        reg("scaffold_2", "BUILDING", "Scaffolding II", "Fall Damage -50%", 3, List.of("scaffold_1"), false, null, 1, "FALL_DAMAGE", -0.50);
        reg("masonry_1", "BUILDING", "Masonry I", "Stone Material Cost -15%", 1, empty, false, null, 1, "STONE_MAT_COST", -0.15);
        reg("masonry_2", "BUILDING", "Masonry II", "Stone Quality +10%", 2, List.of("masonry_1"), false, null, 1, "STONE_QUALITY", 0.10);
        reg("woodwork_1", "BUILDING", "Woodworking I", "Wood Material Cost -15%", 1, empty, false, null, 1, "WOOD_MAT_COST", -0.15);
        reg("woodwork_2", "BUILDING", "Woodworking II", "Wood Quality +10%", 2, List.of("woodwork_1"), false, null, 1, "WOOD_QUALITY", 0.10);
        reg("metalwork_1", "BUILDING", "Metalworking I", "Unlock metal building", 3, empty, false, null, 1, "METAL_BUILD_UNLOCK", 1.0);
        reg("metalwork_2", "BUILDING", "Metalworking II", "Metal Durability +20%", 5, List.of("metalwork_1"), false, null, 1, "METAL_DURABILITY", 0.20);
        reg("insulation_1", "BUILDING", "Insulation Design I", "Base Temp Fluctuation -30%", 3, empty, false, null, 1, "BASE_TEMP_FLUCTUATION", -0.30);
        reg("insulation_2", "BUILDING", "Insulation Design II", "Base Temperature Stability +50%", 5, List.of("insulation_1"), false, null, 1, "BASE_TEMP_STABLE", 0.50);
        reg("trap_1", "BUILDING", "Trapper I", "Unlock trap building", 3, empty, false, null, 1, "TRAP_UNLOCK", 1.0);
        reg("trap_2", "BUILDING", "Trapper II", "Trap Effect +30%", 5, List.of("trap_1"), false, null, 1, "TRAP_EFFECT", 0.30);
        reg("fast_build", "BUILDING", "Fast Builder (A)", "Build Stamina Cost -40%", 8, empty, true, "build_type", 1, "BUILD_STAMINA_COST", -0.40);
        reg("artisan", "BUILDING", "Artisan (B)", "Build Quality Cap increased", 8, empty, true, "build_type", 1, "BUILD_QUALITY_CAP", 1.0);
        reg("master_builder", "BUILDING", "Master Builder", "All building effects +20%", 10, empty, false, null, 1, "BUILD_ALL_BOOST", 0.20);

        // ===== FARMING =====
        reg("farm_1", "FARMING", "Farming I", "Unlock basic farming techniques", 1, empty, false, null, 1, "FARM_UNLOCK", 1.0);
        reg("farm_2", "FARMING", "Farming II", "Unlock intermediate farming", 2, List.of("farm_1"), false, null, 1, "FARM_UNLOCK", 2.0);
        reg("farm_3", "FARMING", "Farming III", "Unlock advanced farming", 3, List.of("farm_2"), false, null, 1, "FARM_UNLOCK", 3.0);
        reg("soil_knowledge", "FARMING", "Soil Knowledge I", "See soil quality info", 2, empty, false, null, 1, "SOIL_INFO", 1.0);
        reg("soil_knowledge_2", "FARMING", "Soil Knowledge II", "Can improve soil", 3, List.of("soil_knowledge"), false, null, 1, "SOIL_IMPROVE", 1.0);
        reg("season_read", "FARMING", "Season Reading I", "See season crop info", 1, empty, false, null, 1, "SEASON_CROP_INFO", 1.0);
        reg("season_read_2", "FARMING", "Season Reading II", "Off-season grow rate +50%", 5, List.of("season_read"), false, null, 1, "OFF_SEASON_GROW", 0.50);
        reg("greenhouse_1", "FARMING", "Greenhouse I", "Greenhouse Water Efficiency +20%", 3, empty, false, null, 1, "GREENHOUSE_WATER", 0.20);
        reg("greenhouse_2", "FARMING", "Greenhouse II", "Can greenhouse in winter", 5, List.of("greenhouse_1"), false, null, 1, "GREENHOUSE_WINTER", 1.0);
        reg("water_mgmt_1", "FARMING", "Water Management I", "Water Grow Boost +20%", 2, empty, false, null, 1, "WATER_GROW_BOOST", 0.20);
        reg("water_mgmt_2", "FARMING", "Water Management II", "Overwater Resistance +50%", 3, List.of("water_mgmt_1"), false, null, 1, "OVERWATER_RESIST", 0.50);
        reg("pest_1", "FARMING", "Pest Control I", "Pest Resistance +30%", 2, empty, false, null, 1, "PEST_RESIST", 0.30);
        reg("pest_2", "FARMING", "Pest Control II", "Can craft pest control", 3, List.of("pest_1"), false, null, 1, "PEST_CRAFT", 1.0);
        reg("harvest_1", "FARMING", "Harvest Technique I", "Harvest Drop +20%", 2, empty, false, null, 1, "HARVEST_DROP", 0.20);
        reg("harvest_2", "FARMING", "Harvest Technique II", "Harvest Seed Drop +20%", 3, List.of("harvest_1"), false, null, 1, "HARVEST_SEED_DROP", 0.20);
        reg("preserve_farm_1", "FARMING", "Farm Preservation I", "Crop Decay -20%", 2, empty, false, null, 1, "CROP_DECAY", -0.20);
        reg("preserve_farm_2", "FARMING", "Farm Preservation II", "Crop Quality Decay -30%", 3, List.of("preserve_farm_1"), false, null, 1, "CROP_QUALITY_DECAY", -0.30);
        reg("breeding", "FARMING", "Breeding Improvement", "Crop Quality Cap increased", 5, empty, false, null, 1, "CROP_QUALITY_CAP", 1.0);
        reg("multi_harvest", "FARMING", "Multi-Harvest (A)", "Harvest Drop +40%", 8, empty, true, "farm_type", 1, "HARVEST_DROP", 0.40);
        reg("quality_farm", "FARMING", "Quality Farm (B)", "Crop Quality +30%", 8, empty, true, "farm_type", 1, "CROP_QUALITY", 0.30);
        reg("earth_blessing", "FARMING", "Earth's Blessing", "Natural Growth Rate +20%", 10, empty, false, null, 1, "NATURAL_GROW", 0.20);

        // ===== COMBAT =====
        reg("combat_train_1", "COMBAT", "Combat Training I", "All Damage +5%", 1, empty, false, null, 1, "ALL_DAMAGE", 0.05);
        reg("combat_train_2", "COMBAT", "Combat Training II", "All Damage +10%", 2, List.of("combat_train_1"), false, null, 1, "ALL_DAMAGE", 0.10);
        reg("combat_train_3", "COMBAT", "Combat Training III", "All Damage +15%", 3, List.of("combat_train_2"), false, null, 1, "ALL_DAMAGE", 0.15);
        reg("danger_sense", "COMBAT", "Danger Sense I", "See attack warnings", 2, empty, false, null, 1, "ATTACK_WARNING", 1.0);
        reg("danger_sense_2", "COMBAT", "Danger Sense II", "Dodge buff", 5, List.of("danger_sense"), false, null, 1, "DODGE_BUFF", 1.0);
        reg("combat_adapt", "COMBAT", "Combat Adaptation I", "Combat Stress -20%", 2, empty, false, null, 1, "COMBAT_STRESS", -0.20);
        reg("combat_adapt_2", "COMBAT", "Combat Adaptation II", "Combat Stress -40%", 3, List.of("combat_adapt"), false, null, 1, "COMBAT_STRESS", -0.40);
        reg("melee_1", "COMBAT", "Melee I", "Melee XP Rate +20%", 1, empty, false, null, 1, "MELEE_XP_RATE", 0.20);
        reg("melee_2", "COMBAT", "Melee II", "Melee Stamina Cost -15%", 2, List.of("melee_1"), false, null, 1, "MELEE_STAMINA_COST", -0.15);
        reg("counter", "COMBAT", "Counter", "Guard counter +30%", 5, List.of("melee_2"), false, null, 1, "GUARD_COUNTER", 0.30);
        reg("hard_style", "COMBAT", "Hard Style (A)", "Melee Damage +20%", 8, empty, true, "melee_style", 1, "MELEE_DAMAGE", 0.20);
        reg("soft_style", "COMBAT", "Soft Style (B)", "Melee Stamina Cost -30%", 8, empty, true, "melee_style", 1, "MELEE_STAMINA_COST", -0.30);
        reg("ranged_1", "COMBAT", "Ranged I", "Ranged XP Rate +20%", 1, empty, false, null, 1, "RANGED_XP_RATE", 0.20);
        reg("ranged_2", "COMBAT", "Ranged II", "Ranged Stamina Cost -15%", 2, List.of("ranged_1"), false, null, 1, "RANGED_STAMINA_COST", -0.15);
        reg("snipe", "COMBAT", "Sniping", "Long Range Accuracy +30%", 3, List.of("ranged_2"), false, null, 1, "LONG_RANGE_ACCURACY", 0.30);
        reg("rapid_fire", "COMBAT", "Rapid Fire", "Ranged Speed +20%", 5, List.of("ranged_2"), false, null, 1, "RANGED_SPEED", 0.20);
        reg("stealth_1", "COMBAT", "Stealth I", "Footstep noise reduced", 1, empty, false, null, 1, "FOOTSTEP_REDUCE", 1.0);
        reg("stealth_2", "COMBAT", "Stealth II", "Detection Range -20%", 2, List.of("stealth_1"), false, null, 1, "DETECT_RANGE", -0.20);
        reg("ambush", "COMBAT", "Ambush", "Stealth Damage +50%", 5, List.of("stealth_2"), false, null, 1, "STEALTH_DAMAGE", 0.50);
        reg("assassin", "COMBAT", "Assassin (A)", "Ambush Damage +50%", 8, empty, true, "stealth_type", 1, "AMBUSH_DAMAGE", 0.50);
        reg("shadow", "COMBAT", "Shadow (B)", "Stealth Duration +50%", 8, empty, true, "stealth_type", 1, "STEALTH_DURATION", 0.50);
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
            player.sendMessage(ChatColor.GREEN + displayName + " level up! " + ChatColor.GRAY + oldLevel + " -> " + ChatColor.GOLD + currentLevel);
            if (gainedPoints > 0) {
                player.sendMessage(ChatColor.YELLOW + "Gained " + gainedPoints + " skill point" + (gainedPoints != 1 ? "s" : "") + "!");
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
            player.sendMessage(ChatColor.RED + "Ability already maxed: " + ability.getName());
            return false;
        }

        int spent = getSpentPoints(data, ability.getSkillTree());
        if (spent + ability.getCost() > plugin.getConfigManager().skillsMaxPointsPerSkill) {
            player.sendMessage(ChatColor.RED + "Max points reached for " + ability.getSkillTree() + " (" + plugin.getConfigManager().skillsMaxPointsPerSkill + " pts)");
            return false;
        }

        if (data.getSkillPoints() < ability.getCost()) {
            player.sendMessage(ChatColor.RED + "Not enough skill points! Need " + ability.getCost() + ", have " + data.getSkillPoints());
            return false;
        }

        for (String prereqId : ability.getPrerequisites()) {
            if (data.getAbilityLevel(prereqId) <= 0) {
                SkillAbility prereq = ALL_ABILITIES.get(prereqId);
                String name = prereq != null ? prereq.getName() : prereqId;
                player.sendMessage(ChatColor.RED + "Requires: " + name);
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
                        player.sendMessage(ChatColor.RED + "Already chosen: " + other.getName() + " (exclusive group)");
                        return false;
                    }
                }
            }
        }

        data.setSkillPoints(data.getSkillPoints() - ability.getCost());
        data.setAbilityLevel(ability.getId(), currentLevel + 1);

        player.sendMessage(ChatColor.GREEN + "Unlocked: " + ChatColor.GOLD + ability.getName() + ChatColor.GREEN + "!");
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
        player.sendMessage(ChatColor.RED + "Reset " + displayName + ". Level reduced to " + newLevel + " (penalty: -" + penalty + ")");
        player.sendMessage(ChatColor.GRAY + "Spent points are lost and not refunded.");
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
