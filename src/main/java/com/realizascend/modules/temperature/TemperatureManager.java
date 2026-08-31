package com.realizascend.modules.temperature;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import com.realizascend.modules.season.SeasonManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public class TemperatureManager extends RealizModule implements Listener {

    private final Map<UUID, Set<PotionEffectType>> appliedEffects = new HashMap<>();
    private BukkitRunnable task;
    private int fatalTickCounter;

    public TemperatureManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        fatalTickCounter = 0;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        task = new BukkitRunnable() {
            @Override
            public void run() {
                fatalTickCounter++;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!com.realizascend.RealizAscend.isSurvival(player)) continue;
                    processPlayer(player);
                }
            }
        };
        task.runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
        HandlerList.unregisterAll(this);
    }

    // 冷たい/熱い食べ物で体温を一時調整
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        PlayerData data = plugin.getDataManager().getData(event.getPlayer());
        double bodyTemp = data.getBodyTemperature();

        if (isColdFood(item.getType())) {
            data.setBodyTemperature(Math.max(-20, bodyTemp - 1.5));
        } else if (isHotFood(item.getType())) {
            data.setBodyTemperature(Math.min(60, bodyTemp + 1.0));
        }
    }

    private boolean isColdFood(Material mat) {
        return mat == Material.MELON_SLICE || mat == Material.SWEET_BERRIES
            || mat == Material.GLOW_BERRIES || mat == Material.APPLE
            || mat == Material.PUFFERFISH;
    }

    private boolean isHotFood(Material mat) {
        String name = mat.name();
        return name.startsWith("COOKED_") || name.contains("STEW") || name.contains("SOUP")
            || mat == Material.BAKED_POTATO || mat == Material.BREAD;
    }

    private void processPlayer(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        double ambient = calculateAmbientTemperature(player);
        double bodyTemp = data.getBodyTemperature();

        double shiftRate = 0.6;
        double armorInsulation = getArmorInsulation(player);
        double armorHeatResist = getArmorHeatResist(player);

        double insulationFactor = Math.max(0.1, 1.0 - armorInsulation);
        if (ambient > bodyTemp) {
            shiftRate *= Math.max(0.1, insulationFactor - armorHeatResist);
        } else {
            shiftRate *= insulationFactor;
        }

        if (bodyTemp < ambient) {
            bodyTemp = Math.min(bodyTemp + shiftRate, ambient);
        } else if (bodyTemp > ambient) {
            bodyTemp = Math.max(bodyTemp - shiftRate, ambient);
        }
        data.setBodyTemperature(bodyTemp);

        applyTemperatureEffects(player, data, bodyTemp);
        applyTemperatureDecay(player, data, bodyTemp);
    }

    private double calculateAmbientTemperature(Player player) {
        Location loc = player.getLocation();
        Biome biome = loc.getBlock().getBiome();
        String biomeName = biome.name().toUpperCase();
        double temp = 20.0;

        boolean isNether = biomeName.equals("NETHER_WASTES") || biomeName.equals("CRIMSON_FOREST")
            || biomeName.equals("WARPED_FOREST") || biomeName.equals("SOUL_SAND_VALLEY")
            || biomeName.equals("BASALT_DELTAS");
        boolean isEnd = biomeName.equals("THE_END") || biomeName.equals("SMALL_END_ISLANDS")
            || biomeName.equals("END_MIDLANDS") || biomeName.equals("END_HIGHLANDS")
            || biomeName.equals("END_BARRENS");

        // ネザー・エンドは季節の影響を受けない (常時高温/低温)
        if (isNether) {
            temp = 40;
        } else if (isEnd) {
            temp = 2;
        } else {
            SeasonManager.Season season = plugin.getSeasonManager().getCurrentSeason();
            switch (season) {
                case SPRING:  temp += 0; break;
                case SUMMER:  temp += 6; break;   // 真夏でも即死しない程度に緩和
                case AUTUMN:  temp -= 5; break;
                case WINTER:   temp -= 20; break;
                case PLUM_RAIN: temp -= 3; break;
            }

            if (biomeName.contains("SNOWY") || biomeName.contains("FROZEN") || biomeName.contains("ICE")
                    || biomeName.contains("TAIGA") || biomeName.contains("GROVE")
                    || biomeName.contains("JAGGED") || biomeName.contains("STONY_PEAKS")) {
                temp -= 10;
            } else if (biomeName.contains("DESERT") || biomeName.contains("BADLANDS")
                    || biomeName.contains("SAVANNA") || biomeName.contains("MESA")
                    || biomeName.contains("JUNGLE") || biomeName.contains("BEACH")) {
                temp += 10;
            }
        }

        if (loc.getWorld().hasStorm()) {
            if (loc.getWorld().isThundering()) {
                temp -= 8;
            } else {
                temp -= 5;
            }
        }

        double y = loc.getY();
        if (y > 128) {
            temp -= 0.05 * (y - 64);
        } else if (y < 40) {
            // 地下は涼しい (暑さからの避難場所になる)
            temp -= 2;
        }

        long time = loc.getWorld().getTime();
        if (time >= 13000 && time <= 23000) {
            temp -= 3;
        }

        temp += getNearbyBlockModifier(loc);

        return temp;
    }

    private double getNearbyBlockModifier(Location center) {
        double mod = 0;
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Material type = center.getWorld().getBlockAt(baseX + dx, baseY + dy, baseZ + dz).getType();
                    String name = type.name();
                    if (type == Material.FIRE || name.contains("CAMPFIRE")) {
                        mod += 3;
                    } else if (name.contains("TORCH") || name.contains("LANTERN")) {
                        mod += 1;
                    } else if (type == Material.ICE || type == Material.PACKED_ICE
                            || type == Material.BLUE_ICE || name.equals("POWDER_SNOW")
                            || type == Material.SNOW || type == Material.SNOW_BLOCK) {
                        mod -= 2;
                    } else if (type == Material.LAVA) {
                        mod += 10;
                    }
                }
            }
        }

        return mod;
    }

    private double getArmorInsulation(Player player) {
        double total = 0;
        int count = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                total += getInsulationValue(armor.getType());
                count++;
            }
        }
        return count > 0 ? total / count : 0;
    }

    private double getInsulationValue(Material type) {
        String name = type.name();
        if (name.startsWith("LEATHER_")) return 0.25;
        if (name.startsWith("CHAINMAIL_")) return 0.4;
        if (name.startsWith("IRON_")) return 0.5;
        if (name.startsWith("GOLDEN_") || name.startsWith("GOLD_")) return 0.5;
        if (name.startsWith("DIAMOND_")) return 0.7;
        if (name.startsWith("NETHERITE_")) return 0.8;
        return 0;
    }

    private double getArmorHeatResist(Player player) {
        double total = 0;
        int count = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                total += getHeatResistValue(armor.getType());
                count++;
            }
        }
        return count > 0 ? total / count : 0;
    }

    private double getHeatResistValue(Material type) {
        String name = type.name();
        if (name.startsWith("LEATHER_")) return 0.5;
        if (name.startsWith("CHAINMAIL_")) return 0.2;
        if (name.startsWith("IRON_")) return 0.15;
        if (name.startsWith("GOLDEN_") || name.startsWith("GOLD_")) return 0.5;
        if (name.startsWith("DIAMOND_")) return 0.0;
        if (name.startsWith("NETHERITE_")) return 0.0;
        return 0;
    }

    private void applyTemperatureEffects(Player player, PlayerData data, double bodyTemp) {
        double min = plugin.getConfigManager().tempComfortableMin;
        double max = plugin.getConfigManager().tempComfortableMax;
        double warnOff = plugin.getConfigManager().tempWarningOffset;
        double critOff = plugin.getConfigManager().tempCriticalOffset;

        warnOff *= plugin.getSkillManager().getAbilityEffectValue(player, "TEMP_WARNING_DELAY");
        critOff *= plugin.getSkillManager().getAbilityEffectValue(player, "TEMP_CRITICAL_DELAY");

        String zone;
        if (bodyTemp >= min && bodyTemp <= max) {
            zone = "COMFORTABLE";
        } else if (bodyTemp >= min - warnOff && bodyTemp <= max + warnOff) {
            zone = "WARNING";
        } else if (bodyTemp >= min - critOff && bodyTemp <= max + critOff) {
            zone = "CRITICAL";
        } else {
            zone = "FATAL";
        }
        data.setTempZone(zone);

        UUID uuid = player.getUniqueId();
        Set<PotionEffectType> previous = appliedEffects.getOrDefault(uuid, Collections.emptySet());
        for (PotionEffectType type : previous) {
            player.removePotionEffect(type);
        }
        Set<PotionEffectType> current = new HashSet<>();

        boolean isHot = bodyTemp > max;

        switch (zone) {
            case "COMFORTABLE":
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false));
                current.add(PotionEffectType.SPEED);
                break;
            case "WARNING":
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0, true, false));
                current.add(PotionEffectType.SLOW);
                break;
            case "CRITICAL":
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 60, isHot ? 1 : 0, true, false));
                current.add(PotionEffectType.SLOW);
                current.add(PotionEffectType.SLOW_DIGGING);
                if (!isHot) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, false));
                    current.add(PotionEffectType.WEAKNESS);
                }
                break;
            case "FATAL":
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 60, 2, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, true, false));
                current.add(PotionEffectType.SLOW);
                current.add(PotionEffectType.SLOW_DIGGING);
                current.add(PotionEffectType.WEAKNESS);
                if (fatalTickCounter % 2 == 0) {
                    double newHealth = Math.max(0, player.getHealth() - 1.0);
                    player.setHealth(newHealth);
                }
                break;
        }

        if (current.isEmpty()) {
            appliedEffects.remove(uuid);
        } else {
            appliedEffects.put(uuid, current);
        }
    }

    private void applyTemperatureDecay(Player player, PlayerData data, double bodyTemp) {
        double max = plugin.getConfigManager().tempComfortableMax;
        double min = plugin.getConfigManager().tempComfortableMin;
        double warnOff = plugin.getConfigManager().tempWarningOffset;

        if (bodyTemp > max + warnOff) {
            data.setHydration(data.getHydration() - 0.05);
        } else if (bodyTemp < min - warnOff) {
            data.setCalories(data.getCalories() - 0.05);
        }
    }
}
