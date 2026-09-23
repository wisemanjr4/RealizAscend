package com.realizascend.modules.chestlock;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChestLockManager extends RealizModule implements Listener {

    private final NamespacedKey keyFlag;
    private final NamespacedKey ownerKey;
    private final NamespacedKey recipeKey;

    private final Map<Location, UUID> locked = new HashMap<>();
    private final Map<Location, String> ownerNames = new HashMap<>();

    public ChestLockManager(RealizAscend plugin) {
        super(plugin);
        keyFlag = new NamespacedKey(plugin, "chestlock_key");
        ownerKey = new NamespacedKey(plugin, "chestlock_owner");
        recipeKey = new NamespacedKey(plugin, "chestlock_key_recipe");
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            Bukkit.addRecipe(new ShapedRecipe(recipeKey, createTemplate())
                .shape("G", "S")
                .setIngredient('G', Material.GOLD_NUGGET)
                .setIngredient('S', Material.STICK));
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        List<NamespacedKey> toRemove = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe r = iter.next();
            if (r instanceof org.bukkit.Keyed k && k.getKey().equals(recipeKey)) {
                toRemove.add(recipeKey);
                break;
            }
        }
        for (NamespacedKey k : toRemove) {
            Bukkit.removeRecipe(k);
        }
        locked.clear();
        ownerNames.clear();
    }

    private ItemStack createTemplate() {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "南京錠の鍵");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "チェストに鍵をかけられる");
        lore.add(ChatColor.GRAY + "スニーク+右クリックで施錠/開錠");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(keyFlag, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKey(Player owner) {
        ItemStack item = createTemplate();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "持ち主: " + owner.getName());
        lore.add(ChatColor.GRAY + "スニーク+右クリックで施錠/開錠");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isKey(ItemStack item) {
        return item != null && item.getType() == Material.TRIPWIRE_HOOK
            && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(keyFlag, PersistentDataType.INTEGER);
    }

    public UUID getKeyOwner(ItemStack item) {
        if (!isKey(item)) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean hasMatchingKey(Player player, UUID lockOwner) {
        if (lockOwner == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            UUID o = getKeyOwner(item);
            if (o != null && o.equals(lockOwner)) return true;
        }
        UUID off = getKeyOwner(player.getInventory().getItemInOffHand());
        if (off != null && off.equals(lockOwner)) return true;
        UUID hand = getKeyOwner(player.getInventory().getItemInMainHand());
        return hand != null && hand.equals(lockOwner);
    }

    private boolean isContainer(Block block) {
        Material t = block.getType();
        return t == Material.CHEST || t == Material.TRAPPED_CHEST || t == Material.BARREL;
    }

    private Location norm(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private List<Location> getChestLocations(Block block) {
        List<Location> list = new ArrayList<>();
        list.add(norm(block));
        Material t = block.getType();
        if (t == Material.CHEST || t == Material.TRAPPED_CHEST) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block rel = block.getRelative(face);
                if (rel.getType() == t) {
                    list.add(norm(rel));
                }
            }
        }
        return list;
    }

    private UUID getEffectiveLock(Block block) {
        for (Location loc : getChestLocations(block)) {
            UUID o = locked.get(loc);
            if (o != null) return o;
        }
        return null;
    }

    private String getEffectiveOwnerName(Block block) {
        for (Location loc : getChestLocations(block)) {
            String n = ownerNames.get(loc);
            if (n != null) return n;
        }
        return "?";
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        Recipe recipe = event.getRecipe();
        if (recipe instanceof org.bukkit.Keyed k && k.getKey().equals(recipeKey)) {
            event.getInventory().setResult(createKey(player));
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe().getResult();
        if (!isKey(result)) return;
        ItemStack current = event.getCurrentItem();
        if (current != null && isKey(current) && getKeyOwner(current) == null) {
            event.setCurrentItem(createKey(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !isContainer(block)) return;
        Player player = event.getPlayer();

        UUID lockOwner = getEffectiveLock(block);
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean sneaking = player.isSneaking();

        if (lockOwner == null) {
            if (sneaking && isKey(hand)) {
                UUID keyOwner = getKeyOwner(hand);
                if (keyOwner == null) {
                    ItemStack fixed = createKey(player);
                    fixed.setAmount(hand.getAmount());
                    player.getInventory().setItemInMainHand(fixed);
                    keyOwner = player.getUniqueId();
                }
                if (!keyOwner.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "自分の鍵でしか施錠できない!");
                    event.setCancelled(true);
                    return;
                }
                for (Location loc : getChestLocations(block)) {
                    locked.put(loc, player.getUniqueId());
                    ownerNames.put(loc, player.getName());
                }
                player.sendMessage(ChatColor.GREEN + "鍵をかけた!");
                event.setCancelled(true);
            }
            return;
        }

        if (sneaking && isKey(hand) && player.getUniqueId().equals(lockOwner)
            && lockOwner.equals(getKeyOwner(hand))) {
            for (Location loc : getChestLocations(block)) {
                locked.remove(loc);
                ownerNames.remove(loc);
            }
            player.sendMessage(ChatColor.GREEN + "鍵を開けた!");
            event.setCancelled(true);
            return;
        }

        if (hasMatchingKey(player, lockOwner)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "鍵がかかっている (" + getEffectiveOwnerName(block) + "の鍵)");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isContainer(block)) return;
        if (getEffectiveLock(block) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "鍵がかかっているため破壊できない!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> isContainer(b) && getEffectiveLock(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> isContainer(b) && getEffectiveLock(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isContainer(b) && getEffectiveLock(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isContainer(b) && getEffectiveLock(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        InventoryHolder holder = event.getSource().getHolder();
        if (holder instanceof org.bukkit.block.BlockState state) {
            Block b = state.getBlock();
            if (isContainer(b) && getEffectiveLock(b) != null) {
                event.setCancelled(true);
            }
        }
    }
}
