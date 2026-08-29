package com.realizascend.modules.weight;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class WeightManager extends RealizModule {

    private final Map<Material, Double> weightFactors = new EnumMap<>(Material.class);
    private BukkitRunnable updateTask;
    private final Map<UUID, String> lastZone = new java.util.HashMap<>();

    private static final double DEFAULT_WEIGHT = 8.0;
    private static final double ARMOR_WEIGHT = 20.0;
    private static final double EQUIPPED_ARMOR_MULTIPLIER = 0.5;

    public WeightManager(RealizAscend plugin) {
        super(plugin);
        initWeightFactors();
    }

    private void initWeightFactors() {
        Material[] heavyMaterials = {
            Material.STONE, Material.COBBLESTONE, Material.DIRT,
            Material.SAND, Material.GRAVEL, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.DEEPSLATE,
            Material.TUFF, Material.CALCITE, Material.BASALT,
            Material.BLACKSTONE, Material.NETHERRACK, Material.END_STONE,
            Material.OBSIDIAN, Material.SOUL_SAND, Material.SOUL_SOIL
        };
        for (Material mat : heavyMaterials) {
            weightFactors.put(mat, 12.0);
        }

        Material[] ingotMaterials = {
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.NETHERITE_INGOT,
            Material.COPPER_INGOT
        };
        for (Material mat : ingotMaterials) {
            weightFactors.put(mat, 8.0);
        }

        weightFactors.put(Material.DIAMOND, 1.0);
        weightFactors.put(Material.EMERALD, 1.0);

        Material[] woodMaterials = {
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_MANGROVE_LOG,
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD,
            Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD,
            Material.MANGROVE_WOOD,
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD,
            Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_JUNGLE_WOOD,
            Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD,
            Material.STRIPPED_MANGROVE_WOOD,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
            Material.MANGROVE_PLANKS,
            Material.CRIMSON_STEM, Material.WARPED_STEM,
            Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
            Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
            Material.STRIPPED_CRIMSON_HYPHAE, Material.STRIPPED_WARPED_HYPHAE,
            Material.CRIMSON_PLANKS,             Material.WARPED_PLANKS
        };
        for (Material mat : woodMaterials) {
            weightFactors.put(mat, 6.0);
        }

        Material[] rawOreMaterials = {
            Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER,
            Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK
        };
        for (Material mat : rawOreMaterials) {
            weightFactors.put(mat, 15.0);
        }

        weightFactors.put(Material.WATER_BUCKET, 20.0);
        weightFactors.put(Material.LAVA_BUCKET, 20.0);
    }

    private boolean isTool(Material mat) {
        String name = mat.name();
        return name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
            || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
            || mat == Material.SHEARS || mat == Material.FLINT_AND_STEEL
            || mat == Material.FISHING_ROD || mat == Material.CARROT_ON_A_STICK
            || mat == Material.WARPED_FUNGUS_ON_A_STICK || mat == Material.BOW
            || mat == Material.CROSSBOW || mat == Material.TRIDENT
            || mat == Material.SHIELD
            || name.endsWith("_ON_A_STICK");
    }

    private boolean isArmor(Material mat) {
        String name = mat.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
            || mat == Material.ELYTRA || mat == Material.TURTLE_HELMET
            || mat == Material.PLAYER_HEAD || mat == Material.CARVED_PUMPKIN;
    }

    private boolean isFood(Material mat) {
        return mat.isEdible();
    }

    private double getItemWeight(ItemStack item) {
        if (item == null) return 0;
        Material mat = item.getType();

        if (weightFactors.containsKey(mat)) {
            return weightFactors.get(mat) * item.getAmount();
        }

        if (isTool(mat)) {
            return 10.0 * item.getAmount();
        }
        if (isArmor(mat)) {
            return ARMOR_WEIGHT * item.getAmount();
        }
        if (isFood(mat)) {
            return 5.0 * item.getAmount();
        }

        return DEFAULT_WEIGHT * item.getAmount();
    }

    private double calculateTotalWeight(Player player) {
        PlayerInventory inv = player.getInventory();
        double total = 0;
        // 怪力: 重いブロックを素手で運べる (重量半減)
        double carryMult = 1.0;
        if (plugin.getSkillManager().getAbilityEffectValue(player, "HEAVY_BLOCK_CARRY") > 1.0) {
            carryMult = 0.5;
        }

        for (ItemStack item : inv.getContents()) {
            total += getItemWeight(item) * (isHeavyMaterial(item) ? carryMult : 1.0);
        }

        ItemStack[] armor = inv.getArmorContents();
        for (ItemStack item : armor) {
            total += getItemWeight(item) * EQUIPPED_ARMOR_MULTIPLIER;
        }

        ItemStack offhand = inv.getItemInOffHand();
        total += getItemWeight(offhand) * (isHeavyMaterial(offhand) ? carryMult : 1.0);

        return total;
    }

    private boolean isHeavyMaterial(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        String name = mat.name();
        return name.contains("STONE") || name.contains("ORE") || name.contains("DEEPSLATE")
            || name.contains("COBBLE") || name.contains("OBSIDIAN") || name.contains("BASALT")
            || name.contains("NETHERRACK") || name.contains("RAW_") || mat == Material.DIRT
            || mat == Material.SAND || mat == Material.GRAVEL;
    }

    private String getWeightZone(double weight, double normalLimit, double overLimit) {
        if (weight <= normalLimit) return "NORMAL";
        if (weight <= overLimit) return "OVER";
        return "HEAVY_OVER";
    }

    @Override
    public void onEnable() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                double normalLimit = plugin.getConfigManager().weightNormalLimit;
                double overLimit = plugin.getConfigManager().weightOverLimit;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    double weight = calculateTotalWeight(player);
                    plugin.getDataManager().getData(player).setCurrentWeight(weight);

                    double weightMult = plugin.getSkillManager().getAbilityEffectValue(player, "MAX_WEIGHT")
                        * plugin.getSkillManager().getAbilityEffectValue(player, "STRENGTH_ALL_BOOST");
                    double actualNormal = normalLimit * weightMult;
                    double actualOver = overLimit * weightMult;
                    double penaltyMult = plugin.getSkillManager().getAbilityEffectValue(player, "OVERWEIGHT_PENALTY");

                    String zone = getWeightZone(weight, actualNormal, actualOver);
                    String previousZone = lastZone.get(player.getUniqueId());

                    player.sendActionBar(ChatColor.GRAY + "重量: " + ChatColor.WHITE
                        + String.format("%.0f", weight) + ChatColor.GRAY + " / "
                        + ChatColor.WHITE + String.format("%.0f", actualNormal));

                    // Re-apply effects every cycle so other modules can't erase them
                    if (!zone.equals(previousZone)) {
                        lastZone.put(player.getUniqueId(), zone);
                        if (zone.equals("OVER")) {
                            player.sendMessage(ChatColor.YELLOW + "重量オーバー! 動きが遅い。");
                        } else if (zone.equals("HEAVY_OVER")) {
                            player.sendMessage(ChatColor.RED + "重度の重量オーバー! ジャンプできない。");
                        }
                    }

                    player.removePotionEffect(PotionEffectType.SLOW);
                    player.removePotionEffect(PotionEffectType.JUMP);
                    if (zone.equals("OVER")) {
                        int slowLevel = penaltyMult < 0.8 ? 0 : 1;
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOW, Integer.MAX_VALUE, slowLevel, false, false, true));
                    } else if (zone.equals("HEAVY_OVER")) {
                        int slowLevel = penaltyMult < 0.7 ? 1 : 2;
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOW, Integer.MAX_VALUE, slowLevel, false, false, true));
                        if (penaltyMult >= 0.9) {
                            player.addPotionEffect(new PotionEffect(
                                PotionEffectType.JUMP, Integer.MAX_VALUE, -100, false, false, true));
                        }
                    }

                    if (weight > actualNormal) {
                        double extraCalories = (weight - actualNormal) * 0.001;
                        plugin.getDataManager().getData(player)
                            .setCalories(Math.max(0, plugin.getDataManager().getData(player).getCalories() - extraCalories));
                    }
                }
            }
        };
        updateTask.runTaskTimer(plugin, 20L, 100L);
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.SLOW);
            player.removePotionEffect(PotionEffectType.JUMP);
        }
    }
}
