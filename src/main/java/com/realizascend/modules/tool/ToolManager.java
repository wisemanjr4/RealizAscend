package com.realizascend.modules.tool;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ToolManager extends RealizModule implements Listener {

    public static final int TOOL_MAX_DURABILITY = 50;

    private final NamespacedKey hammerKey;
    private final NamespacedKey chiselKey;
    private final NamespacedKey sawKey;
    private final NamespacedKey knifeKey;
    private final NamespacedKey needleKey;

    private final NamespacedKey hammerRecipeKey;
    private final NamespacedKey chiselRecipeKey;
    private final NamespacedKey sawRecipeKey;
    private final NamespacedKey knifeRecipeKey;
    private final NamespacedKey needleRecipeKey;

    public ToolManager(RealizAscend plugin) {
        super(plugin);
        hammerKey = new NamespacedKey(plugin, "tool_hammer");
        chiselKey = new NamespacedKey(plugin, "tool_chisel");
        sawKey = new NamespacedKey(plugin, "tool_saw");
        knifeKey = new NamespacedKey(plugin, "tool_knife");
        needleKey = new NamespacedKey(plugin, "tool_needle");
        hammerRecipeKey = new NamespacedKey(plugin, "hammer_recipe");
        chiselRecipeKey = new NamespacedKey(plugin, "chisel_recipe");
        sawRecipeKey = new NamespacedKey(plugin, "saw_recipe");
        knifeRecipeKey = new NamespacedKey(plugin, "knife_recipe");
        needleRecipeKey = new NamespacedKey(plugin, "needle_recipe");
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerRecipes();
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        removeRecipes();
    }

    private void registerRecipes() {
        // Hammer: [IRON_INGOT] [IRON_INGOT] / [IRON_INGOT] [STICK]
        tryAdd(new ShapedRecipe(hammerRecipeKey, createTool(Material.IRON_AXE, hammerKey, ChatColor.GRAY + "ハンマー", "金属加工に必要"))
            .shape("II", "IS")
            .setIngredient('I', Material.IRON_INGOT)
            .setIngredient('S', Material.STICK));

        tryAdd(new ShapedRecipe(chiselRecipeKey, createTool(Material.STONE_SHOVEL, chiselKey, ChatColor.GRAY + "ノミ", "石材加工に必要"))
            .shape("I", "S")
            .setIngredient('I', Material.IRON_INGOT)
            .setIngredient('S', Material.STICK));

        tryAdd(new ShapedRecipe(sawRecipeKey, createTool(Material.GOLDEN_AXE, sawKey, ChatColor.GRAY + "のこぎり", "木材加工に必要"))
            .shape("I", "S")
            .setIngredient('I', Material.IRON_INGOT)
            .setIngredient('S', Material.STICK));

        tryAdd(new ShapedRecipe(knifeRecipeKey, createTool(Material.IRON_SWORD, knifeKey, ChatColor.GRAY + "包丁", "調理の品質が上がる"))
            .shape("I", "S")
            .setIngredient('I', Material.IRON_INGOT)
            .setIngredient('S', Material.STICK));

        tryAdd(new ShapedRecipe(needleRecipeKey, createTool(Material.SHEARS, needleKey, ChatColor.GRAY + "針", "縫製に必要"))
            .shape("I", "T")
            .setIngredient('I', Material.IRON_INGOT)
            .setIngredient('T', Material.STRING));
    }

    private void tryAdd(Recipe recipe) {
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
        }
    }

    private void removeRecipes() {
        NamespacedKey[] keys = {hammerRecipeKey, chiselRecipeKey, sawRecipeKey, knifeRecipeKey, needleRecipeKey};
        List<NamespacedKey> toRemove = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof org.bukkit.Keyed k) {
                for (NamespacedKey key : keys) {
                    if (k.getKey().equals(key)) {
                        toRemove.add(key);
                    }
                }
            }
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
    }

    private ItemStack createTool(Material base, NamespacedKey key, String displayName, String loreLine) {
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + loreLine);
        lore.add(ChatColor.GRAY + "耐久: " + TOOL_MAX_DURABILITY + "/" + TOOL_MAX_DURABILITY);
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, TOOL_MAX_DURABILITY);
        item.setItemMeta(meta);
        return item;
    }

    private NamespacedKey getKeyForType(String type) {
        switch (type) {
            case "HAMMER": return hammerKey;
            case "CHISEL": return chiselKey;
            case "SAW": return sawKey;
            case "KNIFE": return knifeKey;
            case "NEEDLE": return needleKey;
            default: return null;
        }
    }

    public boolean hasTool(Player player, String type) {
        NamespacedKey key = getKeyForType(type);
        if (key == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) {
                return true;
            }
        }
        return false;
    }

    public void degradeTool(Player player, String type) {
        NamespacedKey key = getKeyForType(type);
        if (key == null) return;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            Integer durability = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            if (durability == null) continue;

            durability--;
            if (durability <= 0) {
                player.getInventory().setItem(i, null);
                player.sendMessage(ChatColor.RED + "工具が壊れた!");
            } else {
                meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, durability);
                List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : new ArrayList<>());
                if (!lore.isEmpty()) {
                    lore.set(lore.size() - 1, ChatColor.GRAY + "耐久: " + durability + "/" + TOOL_MAX_DURABILITY);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return;
        }
    }

    // 工具の残り耐久を返す (0 = 工具なし)
    public int getToolDurability(Player player, String type) {
        NamespacedKey key = getKeyForType(type);
        if (key == null) return 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                Integer durability = item.getItemMeta().getPersistentDataContainer()
                    .get(key, PersistentDataType.INTEGER);
                if (durability != null) {
                    return durability;
                }
            }
        }
        return 0;
    }

    public void onCookingExtract(Player player) {
        if (hasTool(player, "KNIFE")) {
            degradeTool(player, "KNIFE");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        if (result == null) return;
        if (isToolItem(result)) return;

        String required = requiredTool(result.getType());
        if (required == null) return;

        if (!hasTool(player, required)) {
            event.getInventory().setResult(null);
            player.sendMessage(ChatColor.RED + "クラフトには" + toolDisplayName(required) + "が必要");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.isCancelled()) return;
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        if (result == null) return;
        if (isToolItem(result)) return;

        String required = requiredTool(result.getType());
        if (required != null && hasTool(player, required)) {
            degradeTool(player, required);
        }
    }

    private boolean isToolItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(hammerKey, PersistentDataType.INTEGER)
            || item.getItemMeta().getPersistentDataContainer().has(chiselKey, PersistentDataType.INTEGER)
            || item.getItemMeta().getPersistentDataContainer().has(sawKey, PersistentDataType.INTEGER)
            || item.getItemMeta().getPersistentDataContainer().has(knifeKey, PersistentDataType.INTEGER)
            || item.getItemMeta().getPersistentDataContainer().has(needleKey, PersistentDataType.INTEGER);
    }

    private String toolDisplayName(String type) {
        switch (type) {
            case "HAMMER": return "ハンマー";
            case "CHISEL": return "ノミ";
            case "SAW": return "のこぎり";
            case "NEEDLE": return "針";
            default: return type;
        }
    }

    private String requiredTool(Material mat) {
        String name = mat.name();
        // Metal family first: tools/armor and metal building need HAMMER; basic metal needs none
        if (name.startsWith("IRON_") || name.startsWith("GOLDEN_")
            || name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")) {
            if (isToolOrArmor(mat)) return "HAMMER";
            if (isMetalBuilding(mat)) return "HAMMER";
            return null;
        }
        if (isWoodBuilding(mat)) return "SAW";
        if (isStoneBuilding(mat)) return "CHISEL";
        if (name.startsWith("LEATHER_")) return "NEEDLE";
        return null;
    }

    private boolean isToolOrArmor(Material mat) {
        String name = mat.name();
        return name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
            || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
            || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private boolean isMetalBuilding(Material mat) {
        String name = mat.name();
        return name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("BARS")
            || name.contains("CAULDRON") || name.contains("BUCKET") || name.contains("SHEARS")
            || name.contains("HOPPER") || name.contains("MINE_CART") || name.contains("RAIL")
            || name.contains("CHAIN") || name.contains("ANVIL") || name.contains("LANTERN");
    }

    private boolean isWoodBuilding(Material mat) {
        String name = mat.name();
        if (!name.contains("WOOD") && !name.contains("PLANK") && !name.contains("FENCE")
            && !name.contains("DOOR") && !name.contains("TRAPDOOR") && !name.contains("GATE")
            && !name.contains("STAIR") && !name.contains("SLAB") && !name.contains("BOAT")
            && !name.contains("SIGN") && !name.contains("LADDER") && !name.contains("BOWL")
            && !name.contains("ITEM_FRAME") && !name.contains("ARMOR_STAND")) {
            return false;
        }
        // Basic items don't need a tool (crafting table, chest, sticks, planks, tools)
        if (mat == Material.CRAFTING_TABLE || mat == Material.CHEST
            || name.endsWith("_PLANKS") || name.endsWith("_WOOD")
            || name.endsWith("_LOG") || name.endsWith("_STEM")) {
            return false;
        }
        return true;
    }

    private boolean isStoneBuilding(Material mat) {
        String name = mat.name();
        return (name.contains("STONE") || name.contains("BRICK") || name.contains("SANDSTONE"))
            && (name.contains("STAIR") || name.contains("SLAB") || name.contains("WALL")
            || name.contains("BRICK") || name.contains("SMOOTH") || name.contains("POLISHED")
            || name.contains("CHISELED"));
    }
}
