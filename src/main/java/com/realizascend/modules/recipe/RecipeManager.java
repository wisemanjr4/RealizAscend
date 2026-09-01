package com.realizascend.modules.recipe;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

public class RecipeManager extends RealizModule implements Listener {

    public static final String TITLE = ChatColor.GOLD + "レシピ";

    private static final List<RecipeData> RECIPES = new ArrayList<>();

    private static class RecipeData {
        final ItemStack result;
        final Material[] grid;   // 9 (3x3)。null=空
        final String ingredients;
        final boolean shapeless;

        RecipeData(ItemStack result, Material[] grid, String ingredients, boolean shapeless) {
            this.result = result;
            this.grid = grid;
            this.ingredients = ingredients;
            this.shapeless = shapeless;
        }
    }

    static {
        // ===== 工具 (ToolManager) =====
        addRecipe("ハンマー", Material.STONE_AXE, "金属加工に必要", "鉄インゴット×3 + 棒×1", false,
            new Material[]{Material.IRON_INGOT, Material.IRON_INGOT, null,
                           Material.IRON_INGOT, Material.STICK, null,
                           null, null, null});
        addRecipe("ノミ", Material.STONE_SHOVEL, "石材加工に必要", "鉄インゴット×1 + 火打石×1 + 棒×1", false,
            new Material[]{Material.IRON_INGOT, null, null,
                           Material.FLINT, Material.STICK, null,
                           null, null, null});
        addRecipe("のこぎり", Material.WOODEN_AXE, "木材加工に必要", "鉄インゴット×1 + 骨×1 + 棒×1", false,
            new Material[]{Material.IRON_INGOT, null, null,
                           Material.BONE, Material.STICK, null,
                           null, null, null});
        addRecipe("包丁", Material.WOODEN_SWORD, "調理の品質が上がる", "鉄インゴット×1 + 革×1 + 棒×1", false,
            new Material[]{Material.IRON_INGOT, null, null,
                           Material.LEATHER, Material.STICK, null,
                           null, null, null});
        addRecipe("針", Material.SHEARS, "縫製に必要", "鉄インゴット×1 + 糸×1", false,
            new Material[]{Material.IRON_INGOT, null, null,
                           Material.STRING, null, null,
                           null, null, null});

        // ===== 医療 (MedicalManager) =====
        addRecipe("包帯", Material.PAPER, "出血・負傷を処置する", "紙×1 + 糸×1 → 包帯×2", true,
            new Material[]{Material.PAPER, Material.STRING, null, null, null, null, null, null, null});
        addRecipe("添え木", Material.STICK, "骨折を固定する", "棒×1 + 糸×1", true,
            new Material[]{Material.STICK, Material.STRING, null, null, null, null, null, null, null});
        addRecipe("消毒液", Material.POTION, "感染の進行を抑える", "ガラス瓶×1 + 砂糖×1 + 木炭×1", true,
            new Material[]{Material.GLASS_BOTTLE, Material.SUGAR, Material.CHARCOAL, null, null, null, null, null, null});
        addRecipe("抗生物質", Material.POTION, "感染症を完治させる", "ガラス瓶×1 + 砂糖×1 + 赤キノコ×1", true,
            new Material[]{Material.GLASS_BOTTLE, Material.SUGAR, Material.RED_MUSHROOM, null, null, null, null, null, null});

        // ===== 罠 (TrapManager) =====
        addRecipe("スパイクトラップ", Material.IRON_INGOT, "踏むと8ダメージ", "鉄塊×1 + 棒×1", true,
            new Material[]{Material.IRON_NUGGET, Material.STICK, null, null, null, null, null, null, null});
        addRecipe("ベアトラップ", Material.IRON_NUGGET, "踏むと6ダメージ+鈍足", "鉄インゴット×1 + 糸×1 + レッドストーン×1", true,
            new Material[]{Material.IRON_INGOT, Material.STRING, Material.REDSTONE, null, null, null, null, null, null});
        addRecipe("毒針トラップ", Material.SPIDER_EYE, "踏むと毒", "鉄インゴット×1 + クモの目×1 + 棒×1", true,
            new Material[]{Material.IRON_INGOT, Material.SPIDER_EYE, Material.STICK, null, null, null, null, null, null});

        // ===== その他 =====
        addRecipe("浄水フィルター", Material.LEATHER, "生水を清浄水に浄化", "木炭×3 + 砂×3 + 砂利×3", false,
            new Material[]{Material.CHARCOAL, Material.CHARCOAL, Material.CHARCOAL,
                           Material.SAND, Material.SAND, Material.SAND,
                           Material.GRAVEL, Material.GRAVEL, Material.GRAVEL});
        addRecipe("塩", Material.SUGAR, "食品を塩漬けにする", "昆布×1 → 塩×4", true,
            new Material[]{Material.KELP, null, null, null, null, null, null, null, null});
        addRecipe("調理台", Material.CRAFTING_TABLE, "調理GUIを開く", "石×8 + 鉄インゴット×1", false,
            new Material[]{Material.STONE, Material.STONE, Material.STONE,
                           Material.STONE, Material.IRON_INGOT, Material.STONE,
                           Material.STONE, Material.STONE, Material.STONE});
    }

