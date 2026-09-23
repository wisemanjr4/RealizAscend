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
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.io.IOException;
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
    private final NamespacedKey lockStateKey;

    private final Map<Location, UUID> locked = new HashMap<>();
    private final Map<Location, String> ownerNames = new HashMap<>();
    private File locksFile;

    public ChestLockManager(RealizAscend plugin) {
        super(plugin);
        keyFlag = new NamespacedKey(plugin, "chestlock_key");
        ownerKey = new NamespacedKey(plugin, "chestlock_owner");
        recipeKey = new NamespacedKey(plugin, "chestlock_key_recipe");
        lockStateKey = new NamespacedKey(plugin, "chestlock_locked");
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
        locksFile = new File(plugin.getDataFolder(), "locks.yml");
        loadLocks();
    }

    @Override
    public void onDisable() {
        saveLocks();
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
        // ファイルに無い場合、Tile PDCから復元 (再起動直後の同期用)
        for (Location loc : getChestLocations(block)) {
            if (loc.getWorld() == null) continue;
            Block b = loc.getBlock();
            if (b.getState() instanceof TileState tile) {
                String s = tile.getPersistentDataContainer().get(lockStateKey, PersistentDataType.STRING);
                if (s != null) {
                    String[] parts = s.split("\\|", 2);
                    try {
                        UUID o = UUID.fromString(parts[0]);
                        locked.put(loc, o);
                        ownerNames.put(loc, parts.length > 1 ? parts[1] : "?");
                        return o;
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
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

    private void markLockTile(Location loc, UUID owner, String name) {
        if (loc.getWorld() == null) return;
        Block b = loc.getBlock();
        if (b.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(lockStateKey, PersistentDataType.STRING,
                owner.toString() + "|" + name);
            tile.update(true, false);
        }
    }

    private void unmarkLockTile(Location loc) {
        if (loc.getWorld() == null) return;
        Block b = loc.getBlock();
        if (b.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().remove(lockStateKey);
            tile.update(true, false);
        }
    }

    private String lockToString(Location loc, UUID owner, String name) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + ","
            + loc.getBlockZ() + "," + owner.toString() + "," + name;
    }

    private void saveLocks() {
        if (locksFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Map.Entry<Location, UUID> e : locked.entrySet()) {
            Location loc = e.getKey();
            if (loc.getWorld() == null) continue;
            list.add(lockToString(loc, e.getValue(), ownerNames.getOrDefault(loc, "?")));
        }
        yaml.set("locks", list);
        try {
            yaml.save(locksFile);
        } catch (IOException e) {
            plugin.getLogger().warning("チェストロックの保存に失敗: " + e.getMessage());
        }
    }

    private void loadLocks() {
        locked.clear();
        ownerNames.clear();
        if (locksFile == null || !locksFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(locksFile);
        int count = 0;
        for (String s : yaml.getStringList("locks")) {
            String[] p = s.split(",", 6);
            if (p.length != 6) continue;
            org.bukkit.World world = Bukkit.getWorld(p[0]);
            if (world == null) continue;
            try {
                Location loc = new Location(world, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                UUID owner = UUID.fromString(p[4]);
                locked.put(loc, owner);
                ownerNames.put(loc, p[5]);
                count++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("チェストロックを " + count + " 件復元した。");
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
                    markLockTile(loc, player.getUniqueId(), player.getName());
                }
                saveLocks();
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
                unmarkLockTile(loc);
            }
            saveLocks();
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
