package com.realizascend.modules.trap;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TrapManager extends RealizModule implements Listener {

    private static final String SPIKE = "SPIKE";
    private static final String BEAR = "BEAR";
    private static final String POISON = "POISON";

    private final NamespacedKey spikeKey;
    private final NamespacedKey bearKey;
    private final NamespacedKey poisonKey;
    private final NamespacedKey spikeRecipeKey;
    private final NamespacedKey bearRecipeKey;
    private final NamespacedKey poisonRecipeKey;

    private final Map<Location, String> traps = new HashMap<>();
    private final Map<Location, Double> damageMultipliers = new HashMap<>();
    private BukkitTask trapTask;

    public TrapManager(RealizAscend plugin) {
        super(plugin);
        spikeKey = new NamespacedKey(plugin, "trap_spike");
        bearKey = new NamespacedKey(plugin, "trap_bear");
        poisonKey = new NamespacedKey(plugin, "trap_poison");
        spikeRecipeKey = new NamespacedKey(plugin, "trap_spike_recipe");
        bearRecipeKey = new NamespacedKey(plugin, "trap_bear_recipe");
        poisonRecipeKey = new NamespacedKey(plugin, "trap_poison_recipe");
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerRecipes();
        trapTask = new TrapTask().runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (trapTask != null) {
            trapTask.cancel();
        }
        for (Location loc : traps.keySet()) {
            Block b = loc.getBlock();
            if (b.getType() == Material.STONE_PRESSURE_PLATE) {
                b.setType(Material.AIR);
            }
        }
        traps.clear();
        damageMultipliers.clear();
        List<NamespacedKey> toRemove = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof Keyed k) {
                NamespacedKey key = k.getKey();
                if (key.equals(spikeRecipeKey) || key.equals(bearRecipeKey) || key.equals(poisonRecipeKey)) {
                    toRemove.add(key);
                }
            }
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
    }

    private void registerRecipes() {
        // 各罠は材料タイプ集合が一意 (材料数だけで区別しない)
        // スパイク: 鉄塊+棒 (鉄インゴット+棒だと工具レシピと衝突するため鉄塊)
        tryAddRecipe(new ShapelessRecipe(spikeRecipeKey, createTrapItem(SPIKE))
            .addIngredient(Material.IRON_NUGGET)
            .addIngredient(Material.STICK));
        // ベア: 鉄+糸+レッドストーン (針の[鉄+糸]と材料数でなくレッドストーンの有無で区別)
        tryAddRecipe(new ShapelessRecipe(bearRecipeKey, createTrapItem(BEAR))
            .addIngredient(Material.IRON_INGOT)
            .addIngredient(Material.STRING)
            .addIngredient(Material.REDSTONE));
        tryAddRecipe(new ShapelessRecipe(poisonRecipeKey, createTrapItem(POISON))
            .addIngredient(Material.IRON_INGOT)
            .addIngredient(Material.SPIDER_EYE)
            .addIngredient(Material.STICK));
    }

    private void tryAddRecipe(Recipe recipe) {
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
        }
    }

    @EventHandler
    public void onTrapPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        ItemStack hand = event.getItem();
        if (hand == null) return;
        String type = getTrapType(hand);
        if (type == null) return;

        Player player = event.getPlayer();
        Location placeLoc;
        if (event.getBlockFace() == BlockFace.UP) {
            placeLoc = event.getClickedBlock().getLocation().add(0, 1, 0);
        } else {
            placeLoc = event.getClickedBlock().getLocation();
        }

        Block target = placeLoc.getBlock();
        if (target.getType() != Material.AIR) {
            player.sendMessage(ChatColor.RED + "ここには設置できない。");
            return;
        }

        event.setCancelled(true);
        target.setType(Material.STONE_PRESSURE_PLATE);
        traps.put(placeLoc, type);
        damageMultipliers.put(placeLoc, plugin.getSkillManager().getAbilityEffectValue(player, "TRAP_EFFECT"));

        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        boolean offhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        ItemStack held = offhand ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (held != null && held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else if (offhand) {
            inv.setItemInOffHand(null);
        } else {
            inv.setItemInMainHand(null);
        }
        player.sendMessage(ChatColor.GREEN + "罠を設置した。");
    }

    private String getTrapType(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(spikeKey, PersistentDataType.BYTE)) return SPIKE;
        if (pdc.has(bearKey, PersistentDataType.BYTE)) return BEAR;
        if (pdc.has(poisonKey, PersistentDataType.BYTE)) return POISON;
        return null;
    }

    private ItemStack createTrapItem(String type) {
        ItemStack item;
        String name;
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "地面に右クリックで設置。");
        switch (type) {
            case BEAR -> {
                item = new ItemStack(Material.IRON_NUGGET, 2);
                name = ChatColor.GOLD + "ベアトラップ";
                lore.add(ChatColor.DARK_GRAY + "鈍足III 5秒 + ダメージ3ハート");
            }
            case POISON -> {
                item = new ItemStack(Material.SPIDER_EYE);
                name = ChatColor.DARK_GREEN + "毒針トラップ";
                lore.add(ChatColor.DARK_GRAY + "毒II 5秒 + ダメージ1ハート");
            }
            default -> {
                item = new ItemStack(Material.IRON_INGOT);
                name = ChatColor.WHITE + "スパイクトラップ";
                lore.add(ChatColor.DARK_GRAY + "ダメージ4ハート + ノックバック");
            }
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        var pdc = meta.getPersistentDataContainer();
        switch (type) {
            case BEAR -> pdc.set(bearKey, PersistentDataType.BYTE, (byte) 1);
            case POISON -> pdc.set(poisonKey, PersistentDataType.BYTE, (byte) 1);
            default -> pdc.set(spikeKey, PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void triggerTrap(Location loc, String type, LivingEntity victim, double mult) {
        switch (type) {
            case BEAR -> {
                victim.damage(6.0 * mult);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 5 * 20, 2));
            }
            case POISON -> {
                victim.damage(2.0 * mult);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 5 * 20, 1));
            }
            default -> {
                victim.damage(8.0 * mult);
                org.bukkit.util.Vector knockback = victim.getLocation().toVector()
                    .subtract(loc.toVector().add(new org.bukkit.util.Vector(0.5, 0, 0.5))).normalize();
                victim.setVelocity(victim.getVelocity().add(knockback.setY(0.4).multiply(0.5)));
            }
        }
        World world = loc.getWorld();
        if (world != null) {
            world.playSound(loc, Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
        }
        loc.getBlock().setType(Material.AIR);
    }

    private class TrapTask extends BukkitRunnable {
        @Override
        public void run() {
            Iterator<Map.Entry<Location, String>> it = traps.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Location, String> entry = it.next();
                Location loc = entry.getKey();
                String type = entry.getValue();
                World world = loc.getWorld();
                Block block = loc.getBlock();
                if (world == null || block.getType() != Material.STONE_PRESSURE_PLATE) {
                    it.remove();
                    damageMultipliers.remove(loc);
                    continue;
                }

                Location center = loc.clone().add(0.5, 0.5, 0.5);
                for (Entity ent : world.getNearbyEntities(center, 0.5, 2, 0.5)) {
                    if (!(ent instanceof Player || ent instanceof Monster)) continue;
                    if (ent.getLocation().getBlock().getY() != loc.getBlockY()) continue;
                    triggerTrap(loc, type, (LivingEntity) ent,
                        damageMultipliers.getOrDefault(loc, 1.0));
                    it.remove();
                    damageMultipliers.remove(loc);
                    break;
                }
            }
        }
    }
}