    private static void addRecipe(String name, Material mat, String desc, String ingredients, boolean shapeless, Material[] grid) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + desc);
        lore.add("");
        lore.add(ChatColor.YELLOW + "クリックでレシピを見る");
        if (mat == Material.POTION) {
            ((PotionMeta) meta).setBasePotionData(new PotionData(PotionType.WATER));
        }
        item.setItemMeta(meta);
        RECIPES.add(new RecipeData(item, grid, ingredients, shapeless));
    }

    public RecipeManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
    }

    public void openRecipeMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE);
        for (int i = 0; i < RECIPES.size() && i < 45; i++) {
            inv.setItem(i, RECIPES.get(i).result.clone());
        }
        for (int i = RECIPES.size(); i < 45; i++) {
            inv.setItem(i, createFiller());
        }
        player.openInventory(inv);
    }

    private void openRecipeDetail(Player player, RecipeData data) {
        Inventory inv = Bukkit.createInventory(null, 45, ChatColor.GOLD + data.result.getItemMeta().getDisplayName() + " のレシピ");
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, createFiller());
        }

        // 3x3 クラフトグリッド (スロット0-8)
        for (int i = 0; i < 9; i++) {
            Material m = data.grid[i];
            if (m != null) {
                inv.setItem(i, new ItemStack(m));
            }
        }

        // 矢印 + 結果
        inv.setItem(12, createArrow());
        inv.setItem(14, data.result.clone());

        // 説明 (スロット20)
        ItemStack desc = new ItemStack(Material.BOOK);
        ItemMeta dm = desc.getItemMeta();
        dm.setDisplayName(ChatColor.GOLD + data.result.getItemMeta().getDisplayName());
        List<String> dl = new ArrayList<>();
        dl.add(ChatColor.GRAY + (data.shapeless ? "任意配置のレシピ" : "配置指定のレシピ"));
        dl.add("");
        dl.add(ChatColor.GREEN + "材料: " + ChatColor.WHITE + data.ingredients);
        dm.setLore(dl);
        desc.setItemMeta(dm);
        inv.setItem(20, desc);

        // 戻るボタン (スロット40)
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.YELLOW + "戻る");
        back.setItemMeta(bm);
        inv.setItem(40, back);

        player.openInventory(inv);
    }

    private ItemStack createArrow() {
        ItemStack arrow = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "→");
        arrow.setItemMeta(meta);
        return arrow;
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + " ");
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.equals(TITLE)) {
            // メインメニュー
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot >= 0 && slot < RECIPES.size()) {
                Bukkit.getScheduler().runTask(plugin, () -> openRecipeDetail(player, RECIPES.get(slot)));
            }
            return;
        }

        if (title.startsWith(ChatColor.GOLD.toString())) {
            // 詳細画面
            event.setCancelled(true);
            if (event.getSlot() == 40) {
                Bukkit.getScheduler().runTask(plugin, () -> openRecipeMenu(player));
            }
        }
    }
}