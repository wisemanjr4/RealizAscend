package com.realizascend.modules.corpse;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CorpseManager extends RealizModule implements Listener {

    private final Map<UUID, List<CorpseData>> corpses = new HashMap<>();
    private final Map<Inventory, UUID> openInventories = new HashMap<>();
    private BukkitRunnable cleanupTask;

    public static class CorpseData {
        public final UUID corpseArmorStandId;
        public final Location location;
        public ItemStack[] inventoryContents;
        public final long deathTime;
        public final UUID playerUUID;
        public final String playerName;

        public CorpseData(UUID corpseArmorStandId, Location location, ItemStack[] inventoryContents,
                          long deathTime, UUID playerUUID, String playerName) {
            this.corpseArmorStandId = corpseArmorStandId;
            this.location = location;
            this.inventoryContents = inventoryContents;
            this.deathTime = deathTime;
            this.playerUUID = playerUUID;
            this.playerName = playerName;
        }
    }

    public CorpseManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long tickDurationTicks = plugin.getConfigManager().corpseDurationDays * 24000L;
                long now = System.currentTimeMillis();
                long tickDurationMs = tickDurationTicks * 50L;

                Iterator<Map.Entry<UUID, List<CorpseData>>> mapIt = corpses.entrySet().iterator();
                while (mapIt.hasNext()) {
                    Map.Entry<UUID, List<CorpseData>> entry = mapIt.next();
                    List<CorpseData> list = entry.getValue();
                    Iterator<CorpseData> listIt = list.iterator();
                    while (listIt.hasNext()) {
                        CorpseData data = listIt.next();
                        if (now - data.deathTime >= tickDurationMs) {
                            expireCorpse(entry.getKey(), data);
                            listIt.remove();
                        }
                    }
                    if (list.isEmpty()) {
                        mapIt.remove();
                    }
                }
            }
        };
        cleanupTask.runTaskTimer(plugin, 1200L, 1200L);
    }

    private void expireCorpse(UUID playerUUID, CorpseData data) {
        Entity entity = Bukkit.getEntity(data.corpseArmorStandId);
        if (entity instanceof ArmorStand stand) {
            stand.remove();
        }

        if (!plugin.getConfigManager().corpseRemoveOnExpire) {
            World world = data.location.getWorld();
            if (world == null) return;
            for (ItemStack item : data.inventoryContents) {
                if (item != null && item.getType() != Material.AIR) {
                    world.dropItemNaturally(data.location, item);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = player.getLocation();

        ItemStack[] drops = event.getDrops().toArray(new ItemStack[0]);
        event.getDrops().clear();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            head.setItemMeta(skullMeta);
        }

        ArmorStand stand = player.getWorld().spawn(deathLoc, ArmorStand.class);
        stand.setVisible(true);
        stand.setCustomName(ChatColor.RED + player.getName() + "'s Corpse");
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.getEquipment().setHelmet(head);

        corpses.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(new CorpseData(
            stand.getUniqueId(), deathLoc, drops,
            System.currentTimeMillis(), player.getUniqueId(), player.getName()
        ));
    }

    @EventHandler
    public void onCorpseInteract(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (!(target instanceof ArmorStand stand)) return;

        for (List<CorpseData> list : corpses.values()) {
            for (CorpseData data : list) {
                if (data.corpseArmorStandId.equals(stand.getUniqueId())) {
                    event.setCancelled(true);
                    openCorpseInventory(event.getPlayer(), data);
                    return;
                }
            }
        }
    }

    private void openCorpseInventory(Player player, CorpseData data) {
        Inventory inv = Bukkit.createInventory(null, 54,
            ChatColor.DARK_RED + data.playerName + "'s Corpse");

        for (int i = 0; i < data.inventoryContents.length && i < 54; i++) {
            if (data.inventoryContents[i] != null) {
                inv.setItem(i, data.inventoryContents[i].clone());
            }
        }

        openInventories.put(inv, data.corpseArmorStandId);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID armorStandId = openInventories.remove(event.getInventory());
        if (armorStandId == null) return;

        for (List<CorpseData> list : corpses.values()) {
            for (CorpseData data : list) {
                if (data.corpseArmorStandId.equals(armorStandId)) {
                    Inventory inv = event.getInventory();
                    data.inventoryContents = inv.getContents().clone();
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onArmorStandDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand stand) {
            for (List<CorpseData> list : corpses.values()) {
                for (CorpseData data : list) {
                    if (data.corpseArmorStandId.equals(stand.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onArmorStandEnvDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand stand) {
            for (List<CorpseData> list : corpses.values()) {
                for (CorpseData data : list) {
                    if (data.corpseArmorStandId.equals(stand.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        for (List<CorpseData> list : corpses.values()) {
            for (CorpseData data : list) {
                Entity entity = Bukkit.getEntity(data.corpseArmorStandId);
                if (entity instanceof ArmorStand stand) {
                    stand.remove();
                }
            }
        }
        corpses.clear();
        openInventories.clear();
        PlayerDeathEvent.getHandlerList().unregister(this);
        PlayerInteractEntityEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        EntityDamageEvent.getHandlerList().unregister(this);
    }
}
