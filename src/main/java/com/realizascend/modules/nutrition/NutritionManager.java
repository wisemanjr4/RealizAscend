package com.realizascend.modules.nutrition;

import com.realizascend.RealizAscend;
import com.realizascend.core.ConfigManager;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import com.realizascend.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NutritionManager extends RealizModule implements Listener {

    private static final long DECAY_INTERVAL = 600L;
    private static final String RAW_WATER_KEY = "raw_water";
    private static final String BOILED_KEY = "boiled";
    private static final String FILTER_KEY = "water_filter";
    private static final long BOIL_DURATION = 400L;
    private static final int FILTER_MAX_DURABILITY = 10;

    private final Set<UUID> recentConsumers = new HashSet<>();
    private final NamespacedKey rawWaterNamespacedKey;
    private final NamespacedKey boiledKey;
    private final NamespacedKey filterKey;
    private final Map<Location, BoilData> boilingFurnaces = new HashMap<>();
    private BukkitTask decayTask;
    private BukkitTask boilCheckTask;

    public NutritionManager(RealizAscend plugin) {
        super(plugin);
        rawWaterNamespacedKey = new NamespacedKey(plugin, RAW_WATER_KEY);
        boiledKey = new NamespacedKey(plugin, BOILED_KEY);
        filterKey = new NamespacedKey(plugin, FILTER_KEY);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        decayTask = new DecayRunnable().runTaskTimer(plugin, DECAY_INTERVAL, DECAY_INTERVAL);
        boilCheckTask = new BoilCheckRunnable().runTaskTimer(plugin, 20L, 20L);
        registerFilterRecipe();
    }

    @Override
    public void onDisable() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
        if (boilCheckTask != null) {
            boilCheckTask.cancel();
            boilCheckTask = null;
        }
        recentConsumers.clear();
        boilingFurnaces.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);
        Material material = event.getItem().getType();

        if (material == Material.POTION) {
            handlePotionConsume(player, data, event.getItem());
            return;
        }

        if (!isEdible(material)) {
            return;
        }

        recentConsumers.add(player.getUniqueId());

        double gainMult = plugin.getSkillManager().getAbilityEffectValue(player, "NUTRIENT_GAIN");
        FoodValues food = getFoodValues(material);
        applyNutrition(data, food, gainMult);
        sendFoodFeedback(player, material, food);

        // 毒耐性: 毒の継続時間短縮
        int poisonTicks = (int) (200 * plugin.getSkillManager().getAbilityEffectValue(player, "POISON_DURATION"));
        if (material == Material.ROTTEN_FLESH || material == Material.SPIDER_EYE || material == Material.PUFFERFISH) {
            // 鋼の胃袋: 腐敗食料のデバフ軽減
            double rottenResist = plugin.getSkillManager().getAbilityEffectValue(player, "ROTTEN_DEBUFF");
            if (rottenResist >= 1.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, 0));
            }
        }

        // 生肉は食中毒リスク (設計書: 食材そのままはデバフあり)
        double diseaseChance = plugin.getSkillManager().getAbilityEffectValue(player, "DISEASE_CHANCE");
        if (isRawMeat(material) && Math.random() < 0.2 * diseaseChance) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 200, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
            player.sendMessage(ChatColor.YELLOW + "生肉を食べて腹を壊した...火を通すべきだった。");
        }

        double foodRegen = plugin.getSkillManager().getAbilityEffectValue(player, "FOOD_HP_REGEN");
        if (foodRegen > 1.0 && player.getHealth() < player.getMaxHealth()) {
            plugin.getRecoveryManager().addHealthRegen(player, 2.0 * (foodRegen - 1.0), 10);
        }

        // バニラ空腹ゲージを栄養状態に合わせて更新 (胃袋の見た目)
        syncFoodLevel(player, data);
    }

    // バニラ空腹ゲージ = 胃袋(食べた量)として、栄養バランスに合わせて表示する
    private void syncFoodLevel(Player player, PlayerData data) {
        int foodLevel = (int) Math.round(data.getNutritionBalance() / 100.0 * 20.0);
        foodLevel = Math.max(0, Math.min(20, foodLevel));
        if (player.getFoodLevel() != foodLevel) {
            player.setFoodLevel(foodLevel);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (recentConsumers.remove(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onWaterCollect(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.WATER) return;

        ItemStack hand = event.getItem();
        if (hand == null || hand.getType() != Material.GLASS_BOTTLE) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        ItemStack waterBottle = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) waterBottle.getItemMeta();
        meta.setBasePotionData(new PotionData(PotionType.WATER));
        meta.setDisplayName(ChatColor.AQUA + "汚染水");
        meta.getPersistentDataContainer().set(rawWaterNamespacedKey, PersistentDataType.BYTE, (byte) 1);
        waterBottle.setItemMeta(meta);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(waterBottle);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        MessageUtil.sendActionBar(player, ChatColor.AQUA + "+1 汚染水ボトル (煮沸か浄化が必要)");
    }

    // 水の中でシフト右クリック (手が空) でその場の水を直接飲める
    @EventHandler(priority = EventPriority.LOW)
    public void onDirectDrink(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.isInWater()) return;

        // 手に何か持っていれば直接飲まない (瓶での汲み取りや設置を優先)
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() != Material.AIR) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && event.getClickedBlock() != null
            && event.getClickedBlock().getType() != Material.WATER) {
            return;
        }

        event.setCancelled(true);
        PlayerData data = plugin.getDataManager().getData(player);
        data.setHydration(data.getHydration() + 20.0);

        double diseaseChance = plugin.getSkillManager().getAbilityEffectValue(player, "DISEASE_CHANCE");
        if (Math.random() < 0.3 * diseaseChance) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 200, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
            player.sendMessage(ChatColor.YELLOW + "水をそのまま飲んで腹を壊した...沸かすべきだった。");
        } else {
            player.sendMessage(ChatColor.AQUA + "水を直接飲んで喉を潤した。(汚染の恐れあり)");
        }
        MessageUtil.sendActionBar(player, ChatColor.AQUA + "水分 +20 (直接飲んだ)");
    }

    private void handlePotionConsume(Player player, PlayerData data, ItemStack item) {
        if (!(item.getItemMeta() instanceof PotionMeta)) return;
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        PotionData potionData = meta.getBasePotionData();
        if (potionData == null) return;

        // 水ポーションは多め、それ以外のポーションも飲めば水分が少し回復する
        boolean isRaw = false;
        double hydGain = 15.0;
        if (potionData.getType() == PotionType.WATER) {
            isRaw = meta.getPersistentDataContainer().has(rawWaterNamespacedKey, PersistentDataType.BYTE);
            hydGain = isRaw ? 20.0 : 40.0;
        }
        data.setHydration(data.getHydration() + hydGain);

        String msg = potionData.getType() == PotionType.WATER
            ? ChatColor.AQUA + "水分 " + (isRaw ? "+20 (汚染水 - 腹を壊すかも!)" : "+40")
            : ChatColor.AQUA + "水分 +15 (ポーション)";
        MessageUtil.sendActionBar(player, msg);

        if (isRaw) {
            double diseaseChance = plugin.getSkillManager().getAbilityEffectValue(player, "DISEASE_CHANCE");
            // 汚染水は確率で腹を壊す
            if (Math.random() < 0.35 * diseaseChance) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 200, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
                player.sendMessage(ChatColor.YELLOW + "汚染水を飲んで腹を壊した...沸かすべきだった。");
            }
            // さらに感染リスクもある
            if (Math.random() < 0.2 * diseaseChance) {
                data.setInfected(true);
                player.sendMessage(ChatColor.RED + "汚染水を飲んで気分が悪くなった...");
            }
        }
    }

    @EventHandler
    public void onFurnaceInteract(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof FurnaceInventory fi)) return;
        Location loc = fi.getLocation();
        if (loc == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack input = fi.getItem(0);
            if (input != null && isRawWater(input)) {
                boilingFurnaces.putIfAbsent(loc, new BoilData());
            } else {
                boilingFurnaces.remove(loc);
            }
        });
    }

    @EventHandler
    public void onHopperMoveItem(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (event.getDestination() instanceof FurnaceInventory fi) {
            Location loc = fi.getLocation();
            if (loc != null && isRawWater(event.getItem())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack input = fi.getItem(0);
                    if (input != null && isRawWater(input)) {
                        boilingFurnaces.putIfAbsent(loc, new BoilData());
                    }
                });
            }
        }
    }

    @EventHandler
    public void onFilterUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = event.getItem();
        if (hand == null || !isWaterFilter(hand)) return;

        Player player = event.getPlayer();

        int rawSlot = -1;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && isRawWater(contents[i])) {
                rawSlot = i;
                break;
            }
        }

        if (rawSlot == -1) {
            MessageUtil.sendActionBar(player, ChatColor.RED + "インベントリに生水ボトルがない!");
            return;
        }

        event.setCancelled(true);

        ItemStack rawWater = contents[rawSlot];
        rawWater.setAmount(rawWater.getAmount() - 1);
        if (rawWater.getAmount() <= 0) {
            player.getInventory().setItem(rawSlot, null);
        }

        player.getInventory().addItem(createCleanWater());
        MessageUtil.sendActionBar(player, ChatColor.AQUA + "生水1つを清浄水に浄化した!");

        int durability = getFilterDurability(hand);
        durability--;
        boolean offhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        if (durability <= 0) {
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else if (offhand) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            MessageUtil.sendActionBar(player, ChatColor.RED + "浄水フィルターが壊れた!");
        } else {
            setFilterDurability(hand, durability);
        }
    }

    private boolean isRawWater(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        return meta.getPersistentDataContainer().has(rawWaterNamespacedKey, PersistentDataType.BYTE);
    }

    private ItemStack createCleanWater() {
        ItemStack bottle = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
        meta.setBasePotionData(new PotionData(PotionType.WATER));
        meta.setDisplayName(ChatColor.AQUA + "清浄水");
        meta.getPersistentDataContainer().set(boiledKey, PersistentDataType.BYTE, (byte) 1);
        bottle.setItemMeta(meta);
        return bottle;
    }

    private ItemStack createWaterFilter() {
        ItemStack filter = new ItemStack(Material.LEATHER);
        ItemMeta meta = filter.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "浄水フィルター");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "右クリックで生水を浄化する");
        lore.add(ChatColor.GRAY + "使用回数: " + FILTER_MAX_DURABILITY + "/" + FILTER_MAX_DURABILITY);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(filterKey, PersistentDataType.INTEGER, FILTER_MAX_DURABILITY);
        filter.setItemMeta(meta);
        return filter;
    }

    private void registerFilterRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "water_filter_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createWaterFilter());
        recipe.shape("CCC", "SSS", "GGG");
        recipe.setIngredient('C', Material.CHARCOAL);
        recipe.setIngredient('S', Material.SAND);
        recipe.setIngredient('G', Material.GRAVEL);
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
            // Recipe already registered
        }
    }

    private boolean isWaterFilter(ItemStack item) {
        if (item == null || item.getType() != Material.LEATHER) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(filterKey, PersistentDataType.INTEGER);
    }

    private int getFilterDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer dura = item.getItemMeta().getPersistentDataContainer().get(filterKey, PersistentDataType.INTEGER);
        return dura != null ? dura : 0;
    }

    private void setFilterDurability(ItemStack item, int durability) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(filterKey, PersistentDataType.INTEGER, durability);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "右クリックで生水を浄化する");
        lore.add(ChatColor.GRAY + "使用回数: " + durability + "/" + FILTER_MAX_DURABILITY);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void applyNutrition(PlayerData data, FoodValues food, double gainMult) {
        data.setCalories(data.getCalories() + food.calories * gainMult);
        data.setProtein(data.getProtein() + food.protein * gainMult);
        data.setVitamins(data.getVitamins() + food.vitamins * gainMult);
        data.setSalt(data.getSalt() + food.salt * gainMult);
        data.setHydration(data.getHydration() + food.hydration);
    }

    private void sendFoodFeedback(Player player, Material material, FoodValues food) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GREEN).append("食べた ").append(formatMaterialName(material)).append(": ");
        appendIfNonZero(sb, "カロリー", food.calories);
        appendIfNonZero(sb, "タンパク質", food.protein);
        appendIfNonZero(sb, "ビタミン", food.vitamins);
        appendIfNonZero(sb, "塩分", food.salt);
        if (food.hydration > 0) {
            sb.append(ChatColor.AQUA).append("水分+").append(String.format("%.0f", food.hydration)).append(" ");
        } else if (food.hydration < 0) {
            sb.append(ChatColor.RED).append("水分").append(String.format("%.0f", food.hydration)).append(" ");
        }
        MessageUtil.sendActionBar(player, sb.toString().trim());
    }

    private void appendIfNonZero(StringBuilder sb, String label, double value) {
        if (value != 0) {
            sb.append(label).append(value > 0 ? "+" : "").append(String.format("%.0f", value)).append(" ");
        }
    }

    private boolean isEdible(Material material) {
        return material.isEdible() || material == Material.CAKE || material == Material.PUMPKIN_PIE;
    }

    private boolean isRawMeat(Material mat) {
        return mat == Material.BEEF || mat == Material.CHICKEN || mat == Material.PORKCHOP
            || mat == Material.MUTTON || mat == Material.RABBIT
            || mat == Material.COD || mat == Material.SALMON
            || mat == Material.TROPICAL_FISH;
    }

    private FoodValues getFoodValues(Material mat) {
        switch (mat) {
            case COOKED_BEEF:
            case COOKED_PORKCHOP:
                return new FoodValues(15, 20, 2, 5, -5);
            case COOKED_CHICKEN:
            case COOKED_MUTTON:
            case COOKED_RABBIT:
                return new FoodValues(10, 15, 3, 3, -3);
            case BREAD:
            case BAKED_POTATO:
                return new FoodValues(20, 5, 3, 2, -5);
            case MELON_SLICE:
            case APPLE:
            case GOLDEN_APPLE:
            case GLOW_BERRIES:
                return new FoodValues(5, 1, 10, 0, 15);
            case SWEET_BERRIES:
                // ベリーはそこまで水分が多くない
                return new FoodValues(4, 1, 8, 0, 5);
            case CARROT:
            case POTATO:
            case BEETROOT:
                return new FoodValues(5, 2, 8, 1, 10);
            case MUSHROOM_STEW:
            case RABBIT_STEW:
            case BEETROOT_SOUP:
            case SUSPICIOUS_STEW:
                return new FoodValues(15, 10, 8, 5, 20);
            case DRIED_KELP:
                return new FoodValues(3, 1, 2, 10, -10);
            case ROTTEN_FLESH:
                return new FoodValues(3, 5, 0, 2, -5);
            case SPIDER_EYE:
                return new FoodValues(2, 3, 0, 1, -5);
            case COOKIE:
            case CAKE:
            case PUMPKIN_PIE:
                return new FoodValues(25, 2, 1, 1, -10);
            case HONEY_BOTTLE:
                return new FoodValues(8, 0, 5, 0, 5);
            case PUFFERFISH:
                return new FoodValues(3, 5, 0, 5, -10);
            case COOKED_COD:
            case COOKED_SALMON:
                return new FoodValues(8, 18, 3, 5, -3);
            case COD:
            case SALMON:
                return new FoodValues(4, 10, 2, 3, 0);
            case TROPICAL_FISH:
                return new FoodValues(2, 5, 2, 3, 0);
            default:
                return new FoodValues(8, 5, 3, 2, -3);
        }
    }

    private String formatMaterialName(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private class DecayRunnable extends BukkitRunnable {
        @Override
        public void run() {
            ConfigManager cfg = plugin.getConfigManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!com.realizascend.RealizAscend.isSurvival(player)) continue;
                PlayerData data = plugin.getDataManager().getData(player);

                double calorieEff = plugin.getSkillManager().getAbilityEffectValue(player, "CALORIE_EFFICIENCY");
                double calorieDecay = cfg.nutritionDecayRate * (2.0 - calorieEff);
                double hydrationEff = plugin.getSkillManager().getAbilityEffectValue(player, "HYDRATION_CONSUME");
                // 省エネ型/超代謝: カロリー・水分の総合節約
                double saveEff = plugin.getSkillManager().getAbilityEffectValue(player, "CALORIE_HYDRATION_SAVE");
                double allEff = plugin.getSkillManager().getAbilityEffectValue(player, "ALL_EFFICIENCY");
                calorieDecay *= (2.0 - saveEff) * (2.0 - allEff);
                hydrationEff *= saveEff;

                data.setCalories(data.getCalories() - calorieDecay);
                data.setProtein(data.getProtein() - cfg.nutritionDecayRate * (2.0 - allEff));
                data.setVitamins(data.getVitamins() - cfg.nutritionDecayRate * (2.0 - allEff));
                data.setSalt(data.getSalt() - cfg.nutritionDecayRate * (2.0 - allEff));

                double hydrationDecay = cfg.hydrationDecayRate * hydrationEff;
                if (data.getBodyTemperature() > 30.0) {
                    hydrationDecay *= 1.3 * plugin.getSkillManager().getAbilityEffectValue(player, "HOT_HYDRATION_COST");
                }
                if (data.getBodyTemperature() < 10.0) {
                    data.setCalories(data.getCalories() - calorieDecay
                        * plugin.getSkillManager().getAbilityEffectValue(player, "COLD_CALORIE_COST"));
                }
                if (player.isSprinting()) {
                    hydrationDecay *= 1.2;
                }
                data.setHydration(data.getHydration() - hydrationDecay);

                applyNutritionEffects(player, data, cfg);
                syncFoodLevel(player, data);
                showNutritionHud(player, data);
            }
        }
    }

    private class BoilData {
        long burnTicksAccumulated;
    }

    private class BoilCheckRunnable extends BukkitRunnable {
        @Override
        public void run() {
            List<Location> toRemove = new ArrayList<>();
            for (Map.Entry<Location, BoilData> entry : boilingFurnaces.entrySet()) {
                Location loc = entry.getKey();
                BoilData data = entry.getValue();

                if (loc.getWorld() == null || !(loc.getBlock().getState() instanceof Furnace furnace)) {
                    toRemove.add(loc);
                    continue;
                }

                FurnaceInventory furnaceInv = furnace.getInventory();
                ItemStack input = furnaceInv.getItem(0);
                if (input == null || !isRawWater(input)) {
                    toRemove.add(loc);
                    continue;
                }

                // ponytail: vanilla furnaces won't burn potions, so boil on elapsed time
                data.burnTicksAccumulated += 20;

                if (data.burnTicksAccumulated >= BOIL_DURATION) {
                    input.setAmount(input.getAmount() - 1);
                    if (input.getAmount() <= 0) {
                        furnaceInv.setItem(0, null);
                    }
                    ItemStack clean = createCleanWater();
                    ItemStack result = furnaceInv.getItem(2);
                    if (result == null || result.getType() == Material.AIR) {
                        furnaceInv.setItem(2, clean);
                    } else if (result.isSimilar(clean) && result.getAmount() < result.getMaxStackSize()) {
                        result.setAmount(result.getAmount() + 1);
                    } else {
                        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), clean);
                    }
                    if (input.getAmount() <= 0 || !isRawWater(input)) {
                        toRemove.add(loc);
                    } else {
                        data.burnTicksAccumulated = 0;
                    }
                }
            }
            for (Location loc : toRemove) {
                boilingFurnaces.remove(loc);
            }
        }
    }

    private void applyNutritionEffects(Player player, PlayerData data, ConfigManager cfg) {
        double balance = data.getNutritionBalance();

        if (balance > 70.0 && player.getHealth() < player.getMaxHealth()) {
            double healAmount = 0.5;
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));
        }

        if (data.getCalories() < cfg.nutritionWarningThreshold) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
        }
        if (data.getProtein() < cfg.nutritionWarningThreshold) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 0, false, false, true));
        }
        if (data.getVitamins() < cfg.nutritionWarningThreshold) {
            if (Math.random() < 0.15) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 200, 0, false, false, true));
            }
        }

        if (data.getCalories() < 10.0 || data.getProtein() < 10.0 || data.getVitamins() < 10.0) {
            player.damage(1.0);
        }
        if (data.getCalories() <= 0) {
            plugin.getCodexManager().unlockEntry(player, "starvation");
        }

        if (data.getHydration() < cfg.hydrationWarningThreshold) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0, false, false, true));
        }
        if (data.getHydration() <= 0) {
            player.damage(0.5);
            plugin.getCodexManager().unlockEntry(player, "dehydration");
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 1, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 1, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
        }
    }

    private void showNutritionHud(Player player, PlayerData data) {
        double balance = data.getNutritionBalance();
        ChatColor color;
        if (balance > 60) color = ChatColor.GREEN;
        else if (balance > 30) color = ChatColor.YELLOW;
        else color = ChatColor.RED;

        String bar = MessageUtil.buildBar(balance / 100.0, 10);
        String hydBar = MessageUtil.buildBar(data.getHydration() / 100.0, 10);

        MessageUtil.sendActionBar(player,
            color + "栄養: " + bar + " " + String.format("%.0f%%  ", balance) +
            ChatColor.AQUA + "水分: " + hydBar + " " + String.format("%.0f%%", data.getHydration()));
    }

    private static class FoodValues {
        final double calories, protein, vitamins, salt, hydration;
        FoodValues(double calories, double protein, double vitamins, double salt, double hydration) {
            this.calories = calories;
            this.protein = protein;
            this.vitamins = vitamins;
            this.salt = salt;
            this.hydration = hydration;
        }
    }
}
