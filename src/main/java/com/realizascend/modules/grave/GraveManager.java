package com.realizascend.modules.grave;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GraveManager extends RealizModule implements Listener {

    private final Map<UUID, List<GraveData>> graves = new HashMap<>();
    private final Map<Inventory, GraveData> openGraves = new HashMap<>();
    private final Set<Location> graveBlocks = new HashSet<>();
    private BukkitRunnable cleanupTask;

    public static class GraveData {
        public final UUID ownerUUID;
        public final String ownerName;
        public final Location stoneLocation;
        public final long deathTime;
        public ItemStack[] mainContents;
        public ItemStack[] armorContents;
        public ItemStack offhand;

        public GraveData(UUID ownerUUID, String ownerName, Location stoneLocation, long deathTime,
                         ItemStack[] mainContents, ItemStack[] armorContents, ItemStack offhand) {
            this.ownerUUID = ownerUUID;
            this.ownerName = ownerName;
            this.stoneLocation = stoneLocation;
            this.deathTime = deathTime;
            this.mainContents = mainContents;
            this.armorContents = armorContents;
            this.offhand = offhand;
        }
    }

    public GraveManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long durationMs = plugin.getConfigManager().corpseDurationDays * 24000L * 50L;
                long now = System.currentTimeMillis();
                List<GraveData> toExpire = new ArrayList<>();
                for (List<GraveData> list : graves.values()) {
                    for (GraveData gd : list) {
                        if (now - gd.deathTime >= durationMs) {
                            toExpire.add(gd);
                        }
                    }
                }
                for (GraveData gd : toExpire) {
                    if (!plugin.getConfigManager().corpseRemoveOnExpire) {
                        dropContents(gd);
                    }
                    removeGrave(gd);
                }
            }
        };
        cleanupTask.runTaskTimer(plugin, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        for (GraveData gd : collectAll()) {
            removeBlocks(gd);
        }
        graves.clear();
        openGraves.clear();
        graveBlocks.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final Location deathLoc = player.getLocation().clone();

        // 死亡時点のインベントリ配置をそのまま保存
        final ItemStack[] mainContents = player.getInventory().getContents();
        final ItemStack[] armorContents = player.getInventory().getArmorContents();
        final ItemStack offhand = player.getInventory().getItemInOffHand();

        // バニラのドロップを抑止 (アイテムは墓に保存される)
        event.getDrops().clear();

        // 墓の設置は次のティックに遅延 (死亡イベント中のブロック操作はハングの原因)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (deathLoc.getWorld() == null) return;
            Location stone = findPlaceable(deathLoc);
            if (stone == null) return;

            stone.getBlock().setType(Material.SMOOTH_STONE, false);
            Location headLoc = stone.clone().add(0, 1, 0);
            Block headBlock = headLoc.getBlock();
            if (headBlock.getType() == Material.AIR || headBlock.getType() == Material.WATER) {
                headBlock.setType(Material.PLAYER_HEAD, false);
                if (headBlock.getState() instanceof Skull skull) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                    skull.update(true, false);
                }
            }

            graveBlocks.add(stone);
            graveBlocks.add(headLoc);
            graves.computeIfAbsent(uuid, k -> new ArrayList<>()).add(new GraveData(
                uuid, name, stone, System.currentTimeMillis(),
                mainContents, armorContents, offhand
            ));
        });
    }

    private Location findPlaceable(Location base) {
        if (base.getWorld() == null) return null;
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                for (int dy = 0; dy >= -2; dy--) {
                    Location loc = base.clone().add(dx, dy, dz);
                    Material type = loc.getBlock().getType();
                    if (type == Material.AIR || type == Material.WATER || type == Material.LAVA) {
                        return loc;
                    }
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onGraveInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        GraveData grave = findGraveAt(block.getLocation());
        if (grave == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // インベントリが空の本人 → 配置を維持してそのまま一括回収
        if (player.getUniqueId().equals(grave.ownerUUID) && isEmptyInventory(player)) {
            restoreToPlayer(player, grave);
            removeGrave(grave);
            player.sendMessage(ChatColor.GREEN + "墓から持ち物を回収した!");
            return;
        }

        openGraveGui(player, grave);
    }

    private GraveData findGraveAt(Location loc) {
        for (List<GraveData> list : graves.values()) {
            for (GraveData gd : list) {
                if (gd.stoneLocation.equals(loc) || gd.stoneLocation.clone().add(0, 1, 0).equals(loc)) {
                    return gd;
                }
            }
        }
        return null;
    }

    private boolean isEmptyInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off == null || off.getType() == Material.AIR;
    }

    private void restoreToPlayer(Player player, GraveData gd) {
        player.getInventory().setContents(gd.mainContents.clone());
        player.getInventory().setArmorContents(gd.armorContents.clone());
        ItemStack off = gd.offhand;
        player.getInventory().setItemInOffHand(off != null ? off.clone() : null);
    }

    private void openGraveGui(Player player, GraveData gd) {
        Inventory inv = Bukkit.createInventory(null, 45, ChatColor.DARK_GRAY + gd.ownerName + "の墓");

        for (int i = 0; i < 36; i++) {
            if (gd.mainContents[i] != null) {
                inv.setItem(i, gd.mainContents[i].clone());
            }
        }
        for (int i = 0; i < 4; i++) {
            if (gd.armorContents[i] != null) {
                inv.setItem(36 + i, gd.armorContents[i].clone());
            }
        }
        if (gd.offhand != null) {
            inv.setItem(40, gd.offhand.clone());
        }
        for (int i = 41; i < 45; i++) {
            inv.setItem(i, createFiller());
        }

        openGraves.put(inv, gd);
        player.openInventory(inv);
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + " ");
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onGraveClose(InventoryCloseEvent event) {
        GraveData gd = openGraves.remove(event.getInventory());
        if (gd == null) return;

        Inventory inv = event.getInventory();
        ItemStack[] main = new ItemStack[36];
        for (int i = 0; i < 36; i++) main[i] = inv.getItem(i);
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) armor[i] = inv.getItem(36 + i);
        gd.mainContents = main;
        gd.armorContents = armor;
        gd.offhand = inv.getItem(40);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (graveBlocks.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (graveBlocks.contains(event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> graveBlocks.contains(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> graveBlocks.contains(b.getLocation()));
    }

    private void dropContents(GraveData gd) {
        World world = gd.stoneLocation.getWorld();
        if (world == null) return;
        Location dropAt = gd.stoneLocation.clone().add(0.5, 0.5, 0.5);
        for (ItemStack item : gd.mainContents) {
            if (item != null && item.getType() != Material.AIR) {
                world.dropItemNaturally(dropAt, item);
            }
        }
        for (ItemStack item : gd.armorContents) {
            if (item != null && item.getType() != Material.AIR) {
                world.dropItemNaturally(dropAt, item);
            }
        }
        if (gd.offhand != null && gd.offhand.getType() != Material.AIR) {
            world.dropItemNaturally(dropAt, gd.offhand);
        }
    }

    private void removeGrave(GraveData gd) {
        removeBlocks(gd);
        List<GraveData> list = graves.get(gd.ownerUUID);
        if (list != null) {
            list.remove(gd);
            if (list.isEmpty()) graves.remove(gd.ownerUUID);
        }
    }

    private void removeBlocks(GraveData gd) {
        if (gd.stoneLocation.getWorld() != null) {
            gd.stoneLocation.getBlock().setType(Material.AIR, false);
            Location head = gd.stoneLocation.clone().add(0, 1, 0);
            if (head.getBlock().getType() == Material.PLAYER_HEAD) {
                head.getBlock().setType(Material.AIR, false);
            }
        }
        graveBlocks.remove(gd.stoneLocation);
        graveBlocks.remove(gd.stoneLocation.clone().add(0, 1, 0));
    }

    private List<GraveData> collectAll() {
        List<GraveData> all = new ArrayList<>();
        for (List<GraveData> list : graves.values()) {
            all.addAll(list);
        }
        return all;
    }
}