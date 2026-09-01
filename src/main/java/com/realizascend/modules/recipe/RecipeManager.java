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

    private static final List<ItemStack> RECIPES = new ArrayList<>();

    static {
        // ===== 工具 (ToolManager) =====
        addRecipe("ハンマー", Material.STONE_AXE, "金属加工に必要", "鉄インゴット×3 + 棒×1", "[鉄][鉄] / [鉄][棒]");
        addRecipe("ノミ", Material.STONE_SHOVEL, "石材加工に必要", "鉄インゴット×1 + 火打石×1 + 棒×1", "[鉄][ ] / [火][棒]");
        addRecipe("のこぎり", Material.WOODEN_AXE, "木材加工に必要", "鉄インゴット×1 + 骨×1 + 棒×1", "[鉄][ ] / [骨][棒]");
        addRecipe("包丁", Material.WOODEN_SWORD, "調理の品質が上がる", "鉄インゴット×1 + 革×1 + 棒×1", "[鉄][ ] / [革][棒]");
        addRecipe("針", Material.SHEARS, "縫製に必要", "鉄インゴット×1 + 糸×1", "[鉄] / [糸]");

        // ===== 医療 (MedicalManager) =====
        addRecipe("包帯", Material.PAPER, "出血・負傷を処置する", "紙×1 + 糸×1", "任意配置 (2個作れる)");
        addRecipe("添え木", Material.STICK, "骨折を固定する", "棒×1 + 糸×1", "任意配置");
        addRecipe("消毒液", Material.POTION, "感染の進行を抑える", "ガラス瓶×1 + 砂糖×1 + 木炭×1", "任意配置");
        addRecipe("抗生物質", Material.POTION, "感染症を完治させる", "ガラス瓶×1 + 砂糖×1 + 赤キノコ×1", "任意配置");

        // ===== 罠 (TrapManager) =====
        addRecipe("スパイクトラップ", Material.IRON_INGOT, "踏むと8ダメージ", "鉄塊×1 + 棒×1", "任意配置");
        addRecipe("ベアトラップ", Material.IRON_NUGGET, "踏むと6ダメージ+鈍足", "鉄インゴット×1 + 糸×1 + レッドストーン×1", "任意配置");
        addRecipe("毒針トラップ", Material.SPIDER_EYE, "踏むと毒", "鉄インゴット×1 + クモの目×1 + 棒×1", "任意配置");

        // ===== その他 =====
        addRecipe("浄水フィルター", Material.LEATHER, "生水を清浄水に浄化", "木炭×3 + 砂×3 + 砂利×3",
            "[木炭][木炭][木炭] / [砂][砂][砂] / [砂利][砂利][砂利]");
        addRecipe("塩", Material.SUGAR, "食品を塩漬けにする", "昆布×1", "任意配置 (4個作れる)");
        addRecipe("調理台", Material.CRAFTING_TABLE, "調理GUIを開く", "石×8 + 鉄インゴット×1",
            "[石][石][石] / [石][鉄][石] / [石][石][石]");
    }

    private static void addRecipe(String name, Material mat, String desc, String ingredients, String layout) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + desc);
        lore.add("");
        lore.add(ChatColor.GREEN + "材料: " + ChatColor.WHITE + ingredients);
        lore.add(ChatColor.GREEN + "配置: " + ChatColor.WHITE + layout);
        if (mat == Material.POTION) {
            ((PotionMeta) meta).setBasePotionData(new PotionData(PotionType.WATER));
        }
        item.setItemMeta(meta);
        RECIPES.add(item);
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
            inv.setItem(i, RECIPES.get(i).clone());
        }
        for (int i = RECIPES.size(); i < 45; i++) {
            inv.setItem(i, createFiller());
        }
        player.openInventory(inv);
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + " ");
        item.setItemMeta(meta);
        return item;
    }

    // 閲覧専用 (アイテムは取れない)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
    }
}