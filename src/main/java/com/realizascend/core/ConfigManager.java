package com.realizascend.core;

import com.realizascend.RealizAscend;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigManager {

    private final RealizAscend plugin;
    private FileConfiguration config;

    public int seasonDaysPerSeason;
    public String seasonInitialSeason;

    public double tempComfortableMin;
    public double tempComfortableMax;
    public double tempWarningOffset;
    public double tempCriticalOffset;

    public double nutritionMaxValue;
    public double nutritionDecayRate;
    public double nutritionWarningThreshold;

    public double hydrationMaxValue;
    public double hydrationDecayRate;
    public double hydrationWarningThreshold;

    public double bleedingMaxBlood;
    public double bleedingRegenerationRate;
    public double bleedingFatalThreshold;

    public double staminaMaxValue;
    public double staminaRegenerationRate;
    public double staminaSprintCost;
    public double staminaJumpCost;
    public double staminaAttackCost;

    public double fatigueMaxValue;
    public double fatigueAccumulationRate;
    public double fatigueSleepRecovery;

    public double stressMaxValue;
    public double stressNormalRangeMin;
    public double stressNormalRangeMax;

    public double weightNormalLimit;
    public double weightOverLimit;

    public int torchDurationMinutes;
    public int torchFuelExtensionMinutes;

    public int corpseDurationDays;
    public boolean corpseRemoveOnExpire;

    public boolean worldDisableWaterSource;
    public Set<Material> blockGravityWhitelist;

    public double skillsXpGainMultiplier;
    public int skillsMaxPointsPerSkill;

    public boolean hudEnabled;
    public int hudUpdateInterval;

    public int statusFlavorInterval;

    public ConfigManager(RealizAscend plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();

        seasonDaysPerSeason = config.getInt("season.days-per-season", 7);
        seasonInitialSeason = config.getString("season.initial-season", "SPRING");

        tempComfortableMin = config.getDouble("temperature.comfortable-min", 15.0);
        tempComfortableMax = config.getDouble("temperature.comfortable-max", 25.0);
        tempWarningOffset = config.getDouble("temperature.warning-offset", 10.0);
        tempCriticalOffset = config.getDouble("temperature.critical-offset", 20.0);

        nutritionMaxValue = config.getDouble("nutrition.max-value", 100.0);
        nutritionDecayRate = config.getDouble("nutrition.decay-rate", 0.3);
        nutritionWarningThreshold = config.getDouble("nutrition.warning-threshold", 30.0);

        hydrationMaxValue = config.getDouble("hydration.max-value", 100.0);
        hydrationDecayRate = config.getDouble("hydration.decay-rate", 0.5);
        hydrationWarningThreshold = config.getDouble("hydration.warning-threshold", 25.0);

        bleedingMaxBlood = config.getDouble("bleeding.max-blood", 100.0);
        bleedingRegenerationRate = config.getDouble("bleeding.regeneration-rate", 0.1);
        bleedingFatalThreshold = config.getDouble("bleeding.fatal-threshold", 0.0);

        staminaMaxValue = config.getDouble("stamina.max-value", 100.0);
        staminaRegenerationRate = config.getDouble("stamina.regeneration-rate", 0.5);
        staminaSprintCost = config.getDouble("stamina.sprint-cost", 0.3);
        staminaJumpCost = config.getDouble("stamina.jump-cost", 2.0);
        staminaAttackCost = config.getDouble("stamina.attack-cost", 1.5);

        fatigueMaxValue = config.getDouble("fatigue.max-value", 100.0);
        fatigueAccumulationRate = config.getDouble("fatigue.accumulation-rate", 0.15);
        fatigueSleepRecovery = config.getDouble("fatigue.sleep-recovery", 80.0);

        stressMaxValue = config.getDouble("stress.max-value", 100.0);
        stressNormalRangeMin = config.getDouble("stress.normal-range-min", 30.0);
        stressNormalRangeMax = config.getDouble("stress.normal-range-max", 70.0);

        weightNormalLimit = config.getDouble("weight.normal-limit", 1000.0);
        weightOverLimit = config.getDouble("weight.over-limit", 1500.0);

        torchDurationMinutes = config.getInt("torch.duration-minutes", 60);
        torchFuelExtensionMinutes = config.getInt("torch.fuel-extension-minutes", 30);

        corpseDurationDays = config.getInt("corpse.duration-days", 1);
        corpseRemoveOnExpire = config.getBoolean("corpse.remove-on-expire", true);

        worldDisableWaterSource = config.getBoolean("world.disable-water-source", true);

        blockGravityWhitelist = new HashSet<>();
        List<String> whitelist = config.getStringList("world.block-gravity-whitelist");
        for (String matName : whitelist) {
            try {
                blockGravityWhitelist.add(Material.valueOf(matName));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in block-gravity-whitelist: " + matName);
            }
        }

        skillsXpGainMultiplier = config.getDouble("skills.xp-gain-multiplier", 1.0);
        skillsMaxPointsPerSkill = config.getInt("skills.max-points-per-skill", 100);

        hudEnabled = config.getBoolean("hud.enabled", true);
        hudUpdateInterval = config.getInt("hud.update-interval", 20);

        statusFlavorInterval = config.getInt("status.flavor-interval", 1200);
    }
}
