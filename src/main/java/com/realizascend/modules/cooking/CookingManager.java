package com.realizascend.modules.cooking;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

public class CookingManager extends RealizModule implements Listener {

    private final NamespacedKey qualityKey = new NamespacedKey(plugin, "quality");
    private final NamespacedKey perfectKey = new NamespacedKey(plugin, "perfect_bonus");
    private final Random random = new Random();

    private enum Quality {
        BURNT("Burnt ", ChatColor.DARK_GRAY, 0.7),
        NORMAL("", ChatColor.WHITE, 1.0),
        WELL_COOKED("Well-cooked ", ChatColor.GREEN, 1.2),
        PERFECT("Perfect ", ChatColor.GOLD, 1.5);

        final String prefix;
        final ChatColor color;
        final double nutritionMultiplier;

        Quality(String prefix, ChatColor color, double nutritionMultiplier) {
            this.prefix = prefix;
            this.color = color;
            this.nutritionMultiplier = nutritionMultiplier;
        }
    }

    private static class FoodValues {
        final double calories, protein, vitamins, salt, hydration;
        FoodValues(double calories, double protein, double vitamins, double salt, double hydration) {
            this.calories = calories;
            this.protein = protein;
            this.vitamins = vitamins;
            this.salt = salt;
            this.hydration = hydration;
        }
    }

    public CookingManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean isCookedFood(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        return mat == Material.COOKED_BEEF || mat == Material.COOKED_CHICKEN
            || mat == Material.COOKED_COD || mat == Material.COOKED_MUTTON
            || mat == Material.COOKED_PORKCHOP || mat == Material.COOKED_RABBIT
            || mat == Material.COOKED_SALMON || mat == Material.BAKED_POTATO
            || mat == Material.DRIED_KELP || mat == Material.BREAD
            || mat == Material.PUMPKIN_PIE || mat == Material.MUSHROOM_STEW
            || mat == Material.BEETROOT_SOUP || mat == Material.RABBIT_STEW;
    }

    private String getFoodDisplayBase(ItemStack item) {
        Material mat = item.getType();
        switch (mat) {
            case COOKED_BEEF: return "Steak";
            case COOKED_CHICKEN: return "Chicken";
            case COOKED_COD: return "Cod";
            case COOKED_MUTTON: return "Mutton";
            case COOKED_PORKCHOP: return "Porkchop";
            case COOKED_RABBIT: return "Rabbit";
            case COOKED_SALMON: return "Salmon";
            case BAKED_POTATO: return "Potato";
            case DRIED_KELP: return "Kelp";
            case BREAD: return "Bread";
            case PUMPKIN_PIE: return "Pie";
            default: return item.getType().name().replace("_", " ");
        }
    }

