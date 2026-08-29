package com.realizascend.modules.death;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.realizascend.modules.skill.SkillManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeathManager extends RealizModule implements Listener {

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

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);

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

        // BUG 12 fix: Clear skill XP on death
        data.getSkillXp().clear();

        player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "=== 死亡しました ===");
        player.sendMessage(ChatColor.GRAY + "HP・栄養・水分・スキルにペナルティが発生した。");
        player.sendMessage(ChatColor.GRAY + "死体を探すか、仲間にアイテムを回収してもらおう。");
        player.sendMessage(ChatColor.GRAY + "現在HP: " + ChatColor.RED + "70"
            + ChatColor.GRAY + " | 血液: " + ChatColor.RED + "70"
            + ChatColor.GRAY + " | 空腹: " + ChatColor.RED + "30");
    }

    @Override
    public void onDisable() {
        PlayerRespawnEvent.getHandlerList().unregister(this);
    }
}
