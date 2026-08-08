package com.realizascend.modules.season;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class SeasonManager extends RealizModule {

    public enum Season {
        SPRING, PLUM_RAIN, SUMMER, AUTUMN, WINTER
    }

    private Season currentSeason;
    private int dayInSeason;
    private long lastCheckedDay;
    private BukkitRunnable task;

    public SeasonManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        try {
            currentSeason = Season.valueOf(plugin.getConfigManager().seasonInitialSeason);
        } catch (IllegalArgumentException e) {
            currentSeason = Season.SPRING;
        }
        dayInSeason = 0;
        lastCheckedDay = -1;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getWorlds().isEmpty()) return;
                World world = Bukkit.getWorlds().get(0);
                long fullTime = world.getFullTime();
                long currentDay = fullTime / 24000;
                if (lastCheckedDay == -1) {
                    lastCheckedDay = currentDay;
                    return;
                }
                if (currentDay > lastCheckedDay) {
                    long daysPassed = currentDay - lastCheckedDay;
                    for (long i = 0; i < daysPassed; i++) {
                        dayInSeason++;
                        if (dayInSeason >= plugin.getConfigManager().seasonDaysPerSeason) {
                            advanceSeason();
                            dayInSeason = 0;
                        }
                    }
                    lastCheckedDay = currentDay;
                }
            }
        };
        task.runTaskTimer(plugin, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
    }

    private void advanceSeason() {
        int ordinal = (currentSeason.ordinal() + 1) % Season.values().length;
        currentSeason = Season.values()[ordinal];
        Bukkit.broadcastMessage("§6[RealizAscend] §eThe season has changed to §f" + currentSeason.name() + "§e!");
    }

    public Season getCurrentSeason() {
        return currentSeason;
    }

    public int getDayInSeason() {
        return dayInSeason;
    }

    public boolean isCropGrowthAllowed(Location location) {
        if (currentSeason == Season.WINTER) {
            return isGreenhouse(location);
        }
        return true;
    }

    private boolean isGreenhouse(Location location) {
        Location check = location.clone();

        boolean hasRoof = false;
        for (int dy = 3; dy <= 5; dy++) {
            Material type = check.clone().add(0, dy, 0).getBlock().getType();
            if (isGlass(type)) {
                hasRoof = true;
                break;
            }
        }
        if (!hasRoof) {
            return false;
        }

        int wallsFound = 0;
        for (int dir = 0; dir < 4; dir++) {
            int dx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
            int dz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
            for (int dist = 1; dist <= 3; dist++) {
                Location wallCheck = location.clone().add(dx * dist, 1, dz * dist);
                for (int dy = 0; dy <= 2; dy++) {
                    Material type = wallCheck.clone().add(0, dy, 0).getBlock().getType();
                    if (isGlass(type)) {
                        wallsFound++;
                        dist = 99;
                        break;
                    }
                }
                if (dist == 99) break;
            }
        }

        return wallsFound >= 3;
    }

    private boolean isGlass(Material type) {
        String name = type.name();
        return name.contains("GLASS") || name.contains("GLASS_PANE");
    }
}
