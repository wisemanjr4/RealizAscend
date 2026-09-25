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
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager extends RealizModule implements Listener {

    // 構造理解III: MCRealistic側の重力を無効化する権限 (false付与で免除)
    public static final String GRAVITY_PERMISSION = "mcrealistic.gravity";

    private final Map<Location, Long> torchTimers = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> gravityAttachments = new ConcurrentHashMap<>();
    private BukkitRunnable torchCheckTask;

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

        // リロード時にオンライン中の構造理解III所持者へ権限を付け直す
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncGravityPermission(player);
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (torchCheckTask != null) {
            torchCheckTask.cancel();
        }
        for (Map.Entry<UUID, PermissionAttachment> entry : gravityAttachments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.removeAttachment(entry.getValue());
            }
        }
        gravityAttachments.clear();
    }

    // 構造理解III: mcrealistic.gravity=false を付与/剥奪する
    public void syncGravityPermission(Player player) {
        boolean has = plugin.getDataManager().getData(player).getAbilityLevel("structure_3") > 0;
        PermissionAttachment attachment = gravityAttachments.get(player.getUniqueId());
        if (has) {
            if (attachment == null) {
                attachment = player.addAttachment(plugin);
                gravityAttachments.put(player.getUniqueId(), attachment);
            }
            attachment.setPermission(GRAVITY_PERMISSION, false);
        } else if (attachment != null) {
            player.removeAttachment(attachment);
            gravityAttachments.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinSyncGravity(org.bukkit.event.player.PlayerJoinEvent event) {
        syncGravityPermission(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuitClearGravity(org.bukkit.event.player.PlayerQuitEvent event) {
        PermissionAttachment attachment = gravityAttachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            event.getPlayer().removeAttachment(attachment);
        }
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
    public void onWaterBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.getConfigManager().worldDisableWaterSource) return;
        if (event.getBucket() != Material.WATER_BUCKET) return;

        // 置いた水は「水流」(level>0) にする → 水源の無限生成を防ぐ
        // (汲み取りは許可: 自然水源からは自由に水を得られる)
        Location loc = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (loc.getWorld() == null) return;
            if (loc.getBlock().getType() != Material.WATER) return;
            Levelled flowing = (Levelled) Material.WATER.createBlockData();
            flowing.setLevel(1);
            loc.getBlock().setBlockData(flowing, false);
        });
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Material type = event.getChangedType();
        if (plugin.getConfigManager().blockGravityWhitelist.contains(type)) return;

        if (!type.hasGravity()) return;

        Block block = event.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.AIR || below.getType() == Material.WATER
                || below.getType() == Material.LAVA || below.isPassable()) {
            event.setCancelled(true);
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
