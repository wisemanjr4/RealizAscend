package com.realizascend.modules.skill;

import com.realizascend.data.PlayerData;
import com.realizascend.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillMenu {

    private static final String MAIN_TITLE = ChatColor.DARK_GRAY + "Skill Tree";
    private static final Map<String, Material> TREE_ICONS = Map.ofEntries(
        Map.entry("ENDURANCE", Material.IRON_BOOTS),
        Map.entry("STRENGTH", Material.IRON_SWORD),
        Map.entry("RESISTANCE", Material.SHIELD),
        Map.entry("METABOLISM", Material.GOLDEN_APPLE),
        Map.entry("COOKING", Material.FURNACE),
        Map.entry("MEDICAL", Material.POTION),
        Map.entry("BUILDING", Material.BRICKS),
        Map.entry("FARMING", Material.WHEAT),
        Map.entry("COMBAT", Material.DIAMOND_SWORD)
    );

    private static final Material ABILITY_UNLOCKED = Material.LIME_STAINED_GLASS_PANE;
    private static final Material ABILITY_LOCKED = Material.RED_STAINED_GLASS_PANE;
    private static final Material ABILITY_AFFORDABLE = Material.YELLOW_STAINED_GLASS_PANE;
    private static final Material CLOSE_BUTTON = Material.BARRIER;
    private static final Material EMPTY_SLOT = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material BACK_BUTTON = Material.ARROW;
    private static final Material POINTS_DISPLAY = Material.EMERALD;

    public static void openMainMenu(Player player, SkillManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, MAIN_TITLE);
        PlayerData data = manager.getPlugin().getDataManager().getData(player);

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, createEmptySlot());
        }

        String[] trees = SkillManager.SKILL_TREES;
        int[] slots = {10, 12, 14, 16, 19, 21, 23, 25, 31};
        for (int i = 0; i < trees.length && i < slots.length; i++) {
            String treeId = trees[i];
            ItemStack icon = createTreeIcon(treeId, data, manager);
            inv.setItem(slots[i], icon);
        }

        inv.setItem(49, createCloseButton());

        player.openInventory(inv);
    }

    public static void openSkillTree(Player player, String treeId, SkillManager manager) {
        String displayName = SkillManager.SKILL_TREE_DISPLAY_NAMES.getOrDefault(treeId, treeId);
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + displayName);
        PlayerData data = manager.getPlugin().getDataManager().getData(player);

        for (int i = 0; i < 53; i++) {
            inv.setItem(i, createEmptySlot());
        }

        List<SkillManager.SkillAbility> abilities = SkillManager.ABILITIES_BY_TREE.get(treeId);
        if (abilities != null) {
            int slot = 10;
            int count = 0;
            for (SkillManager.SkillAbility ability : abilities) {
                if (slot > 43) break;
                inv.setItem(slot, createAbilityItem(ability, data, manager));
                count++;
                slot++;
                if (count % 7 == 0) {
                    slot += 2;
                }
            }
        }

        inv.setItem(45, createBackButton());
        inv.setItem(49, createCloseButton());

        ItemStack pointsItem = new ItemStack(POINTS_DISPLAY);
        ItemMeta pointsMeta = pointsItem.getItemMeta();
        if (pointsMeta != null) {
            pointsMeta.setDisplayName(ChatColor.GREEN + "Skill Points: " + data.getSkillPoints());
            pointsItem.setItemMeta(pointsMeta);
        }
        inv.setItem(53, pointsItem);

        player.openInventory(inv);
    }

    public static void handleClick(InventoryClickEvent event, SkillManager manager) {
        String title = event.getView().getTitle();
        if (!title.startsWith(ChatColor.DARK_GRAY.toString())) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = manager.getPlugin().getDataManager().getData(player);
        ItemStack clicked = event.getCurrentItem();
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String itemName = ChatColor.stripColor(meta.getDisplayName());

        if (itemName.equals("Close")) {
            player.closeInventory();
            return;
        }

        if (itemName.equals("Back")) {
            openMainMenu(player, manager);
            return;
        }

        if (title.equals(MAIN_TITLE)) {
            for (String treeId : SkillManager.SKILL_TREES) {
                String displayName = SkillManager.SKILL_TREE_DISPLAY_NAMES.getOrDefault(treeId, treeId);
                String cleanName = ChatColor.stripColor(displayName);
                if (itemName.startsWith(cleanName)) {
                    openSkillTree(player, treeId, manager);
                    return;
                }
            }
            return;
        }

        String strippedTitle = ChatColor.stripColor(title);
        String treeId = null;
        for (String id : SkillManager.SKILL_TREES) {
            String dName = SkillManager.SKILL_TREE_DISPLAY_NAMES.getOrDefault(id, id);
            if (strippedTitle.equals(ChatColor.stripColor(dName))) {
                treeId = id;
                break;
            }
        }

        if (treeId != null) {
            SkillManager.SkillAbility ability = findAbilityByName(treeId, itemName, manager);
            if (ability != null) {
                manager.unlockAbility(player, ability);
            }
        }
    }

    private static SkillManager.SkillAbility findAbilityByName(String treeId, String displayName, SkillManager manager) {
        List<SkillManager.SkillAbility> abilities = SkillManager.ABILITIES_BY_TREE.get(treeId);
        if (abilities == null) return null;
        for (SkillManager.SkillAbility a : abilities) {
            if (ChatColor.stripColor(a.getName()).equals(displayName)) return a;
        }
        return null;
    }

    private static ItemStack createTreeIcon(String treeId, PlayerData data, SkillManager manager) {
        Material mat = TREE_ICONS.getOrDefault(treeId, Material.BOOK);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = SkillManager.SKILL_TREE_DISPLAY_NAMES.getOrDefault(treeId, treeId);
        meta.setDisplayName(ChatColor.GOLD + displayName);

        int level = data.getSkillLevel(treeId);
        double xp = data.getSkillXp(treeId);
        int xpForNext = manager.getXpForLevel(level);
        double progress = xpForNext > 0 ? Math.min(1.0, xp / xpForNext) : 1.0;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + level);
        lore.add("");
        lore.add(ChatColor.GRAY + "Progress to next level:");
        lore.add(MessageUtil.buildBar(progress, 20));
        lore.add(ChatColor.GRAY + String.format("%.0f / %d XP", xp, xpForNext));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to view abilities");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createAbilityItem(SkillManager.SkillAbility ability, PlayerData data, SkillManager manager) {
        int currentLevel = data.getAbilityLevel(ability.getId());
        boolean isUnlocked = currentLevel > 0;
        boolean canAfford = data.getSkillPoints() >= ability.getCost() && !isUnlocked;
        boolean prereqsMet = checkPrerequisites(ability, data, manager);

        Material mat;
        if (isUnlocked) {
            mat = ABILITY_UNLOCKED;
        } else if (canAfford && prereqsMet) {
            mat = ABILITY_AFFORDABLE;
        } else {
            mat = ABILITY_LOCKED;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String prefix = isUnlocked ? ChatColor.GREEN.toString() : canAfford && prereqsMet ? ChatColor.YELLOW.toString() : ChatColor.RED.toString();
        meta.setDisplayName(prefix + ability.getName());

        List<String> lore = new ArrayList<>();
        if (ability.getMaxLevel() > 1) {
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + currentLevel + "/" + ability.getMaxLevel());
        }
        lore.add(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + ability.getCost() + " point" + (ability.getCost() != 1 ? "s" : ""));
        lore.add("");
        lore.add(ChatColor.WHITE + ability.getDescription());
        lore.add("");

        if (!ability.getPrerequisites().isEmpty()) {
            lore.add(ChatColor.GRAY + "Requires:");
            for (String prereqId : ability.getPrerequisites()) {
                SkillManager.SkillAbility prereq = SkillManager.getAbility(prereqId);
                if (prereq != null) {
                    int prereqLevel = data.getAbilityLevel(prereqId);
                    boolean met = prereqLevel > 0;
                    lore.add((met ? ChatColor.GREEN : ChatColor.RED) + "  - " + prereq.getName());
                }
            }
            lore.add("");
        }

        if (ability.isExclusive()) {
            lore.add(ChatColor.LIGHT_PURPLE + "Exclusive: " + ability.getExclusiveGroup());
            lore.add(ChatColor.GRAY + "Cannot take other abilities in this group");
            lore.add("");
        }

        if (isUnlocked) {
            lore.add(ChatColor.GREEN + "UNLOCKED");
        } else if (canAfford && prereqsMet) {
            lore.add(ChatColor.YELLOW + "Click to unlock!");
        } else if (!prereqsMet) {
            lore.add(ChatColor.RED + "Prerequisites not met");
        } else {
            lore.add(ChatColor.RED + "Not enough skill points");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean checkPrerequisites(SkillManager.SkillAbility ability, PlayerData data, SkillManager manager) {
        if (ability.getPrerequisites().isEmpty()) return true;
        for (String prereqId : ability.getPrerequisites()) {
            if (data.getAbilityLevel(prereqId) <= 0) return false;
        }
        return true;
    }

    private static ItemStack createEmptySlot() {
        ItemStack item = new ItemStack(EMPTY_SLOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createCloseButton() {
        ItemStack item = new ItemStack(CLOSE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Close");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createBackButton() {
        ItemStack item = new ItemStack(BACK_BUTTON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Back");
            item.setItemMeta(meta);
        }
        return item;
    }
}
