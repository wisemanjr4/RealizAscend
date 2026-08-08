package com.realizascend.modules.cookingstation;

import com.realizascend.RealizAscend;
import com.realizascend.data.PlayerData;
import com.realizascend.modules.skill.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CookingStationMenu {

    public static final String TITLE = ChatColor.DARK_GRAY + "調理台";

    private static RealizAscend plugin;
    private static NamespacedKey calKey;
    private static NamespacedKey proKey;
    private static NamespacedKey vitKey;
    private static NamespacedKey saltKey;
    private static NamespacedKey hydKey;
    private static NamespacedKey dishKey;

    private static final List<Dish> DISHES = new ArrayList<>();

    private static class Dish {
        final List<Material> ingredients;
        final Material baseMaterial;
        final String name;
        final int calories, protein, vitamins, salt, hydration;
        final String requiredAbility;
        final int tasteRequired;
        final boolean perfect;

        Dish(List<Material> ingredients, Material baseMaterial, String name,
             int calories, int protein, int vitamins, int salt, int hydration,
             String requiredAbility, int tasteRequired, boolean perfect) {
            this.ingredients = ingredients;
            this.baseMaterial = baseMaterial;
            this.name = name;
            this.calories = calories;
            this.protein = protein;
            this.vitamins = vitamins;
            this.salt = salt;
            this.hydration = hydration;
            this.requiredAbility = requiredAbility;
            this.tasteRequired = tasteRequired;
            this.perfect = perfect;
        }

        boolean isUnlocked(PlayerData data) {
            if (requiredAbility != null && data.getAbilityLevel(requiredAbility) <= 0) return false;
            return data.getTasteLevel() >= tasteRequired;
        }
    }

    public static void init(RealizAscend p) {
        plugin = p;
        calKey = new NamespacedKey(p, "dish_cal");
        proKey = new NamespacedKey(p, "dish_pro");
        vitKey = new NamespacedKey(p, "dish_vit");
        saltKey = new NamespacedKey(p, "dish_salt");
        hydKey = new NamespacedKey(p, "dish_hyd");
        dishKey = new NamespacedKey(p, "dish_item");

        DISHES.clear();
        DISHES.add(new Dish(List.of(Material.BEEF, Material.POTATO, Material.CARROT, Material.BOWL),
            Material.MUSHROOM_STEW, "シチュー", 30, 25, 8, 6, 25, "cook_stew_1", 0, false));
        DISHES.add(new Dish(List.of(Material.COD, Material.SWEET_BERRIES, Material.BOWL),
            Material.COOKED_COD, "魚の煮付け", 20, 20, 5, 6, 15, "cook_stew_2", 0, false));
        DISHES.add(new Dish(List.of(Material.CHICKEN, Material.STICK, Material.SWEET_BERRIES),
            Material.COOKED_CHICKEN, "焼き鳥", 18, 22, 3, 4, 0, "cook_grill_1", 0, false));
        DISHES.add(new Dish(List.of(Material.PORKCHOP, Material.POTATO, Material.BOWL),
            Material.COOKED_PORKCHOP, "鉄板焼き肉", 28, 26, 4, 6, -5, "cook_grill_2", 3, false));
        DISHES.add(new Dish(List.of(Material.MELON_SLICE, Material.APPLE, Material.SWEET_BERRIES, Material.BOWL),
            Material.MUSHROOM_STEW, "フルーツサラダ", 12, 2, 25, 0, 25, null, 0, false));
        DISHES.add(new Dish(List.of(Material.PORKCHOP, Material.CHARCOAL, Material.STRING),
            Material.COOKED_PORKCHOP, "燻製ベーコン", 22, 20, 2, 8, -10, "cook_smoke_1", 5, false));
        DISHES.add(new Dish(List.of(Material.BEETROOT, Material.CARROT, Material.BOWL, Material.WHEAT),
            Material.BEETROOT_SOUP, "野草スープ", 15, 5, 18, 4, 20, null, 0, false));
        DISHES.add(new Dish(List.of(Material.WHEAT, Material.SUGAR, Material.BOWL),
            Material.MUSHROOM_STEW, "即席おかゆ", 20, 6, 5, 3, 18, "cook_instant_1", 2, false));

        // パーフェクトレシピ (秘密: ヒントに表示しない)
        DISHES.add(new Dish(List.of(Material.BEEF, Material.POTATO, Material.CARROT, Material.BOWL, Material.GOLDEN_APPLE),
            Material.MUSHROOM_STEW, "至高のシチュー", 45, 40, 15, 8, 35, "cook_stew_2", 8, true));
        DISHES.add(new Dish(List.of(Material.CHICKEN, Material.STICK, Material.GOLDEN_APPLE),
            Material.COOKED_CHICKEN, "至高の焼き鳥", 30, 35, 10, 5, 5, "cook_grill_2", 8, true));
        DISHES.add(new Dish(List.of(Material.MELON_SLICE, Material.APPLE, Material.SWEET_BERRIES, Material.GOLDEN_APPLE, Material.BOWL),
            Material.MUSHROOM_STEW, "豪華フルーツサラダ", 20, 4, 40, 0, 40, null, 10, true));
    }

    public static void open(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        for (int i = 0; i < 27; i++) {
            if (i <= 3 || i == 8 || i == 17 || i == 18) continue;
            inv.setItem(i, createHintPane(data));
        }
        inv.setItem(17, createCookButton());
        inv.setItem(18, createCloseButton());

        player.openInventory(inv);
    }

    public static void updateResult(Inventory inv, Player player) {
        Dish dish = findDish(inv, player);
        inv.setItem(8, dish != null ? createDishItem(dish) : null);
    }

    public static void cook(Inventory inv, Player player) {
        Dish dish = findDish(inv, player);
        if (dish == null) {
            player.sendMessage(ChatColor.RED + "食材が足りない");
            return;
        }

        for (int i = 0; i < 4; i++) {
            inv.setItem(i, null);
        }

        int amount = 1;
        if (plugin.getToolManager().hasTool(player, "KNIFE")) {
            plugin.getToolManager().degradeTool(player, "KNIFE");
            amount = 2;
        }

        ItemStack dishItem = createDishItem(dish);
        dishItem.setAmount(amount);
        plugin.getFoodManager().markCooked(dishItem);
        if (dish.perfect) {
            plugin.getFoodManager().markPreserved(dishItem, "DRIED");
            player.sendMessage(ChatColor.GOLD + "パーフェクトレシピを発見した!");
        }
        player.getInventory().addItem(dishItem)
            .values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        plugin.getSkillManager().addXp(player, "COOKING", 8);
    }

    private static Dish findDish(Inventory inv, Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        List<Material> present = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                present.add(it.getType());
            }
        }
        for (Dish dish : DISHES) {
            if (!dish.isUnlocked(data)) continue;
            boolean match = true;
            for (Material ingredient : dish.ingredients) {
                if (!present.contains(ingredient)) {
                    match = false;
                    break;
                }
            }
            if (match) return dish;
        }
        return null;
    }

    private static ItemStack createDishItem(Dish dish) {
        ItemStack item = new ItemStack(dish.baseMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + dish.name);
        meta.setLore(List.of(ChatColor.GRAY + "カロリー " + signed(dish.calories)
            + " タンパク質 " + signed(dish.protein)
            + " ビタミン " + signed(dish.vitamins)
            + " 塩分 " + signed(dish.salt)
            + " 水分 " + signed(dish.hydration)));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(dishKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(calKey, PersistentDataType.INTEGER, dish.calories);
        pdc.set(proKey, PersistentDataType.INTEGER, dish.protein);
        pdc.set(vitKey, PersistentDataType.INTEGER, dish.vitamins);
        pdc.set(saltKey, PersistentDataType.INTEGER, dish.salt);
        pdc.set(hydKey, PersistentDataType.INTEGER, dish.hydration);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createHintPane(PlayerData data) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "レシピヒント");
        List<String> lore = new ArrayList<>();
        for (Dish dish : DISHES) {
            if (dish.perfect) continue; // パーフェクトレシピは秘密
            if (dish.isUnlocked(data)) {
                lore.add(ChatColor.GRAY + dish.name);
            } else {
                if (dish.requiredAbility != null) {
                    lore.add(ChatColor.RED + "要スキル: " + abilityName(dish.requiredAbility));
                } else {
                    lore.add(ChatColor.RED + "要味覚Lv: " + dish.tasteRequired);
                }
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCookButton() {
        ItemStack item = new ItemStack(Material.CAMPFIRE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "調理する");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "閉じる");
        item.setItemMeta(meta);
        return item;
    }

    private static String abilityName(String abilityId) {
        SkillManager.SkillAbility ability = SkillManager.getAbility(abilityId);
        return ability != null ? ability.getName() : abilityId;
    }

    private static String signed(int value) {
        return (value >= 0 ? "+" : "") + value;
    }
}
