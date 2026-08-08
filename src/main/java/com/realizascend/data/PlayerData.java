package com.realizascend.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    private UUID uuid;

    // Nutrition
    private double calories = 50.0;
    private double protein = 50.0;
    private double vitamins = 50.0;
    private double salt = 50.0;
    private double hydration = 80.0;

    // Bleeding / Injury
    private double blood = 100.0;
    private boolean headInjured;
    private boolean torsoInjured;
    private boolean legsInjured;
    private boolean infected;
    private double infectionProgress;
    private boolean fractured;

    // Stamina / Fatigue
    private double stamina = 100.0;
    private double fatigue = 0.0;
    private double healthLevel = 100.0;
    private int sleepDebt;
    private int staminaBonusPercent = 0;

    // Stress
    private double stress = 30.0;

    // Weight
    private double currentWeight = 0.0;

    // Temperature
    private double bodyTemperature = 20.0;
    private String tempZone = "COMFORTABLE";

    // Skills
    private int skillPoints = 0;
    private Map<String, Integer> skillLevels = new HashMap<>();
    private Map<String, Double> skillXp = new HashMap<>();
    private Map<String, Integer> unlockedAbilities = new HashMap<>();

    // Codex
    private Map<String, Boolean> codexEntries = new HashMap<>();

    // Cooking
    private int tasteLevel = 0;

    // Misc
    private boolean torchPlaced;

    public PlayerData() {}

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isOnline() {
        return getPlayer() != null && getPlayer().isOnline();
    }

    // --- Nutrition ---
    public double getCalories() { return calories; }
    public void setCalories(double v) { calories = clamp(v, 0, 100); }
    public double getProtein() { return protein; }
    public void setProtein(double v) { protein = clamp(v, 0, 100); }
    public double getVitamins() { return vitamins; }
    public void setVitamins(double v) { vitamins = clamp(v, 0, 100); }
    public double getSalt() { return salt; }
    public void setSalt(double v) { salt = clamp(v, 0, 100); }
    public double getHydration() { return hydration; }
    public void setHydration(double v) { hydration = clamp(v, 0, 100); }

    public double getNutritionBalance() {
        return (calories + protein + vitamins + salt) / 4.0;
    }

    // --- Bleeding ---
    public double getBlood() { return blood; }
    public void setBlood(double v) { blood = clamp(v, 0, 100); }
    public boolean isHeadInjured() { return headInjured; }
    public void setHeadInjured(boolean v) { headInjured = v; }
    public boolean isTorsoInjured() { return torsoInjured; }
    public void setTorsoInjured(boolean v) { torsoInjured = v; }
    public boolean isLegsInjured() { return legsInjured; }
    public void setLegsInjured(boolean v) { legsInjured = v; }
    public boolean isInfected() { return infected; }
    public void setInfected(boolean v) { infected = v; }
    public double getInfectionProgress() { return infectionProgress; }
    public void setInfectionProgress(double v) { infectionProgress = clamp(v, 0, 100); }
    public boolean isFractured() { return fractured; }
    public void setFractured(boolean v) { fractured = v; }

    // --- Stamina ---
    public double getStamina() { return stamina; }
    public void setStamina(double v) { stamina = clamp(v, 0, 100); }
    public double getFatigue() { return fatigue; }
    public void setFatigue(double v) { fatigue = clamp(v, 0, 100); }
    public double getHealthLevel() { return healthLevel; }
    public void setHealthLevel(double v) { healthLevel = clamp(v, 0, 100); }
    public int getSleepDebt() { return sleepDebt; }
    public void setSleepDebt(int v) { sleepDebt = Math.max(0, v); }

    public int getStaminaBonusPercent() { return staminaBonusPercent; }
    public void setStaminaBonusPercent(int v) { staminaBonusPercent = Math.max(0, v); }
    public void addStaminaBonusPercent(int v) { staminaBonusPercent = Math.min(25, Math.max(0, staminaBonusPercent + v)); }

    // --- Stress ---
    public double getStress() { return stress; }
    public void setStress(double v) { stress = clamp(v, 0, 100); }

    // --- Weight ---
    public double getCurrentWeight() { return currentWeight; }
    public void setCurrentWeight(double v) { currentWeight = Math.max(0, v); }

    // --- Temperature ---
    public double getBodyTemperature() { return bodyTemperature; }
    public void setBodyTemperature(double v) { bodyTemperature = v; }
    public String getTempZone() { return tempZone; }
    public void setTempZone(String v) { tempZone = v; }

    // --- Skills ---
    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int v) { skillPoints = Math.max(0, v); }
    public void addSkillPoints(int v) { skillPoints += v; }

    public int getSkillLevel(String skillId) {
        return skillLevels.getOrDefault(skillId, 0);
    }
    public void setSkillLevel(String skillId, int level) {
        skillLevels.put(skillId, Math.max(0, level));
    }

    public double getSkillXp(String skillId) {
        return skillXp.getOrDefault(skillId, 0.0);
    }
    public void setSkillXp(String skillId, double xp) {
        skillXp.put(skillId, Math.max(0, xp));
    }
    public void addSkillXp(String skillId, double xp) {
        skillXp.put(skillId, getSkillXp(skillId) + xp);
    }

    public int getAbilityLevel(String abilityId) {
        return unlockedAbilities.getOrDefault(abilityId, 0);
    }
    public void setAbilityLevel(String abilityId, int level) {
        unlockedAbilities.put(abilityId, Math.max(0, level));
    }

    public Map<String, Integer> getSkillLevels() { return skillLevels; }
    public Map<String, Double> getSkillXp() { return skillXp; }
    public Map<String, Integer> getUnlockedAbilities() { return unlockedAbilities; }

    // --- Codex ---
    public boolean hasCodexEntry(String key) {
        return codexEntries.getOrDefault(key, false);
    }
    public void unlockCodexEntry(String key) {
        codexEntries.put(key, true);
    }
    public Map<String, Boolean> getCodexEntries() { return codexEntries; }

    // --- Misc ---
    public boolean hasTorchPlaced() { return torchPlaced; }

    public void setTorchPlaced(boolean v) { torchPlaced = v; }

    public int getTasteLevel() { return tasteLevel; }

    public void setTasteLevel(int v) { tasteLevel = Math.max(0, v); }

    public void addTasteLevel(int v) { tasteLevel = Math.max(0, tasteLevel + v); }
    public UUID getUuid() { return uuid; }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
