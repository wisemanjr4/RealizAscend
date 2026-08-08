package com.realizascend.modules.world;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager extends RealizModule implements Listener {

    private final Map<Location, Long> torchTimers = new ConcurrentHashMap<>();
    private final Set<Location> gravityImmuneBlocks = ConcurrentHashMap.newKeySet();
    private BukkitRunnable torchCheckTask;
    private BukkitRunnable gravityCleanupTask;

    public WorldManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        torchCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long durationMs = (long) plugin.getConfigManager().torchDurationMinutes * 60L * 1000L;

                var it = torchTimers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Location, Long> entry = it.next();
                    if (now - entry.getValue() > durationMs) {
                        Location loc = entry.getKey();
                        Material type = loc.getBlock().getType();
                        if (isTorch(type)) {
                            loc.getBlock().setType(Material.AIR);
                        }
                        it.remove();
                    }
                }
            }
        };
        torchCheckTask.runTaskTimer(plugin, 1200L, 1200L);

        // 構造理解Ⅲ: 無重力ブロックの定期的クリーンアップ (変更されたブロックは除去)
        gravityCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                gravityImmuneBlocks.removeIf(loc -> {
                    Material type = loc.getBlock().getType();
                    return !type.hasGravity();
                });
            }
        };
        gravityCleanupTask.runTaskTimer(plugin, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (torchCheckTask != null) {
            torchCheckTask.cancel();
        }
        if (gravityCleanupTask != null) {
            gravityCleanupTask.cancel();
        }
        gravityImmuneBlocks.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTorchPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (isTorch(type)) {
            torchTimers.put(block.getLocation(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTorchBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isTorch(block.getType())) {
            torchTimers.remove(block.getLocation());
        }
    }

    @EventHandler
    public void onTorchFuel(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || !isTorch(block.getType())) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null) return;

        Material heldType = held.getType();
        if (heldType != Material.COAL && heldType != Material.CHARCOAL) return;

        Location loc = block.getLocation();
        long extensionMs = (long) plugin.getConfigManager().torchFuelExtensionMinutes * 60L * 1000L;
        if (torchTimers.containsKey(loc)) {
            torchTimers.put(loc, torchTimers.get(loc) + extensionMs);
        } else {
            torchTimers.put(loc, System.currentTimeMillis() + extensionMs);
        }

        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        player.sendMessage("§aTorch fuel extended by " + plugin.getConfigManager().torchFuelExtensionMinutes + " minutes.");
    }

    @EventHandler
    public void onWaterBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.getConfigManager().worldDisableWaterSource) return;

        Block block = event.getBlockClicked();
        if (block == null) return;

        if (block.getType() == Material.WATER) {
            if (block.getBlockData() instanceof Levelled) {
                Levelled levelled = (Levelled) block.getBlockData();
                if (levelled.getLevel() == 0) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onWaterFlow(BlockFromToEvent event) {
        if (!plugin.getConfigManager().worldDisableWaterSource) return;
        if (event.getBlock().getType() != Material.WATER) return;

        Block toBlock = event.getToBlock();
        boolean playerNearby = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(toBlock.getWorld())
                    && player.getLocation().distanceSquared(toBlock.getLocation()) <= 64) {
                playerNearby = true;
                break;
            }
        }

        if (!playerNearby) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Material type = event.getChangedType();
        if (plugin.getConfigManager().blockGravityWhitelist.contains(type)) return;

        if (!type.hasGravity()) return;

        // 構造理解Ⅲ: 設置したブロックは落下しない
        if (gravityImmuneBlocks.contains(event.getBlock().getLocation())) return;

        Block block = event.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.AIR || below.getType() == Material.WATER
                || below.getType() == Material.LAVA || below.isPassable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getBlock().getType().hasGravity()) return;
        Player player = event.getPlayer();
        if (plugin.getDataManager().getData(player).getAbilityLevel("structure_3") > 0) {
            gravityImmuneBlocks.add(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTreeChop(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        Material type = block.getType();
        if (!isLogType(type)) return;

        Player player = event.getPlayer();
        if (!isBareHands(player)) return;

        event.setCancelled(true);
        player.damage(1.0);
        MessageUtil.sendCodexUnlock(player, "Punching trees with bare hands is ineffective. Craft an axe to harvest wood.");
    }

    private boolean isTorch(Material type) {
        String name = type.name();
        return name.contains("TORCH");
    }

    private boolean isLogType(Material type) {
        String name = type.name();
        return name.contains("LOG") || name.contains("WOOD") || name.contains("STEM") || name.contains("HYPHAE");
    }

    private boolean isBareHands(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return true;
        return !item.getType().name().contains("AXE");
    }
}
