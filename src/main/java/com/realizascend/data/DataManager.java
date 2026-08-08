package com.realizascend.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.realizascend.RealizAscend;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager implements Listener {

    private static final long AUTOSAVE_INTERVAL = 6000L; // 5 minutes in ticks

    private final RealizAscend plugin;
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File dataFolder;
    private BukkitTask autosaveTask;

    public DataManager(RealizAscend plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    public void startAutosave() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, AUTOSAVE_INTERVAL, AUTOSAVE_INTERVAL);
    }

    public void stopAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        save(event.getPlayer().getUniqueId());
    }

    public PlayerData getData(Player player) {
        return getData(player.getUniqueId());
    }

    public PlayerData getData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, PlayerData::new);
    }

    public void removeData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    public Map<UUID, PlayerData> getAllData() {
        return playerDataMap;
    }

    public void loadAll() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player.getUniqueId());
        }
    }

    public void load(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                PlayerData data = gson.fromJson(reader, PlayerData.class);
                if (data != null) {
                    playerDataMap.put(uuid, data);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load data for " + uuid);
            }
        } else {
            playerDataMap.putIfAbsent(uuid, new PlayerData(uuid));
        }
    }

    public void saveAll() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    public void save(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            save(uuid, data);
        }
    }

    private void save(UUID uuid, PlayerData data) {
        File file = new File(dataFolder, uuid.toString() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save data for " + uuid);
        }
    }

    public int getOnlineCount() {
        return (int) Bukkit.getOnlinePlayers().stream()
            .filter(p -> playerDataMap.containsKey(p.getUniqueId()))
            .count();
    }
}
