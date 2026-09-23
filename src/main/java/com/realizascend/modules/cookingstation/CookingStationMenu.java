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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CookingStationMenu {

    public static final String TITLE = ChatColor.DARK_GRAY + "調理台";

    private static RealizAscend plugin;
    private static NamespacedKey calKey;
    private static NamespacedKey proKey;
    private static NamespacedKey vitKey;
    private static NamespacedKey saltKey;
    private static NamespacedKey hydKey;
    private static NamespacedKey dishKey;

    public static final List<Dish> DISHES = new ArrayList<>();

    public static class Dish {
        public final List<Material> ingredients;
        public final Material baseMaterial;
        public final String name;
        public final int calories, protein, vitamins, salt, hydration;
        public final String requiredAbility;
        public final int tasteRequired;
        public final boolean perfect;
        public final String preservedType;

        Dish(List<Material> ingredients, Material baseMaterial, String name,
             int calories, int protein, int vitamins, int salt, int hydration,
             String requiredAbility, int tasteRequired, boolean perfect) {
            this(ingredients, baseMaterial, name, calories, protein, vitamins, salt, hydration, requiredAbility, tasteRequired, perfect, null);
        }

        Dish(List<Material> ingredients, Material baseMaterial, String name,
             int calories, int protein, int vitamins, int salt, int hydration,
             String requiredAbility, int tasteRequired, boolean perfect, String preservedType) {
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
            this.preservedType = preservedType;
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

// 保存食 (賞味期限が長い)
        DISHES.add(new Dish(List.of(Material.COOKED_SALMON, Material.CHARCOAL, Material.STRING),
            Material.COOKED_SALMON, "燻製サーモン", 20, 22, 4, 7, -5, "cook_smoke_2", 0, false, "SMOKED"));
        DISHES.add(new Dish(List.of(Material.COOKED_BEEF, Material.CHARCOAL, Material.PAPER),
            Material.COOKED_BEEF, "干し肉", 25, 24, 2, 6, -12, "cook_preserve_1", 0, false, "DRIED"));
        DISHES.add(new Dish(List.of(Material.BEETROOT, Material.CARROT, Material.GLASS_BOTTLE),
            Material.BEETROOT, "野菜の漬物", 10, 4, 15, 8, 10, "cook_preserve_1", 3, false, "SALTED"));

        // 長期保存食 (24h以上)
        DISHES.add(new Dish(List.of(Material.COOKED_BEEF, Material.PAPER, Material.STRING),
            Material.COOKED_BEEF, "肉の缶詰", 30, 28, 3, 8, -8, "cook_preserve_2", 0, false, "VACUUM"));
        DISHES.add(new Dish(List.of(Material.COOKED_COD, Material.PAPER, Material.STRING),
            Material.COOKED_COD, "魚の缶詰", 22, 25, 3, 7, -8, "cook_preserve_2", 0, false, "VACUUM"));
        DISHES.add(new Dish(List.of(Material.COD, Material.SUGAR, Material.GLASS_BOTTLE),
            Material.COD, "塩辛", 15, 18, 2, 12, -5, "cook_preserve_2", 5, false, "FERMENTED"));
        DISHES.add(new Dish(List.of(Material.COOKED_CHICKEN, Material.CHARCOAL, Material.GLASS_BOTTLE),
            Material.COOKED_CHICKEN, "鴨のコンフィ", 26, 24, 3, 9, -6, "cook_smoke_2", 5, false, "CONFIT"));
        DISHES.add(new Dish(List.of(Material.WHEAT, Material.SUGAR),
            Material.BREAD, "硬焼パン", 35, 5, 2, 4, -15, "cook_preserve_2", 0, false, "HARDTACK"));
        DISHES.add(new Dish(List.of(Material.MUSHROOM_STEW, Material.GLASS_BOTTLE, Material.CHARCOAL),
            Material.MUSHROOM_STEW, "保存シチュー", 35, 30, 10, 8, 25, "cook_stew_2", 5, false, "VACUUM"));

        // ===== 追加: 肉・定食系 =====
        DISHES.add(new Dish(List.of(Material.BREAD, Material.COOKED_BEEF, Material.CARROT),
            Material.BREAD, "サンドイッチ", 25, 20, 5, 5, 0, null, 0, false));
        DISHES.add(new Dish(List.of(Material.BREAD, Material.COOKED_CHICKEN, Material.CARROT),
            Material.BREAD, "チキンサンド", 22, 18, 5, 4, 0, null, 0, false));
        DISHES.add(new Dish(List.of(Material.COOKED_BEEF, Material.WHEAT, Material.MUSHROOM_STEW),
            Material.COOKED_BEEF, "ステーキ丼", 40, 32, 6, 8, 15, "cook_grill_2", 3, false));
        DISHES.add(new Dish(List.of(Material.CHICKEN, Material.CARROT, Material.BOWL),
            Material.MUSHROOM_STEW, "チキンスープ", 18, 14, 8, 4, 20, "cook_stew_1", 0, false));
        DISHES.add(new Dish(List.of(Material.SALMON, Material.COD, Material.BOWL),
            Material.MUSHROOM_STEW, "魚介スープ", 25, 22, 6, 6, 22, "cook_stew_1", 0, false));
        DISHES.add(new Dish(List.of(Material.MUTTON, Material.POTATO, Material.CARROT, Material.BOWL),
            Material.MUSHROOM_STEW, "マトンシチュー", 32, 28, 8, 7, 22, "cook_stew_2", 3, false));

        // ===== 追加: 焼き物・軽食 =====
        DISHES.add(new Dish(List.of(Material.POTATO, Material.CHARCOAL),
            Material.BAKED_POTATO, "焼き芋", 15, 2, 4, 0, 5, null, 0, false));
        DISHES.add(new Dish(List.of(Material.BREAD, Material.MELON_SLICE, Material.SUGAR),
            Material.BREAD, "メロンパン", 22, 4, 8, 3, 5, "cook_instant_1", 2, false));

        // ===== 追加: 甘味・デザート =====
        DISHES.add(new Dish(List.of(Material.SWEET_BERRIES, Material.SUGAR, Material.GLASS_BOTTLE),
            Material.HONEY_BOTTLE, "ベリージャム", 15, 0, 20, 0, 10, "cook_instant_1", 2, false));
        DISHES.add(new Dish(List.of(Material.COCOA_BEANS, Material.SUGAR, Material.PAPER),
            Material.COOKIE, "チョコレート", 18, 2, 2, 3, -3, null, 0, false));
        DISHES.add(new Dish(List.of(Material.SWEET_BERRIES, Material.WHEAT, Material.SUGAR),
            Material.PUMPKIN_PIE, "ベリータルト", 25, 3, 12, 3, 5, "cook_instant_2", 3, false));

        // ===== 追加: 戦闘食・行軍食 =====
        DISHES.add(new Dish(List.of(Material.COOKED_BEEF, Material.SUGAR, Material.CHARCOAL),
            Material.COOKED_BEEF, "ビーフジャーキー", 18, 18, 1, 10, -15, null, 0, false, "DRIED"));

        // ===== 簡単な料理 (スキル解放なし・材料2個) =====
        DISHES.add(new Dish(List.of(Material.BEEF, Material.STICK),
            Material.COOKED_BEEF, "肉の串焼き", 15, 15, 2, 3, -3, null, 0, false));
        DISHES.add(new Dish(List.of(Material.SALMON, Material.STICK),
            Material.COOKED_SALMON, "魚の串焼き", 12, 14, 2, 3, -2, null, 0, false));
        DISHES.add(new Dish(List.of(Material.APPLE, Material.CHARCOAL),
            Material.APPLE, "焼きリンゴ", 10, 0, 12, 0, 10, null, 0, false));
        DISHES.add(new Dish(List.of(Material.RED_MUSHROOM, Material.CHARCOAL),
            Material.MUSHROOM_STEW, "きのこの焼き物", 8, 2, 6, 1, 5, null, 0, false));
        DISHES.add(new Dish(List.of(Material.CARROT, Material.STICK),
            Material.CARROT, "焼きニンジン", 8, 1, 6, 0, 5, null, 0, false));
        DISHES.add(new Dish(List.of(Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.BOWL),
            Material.MUSHROOM_STEW, "ベリーボウル", 10, 1, 15, 0, 15, null, 0, false));
        DISHES.add(new Dish(List.of(Material.BREAD, Material.HONEY_BOTTLE),
            Material.BREAD, "ハチミツパン", 18, 3, 8, 2, 12, null, 0, false));
        DISHES.add(new Dish(List.of(Material.BREAD, Material.COOKED_BEEF, Material.CHARCOAL),
            Material.BREAD, "行軍食パック", 30, 22, 3, 6, -10, null, 0, false, "VACUUM"));

        // ===== 追加: 塩漬け・干物 =====
        DISHES.add(new Dish(List.of(Material.PORKCHOP, Material.SUGAR, Material.PAPER),
            Material.PORKCHOP, "塩漬け肉", 20, 18, 1, 12, -15, "cook_preserve_1", 0, false, "SALTED"));
        DISHES.add(new Dish(List.of(Material.COOKED_SALMON, Material.SUGAR, Material.PAPER),
            Material.COOKED_SALMON, "干し魚", 15, 15, 2, 6, -10, "cook_preserve_1", 0, false, "DRIED"));
        DISHES.add(new Dish(List.of(Material.COD, Material.SUGAR, Material.PAPER),
            Material.COD, "塩漬け魚", 12, 14, 2, 10, -8, "cook_preserve_1", 0, false, "SALTED"));

        // ===== 追加: ネザー食 =====
        DISHES.add(new Dish(List.of(Material.CRIMSON_FUNGUS, Material.CHARCOAL, Material.BONE_MEAL),
            Material.CRIMSON_FUNGUS, "クリムゾン焼き", 10, 5, 5, 0, 5, null, 0, false));
    }

    public static Inventory createGUI() {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        for (int i = 0; i < 54; i++) {
            if (i <= 3 || i == 8 || i == 17 || i == 18) continue;
            inv.setItem(i, createFiller());
        }
        inv.setItem(17, createCookButton());
        inv.setItem(18, createCloseButton());
        return inv;
    }

    public static void refreshDishes(Inventory inv, Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        List<Dish> regular = new ArrayList<>();
        for (Dish d : DISHES) {
            if (!d.perfect) regular.add(d);
        }
        int dishSlot = 19;
        for (Dish dish : regular) {
            if (dishSlot >= 54) break;
            if (dishSlot == 27) dishSlot = 28;
            inv.setItem(dishSlot, createDishDisplayItem(dish, data));
            dishSlot++;
        }
    }

    public static void open(Player player, Inventory inv) {
        player.openInventory(inv);
    }

    // 各料理を個別アイテムとして表示 (ホバーで材料リストを見れる)
    private static ItemStack createDishDisplayItem(Dish dish, PlayerData data) {
        Material mat = dish.baseMaterial;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        boolean unlocked = dish.isUnlocked(data);
        meta.setDisplayName(unlocked ? ChatColor.GOLD + dish.name : ChatColor.DARK_GRAY + "???");

        List<String> lore = new ArrayList<>();
        if (unlocked) {
            lore.add(ChatColor.GRAY + "材料: " + ChatColor.WHITE
                + dish.ingredients.stream().map(CookingStationMenu::getMaterialJP)
                    .reduce((a, b) -> a + " + " + b).orElse(""));
            lore.add("");
            lore.add(ChatColor.AQUA + "カロリー " + signed(dish.calories)
                + " タンパク質 " + signed(dish.protein)
                + " ビタミン " + signed(dish.vitamins)
                + " 塩分 " + signed(dish.salt)
                + " 水分 " + signed(dish.hydration));
        } else {
            lore.add(ChatColor.RED + "???");
            if (dish.requiredAbility != null) {
                lore.add(ChatColor.RED + "要スキル: " + abilityName(dish.requiredAbility));
            }
            if (dish.tasteRequired > 0) {
                lore.add(ChatColor.RED + "要味覚Lv: " + dish.tasteRequired);
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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

        // レシピに必要な素材をスロットから1個ずつ消費する
        for (Material ingredient : dish.ingredients) {
            if (!consumeIngredient(inv, ingredient)) {
                player.sendMessage(ChatColor.RED + "食材が足りない");
                return;
            }
        }

        int amount = 1;
        if (plugin.getToolManager().hasTool(player, "KNIFE")) {
            plugin.getToolManager().degradeTool(player, "KNIFE");
            amount = 2;
        }

        ItemStack dishItem = createDishItem(dish);
        dishItem.setAmount(amount);
        plugin.getFoodManager().markCooked(dishItem);
        if (dish.preservedType != null) {
            plugin.getFoodManager().markPreserved(dishItem, dish.preservedType);
        }
        if (dish.perfect) {
            plugin.getFoodManager().markPreserved(dishItem, "DRIED");
            player.sendMessage(ChatColor.GOLD + "パーフェクトレシピを発見した!");
        }
        player.getInventory().addItem(dishItem)
            .values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        plugin.getSkillManager().addXp(player, "COOKING", 8);
        updateResult(inv, player);
    }

    private static boolean consumeIngredient(Inventory inv, Material ingredient) {
        for (int i = 0; i < 4; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() == ingredient && it.getAmount() > 0) {
                if (it.getAmount() > 1) {
                    it.setAmount(it.getAmount() - 1);
                    inv.setItem(i, it);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private static Dish findDish(Inventory inv, Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        Set<Material> present = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                present.add(it.getType());
            }
        }
        for (Dish dish : DISHES) {
            if (!dish.isUnlocked(data)) continue;
            // 完全一致: 材料の数と種類が完全に一致する場合のみ
            if (present.size() != dish.ingredients.size()) continue;
            if (present.containsAll(dish.ingredients)) return dish;
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

private static ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + " ");
        item.setItemMeta(meta);
        return item;
    }

    public static String getMaterialJP(Material mat) {
        switch (mat) {
            case BEEF: return "生肉";
            case COOKED_BEEF: return "焼肉";
            case PORKCHOP: return "豚肉";
            case COOKED_PORKCHOP: return "焼豚";
            case CHICKEN: return "鶏肉";
            case COOKED_CHICKEN: return "焼鳥";
            case MUTTON: return "羊肉";
            case COD: return "鱈";
            case COOKED_COD: return "焼魚";
            case SALMON: return "鮭";
            case COOKED_SALMON: return "焼鮭";
            case POTATO: return "芋";
            case CARROT: return "ニンジン";
            case BEETROOT: return "ビート";
            case BOWL: return "ボウル";
            case WHEAT: return "小麦";
            case SUGAR: return "砂糖";
            case CHARCOAL: return "木炭";
            case STRING: return "糸";
            case GLASS_BOTTLE: return "瓶";
            case MUSHROOM_STEW: return "シチュー";
            case SWEET_BERRIES: return "ベリー";
            case GLOW_BERRIES: return "グロウベリー";
            case MELON_SLICE: return "スイカ";
            case APPLE: return "リンゴ";
            case GOLDEN_APPLE: return "金リンゴ";
            case HONEY_BOTTLE: return "蜂蜜";
            case COCOA_BEANS: return "ココア";
            case PAPER: return "紙";
            case RED_MUSHROOM: return "赤キノコ";
            case BONE_MEAL: return "骨粉";
            case EGG: return "卵";
            default: return mat.name().toLowerCase();
        }
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