    private Quality calculateQuality(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        int skillLevel = data.getSkillLevel("COOKING");

        int tierCount = 0;
        for (String tierId : new String[]{
            "cook_grill_1", "cook_grill_2", "cook_grill_3",
            "cook_stew_1", "cook_stew_2", "cook_stew_3",
            "cook_smoke_1", "cook_smoke_2", "cook_smoke_3",
            "cook_preserve_1", "cook_preserve_2", "cook_preserve_3",
            "cook_instant_1", "cook_instant_2", "cook_instant_3"
        }) {
            if (data.getAbilityLevel(tierId) > 0) {
                tierCount++;
            }
        }

        double roll = random.nextDouble();
        double skillBonus = skillLevel * 0.02 + tierCount * 0.04;
        double threshold = skillBonus + roll * 0.5;

        if (threshold < 0.25) return Quality.BURNT;
        if (threshold < 0.60) return Quality.NORMAL;
        if (threshold < 0.85) return Quality.WELL_COOKED;
        return Quality.PERFECT;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFurnaceExtract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!(event.getInventory() instanceof FurnaceInventory)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;
        if (!isCookedFood(result)) return;

        Quality quality = calculateQuality(player);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        // 工具の質(包丁の残り耐久)が高いほど品質が上がりやすい
        if (plugin.getToolManager().hasTool(player, "KNIFE")) {
            int knifeDura = plugin.getToolManager().getToolDurability(player, "KNIFE");
            if (knifeDura >= 35) {
                quality = upgradeQuality(quality);
            } else if (knifeDura >= 15 && quality == Quality.BURNT) {
                quality = Quality.NORMAL;
            }
            plugin.getToolManager().degradeTool(player, "KNIFE");
        }

        meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.STRING, quality.name());
        meta.setDisplayName(quality.color + quality.prefix + getFoodDisplayBase(result));
        // 至高の一皿: 最高品質の料理に栄養ボーナスを焼き込む
        if (quality == Quality.PERFECT) {
            double perfectBonus = plugin.getSkillManager().getAbilityEffectValue(player, "COOK_PERFECT_BONUS");
            if (perfectBonus > 1.0) {
                meta.getPersistentDataContainer().set(perfectKey, PersistentDataType.DOUBLE, perfectBonus);
            }
        } else {
            meta.getPersistentDataContainer().remove(perfectKey);
        }
        result.setItemMeta(meta);
    }

    private Quality upgradeQuality(Quality quality) {
        switch (quality) {
            case BURNT: return Quality.NORMAL;
            case NORMAL: return Quality.WELL_COOKED;
            case WELL_COOKED: return Quality.PERFECT;
            default: return Quality.PERFECT;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String qualityStr = meta.getPersistentDataContainer().get(qualityKey, PersistentDataType.STRING);
        if (qualityStr == null) return;

        Quality quality;
        try {
            quality = Quality.valueOf(qualityStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);
        double multiplier = quality.nutritionMultiplier;
        // 至高の一皿: パーフェクト料理の栄養+50%等
        Double perfectBonus = meta.getPersistentDataContainer().get(perfectKey, PersistentDataType.DOUBLE);
        if (perfectBonus != null) {
            multiplier *= perfectBonus;
        }

        FoodValues base = getBaseFoodValues(item.getType());
        if (base == null) return;

        data.setCalories(data.getCalories() + base.calories * multiplier);
        data.setProtein(data.getProtein() + base.protein * multiplier);
        data.setVitamins(data.getVitamins() + base.vitamins * multiplier);
        data.setSalt(data.getSalt() + base.salt * multiplier);
        data.setHydration(data.getHydration() + base.hydration * multiplier);

        if (plugin.getFoodManager().isSalted(item)) {
            data.setHydration(Math.max(0, data.getHydration() - 5.0));
            data.setSalt(data.getSalt() + 2.0);
        }

        if (plugin.getFoodManager().isSmoked(item)) {
            data.setCalories(data.getCalories() + base.calories * multiplier * 0.1);
            data.setProtein(data.getProtein() + base.protein * multiplier * 0.1);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        player.sendMessage(ChatColor.GRAY + "Ate " + quality.color + quality.prefix
            + getFoodDisplayBase(item) + ChatColor.GRAY + " ("
            + (int)(multiplier * 100) + "% nutrition)");
    }

    private FoodValues getBaseFoodValues(Material mat) {
        switch (mat) {
            case COOKED_BEEF:
            case COOKED_PORKCHOP:
                return new FoodValues(15, 20, 2, 5, -5);
            case COOKED_CHICKEN:
            case COOKED_MUTTON:
            case COOKED_RABBIT:
                return new FoodValues(10, 15, 3, 3, -3);
            case COOKED_COD:
            case COOKED_SALMON:
                return new FoodValues(8, 18, 3, 5, -3);
            case BREAD:
            case BAKED_POTATO:
                return new FoodValues(20, 5, 3, 2, -5);
            case MUSHROOM_STEW:
            case RABBIT_STEW:
            case BEETROOT_SOUP:
                return new FoodValues(15, 10, 8, 5, 20);
            case DRIED_KELP:
                return new FoodValues(3, 1, 2, 10, -10);
            default:
                return null;
        }
    }

    @Override
    public void onDisable() {
        InventoryClickEvent.getHandlerList().unregister(this);
        PlayerItemConsumeEvent.getHandlerList().unregister(this);
    }
}
