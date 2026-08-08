package com.realizascend.modules.farm;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import com.realizascend.modules.season.SeasonManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class FarmingManager extends RealizModule implements Listener {

    private final Random random = new Random();

    public FarmingManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onCropGrow(BlockGrowEvent event) {
        SeasonManager.Season season = plugin.getSeasonManager().getCurrentSeason();
        Location loc = event.getBlock().getLocation();
        Player near = findNearbyPlayer(loc);
        PlayerData nd = near != null ? plugin.getDataManager().getData(near) : null;

        switch (season) {
            case WINTER:
                if (plugin.getSeasonManager().isCropGrowthAllowed(loc)) return;
                if (nd != null && nd.getAbilityLevel("season_read_2") > 0) return;
                event.setCancelled(true);
                if (near != null) {
                    near.sendMessage(ChatColor.GRAY + "冬の寒さで作物が育たない。温室が必要だ。");
                }
                break;
            case AUTUMN:
                // 霜: 成長が止まる
                if (random.nextDouble() < 0.4) {
                    event.setCancelled(true);
                    if (near != null) {
                        near.sendMessage(ChatColor.GRAY + "霜で作物の成長が止まっている。");
                    }
                }
                break;
            case PLUM_RAIN:
                // 梅雨: 一部作物が腐る (水管理Ⅱで耐性)
                double overwaterResist = nd != null
                    ? plugin.getSkillManager().getAbilityEffectValue(near, "OVERWATER_RESIST") : 1.0;
                if (random.nextDouble() < 0.12 * overwaterResist) {
                    event.setCancelled(true);
                    event.getBlock().setType(Material.AIR);
                    if (near != null) {
                        near.sendMessage(ChatColor.RED + "梅雨の湿気で作物が腐ってしまった!");
                    }
                }
                break;
            case SUMMER:
                // 高温バイオームの畑が乾く
                if (random.nextDouble() < 0.05 && isHotBiome(loc)) {
                    Block below = event.getBlock().getRelative(BlockFace.DOWN);
                    if (below.getType() == Material.FARMLAND) {
                        below.setType(Material.DIRT);
                        if (near != null) {
                            near.sendMessage(ChatColor.GOLD + "夏の暑さで畑が乾いてしまった。水やりが必要だ。");
                        }
                    }
                }
                break;
            case SPRING:
            default:
                break;
        }
    }

    private boolean isHotBiome(Location loc) {
        String name = loc.getBlock().getBiome().name();
        return name.contains("DESERT") || name.contains("BADLANDS") || name.contains("SAVANNA")
            || name.contains("BEACH") || name.contains("NETHER");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        BlockData data = event.getBlock().getState().getBlockData();
        if (!(data instanceof Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return;

        Material dropType = cropDrop(event.getBlock().getType());
        if (dropType == null) return;

        Player player = event.getPlayer();
        double harvestMult = plugin.getSkillManager().getAbilityEffectValue(player, "HARVEST_DROP");
        if (harvestMult > 1.0 && random.nextDouble() < harvestMult - 1.0) {
            event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5), new ItemStack(dropType));
        }

        // 収穫術Ⅱ: 種が余分に落ちる
        Material seedType = seedDrop(event.getBlock().getType());
        if (seedType != null) {
            double seedMult = plugin.getSkillManager().getAbilityEffectValue(player, "HARVEST_SEED_DROP");
            if (seedMult > 1.0 && random.nextDouble() < seedMult - 1.0) {
                event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5), new ItemStack(seedType));
            }
        }

        plugin.getSkillManager().addXp(player, "FARMING", 3.0);
    }

    private Material seedDrop(Material type) {
        switch (type) {
            case WHEAT: return Material.WHEAT_SEEDS;
            case BEETROOTS: return Material.BEETROOT_SEEDS;
            case PUMPKIN: return Material.PUMPKIN_SEEDS;
            case MELON: return Material.MELON_SEEDS;
            default: return null;
        }
    }

    @EventHandler
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.isCancelled()) return;
        if (event.getItem() == null) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.FARMLAND) return;
        if (!isSeed(event.getItem().getType())) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);
        if (data == null) return;

        SeasonManager.Season season = plugin.getSeasonManager().getCurrentSeason();
        if (data.getAbilityLevel("season_read_1") > 0) {
            player.sendMessage(ChatColor.GRAY + switch (season) {
                case SPRING -> "春だ。作物の育ちはやや遅い。";
                case PLUM_RAIN -> "梅雨だ。水は十分だが腐りに注意。";
                case SUMMER -> "夏だ。最も育ちやすい。";
                case AUTUMN -> "秋だ。収穫の時期。霜に注意。";
                case WINTER -> "冬だ。屋外では育たない。温室が必要。";
            });
        }

        if (season == SeasonManager.Season.WINTER && data.getAbilityLevel("season_read_2") > 0) {
            player.sendMessage(ChatColor.GRAY + "季節読みの力で屋外でも育つ!");
        }
    }

    private Player findNearbyPlayer(Location loc) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player p : loc.getWorld().getPlayers()) {
            if (!p.getLocation().getWorld().equals(loc.getWorld())) continue;
            double dist = p.getLocation().distanceSquared(loc);
            if (dist <= 64.0 && dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private Material cropDrop(Material type) {
        switch (type) {
            case WHEAT: return Material.WHEAT;
            case CARROTS: return Material.CARROT;
            case POTATOES: return Material.POTATO;
            case BEETROOTS: return Material.BEETROOT;
            case COCOA: return Material.COCOA_BEANS;
            case NETHER_WART: return Material.NETHER_WART;
            default: return null;
        }
    }

    private boolean isSeed(Material material) {
        return material == Material.WHEAT_SEEDS || material == Material.CARROT
            || material == Material.POTATO || material == Material.BEETROOT_SEEDS
            || material == Material.PUMPKIN_SEEDS || material == Material.MELON_SEEDS
            || material == Material.NETHER_WART || material == Material.COCOA_BEANS;
    }
}
