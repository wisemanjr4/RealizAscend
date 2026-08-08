package com.realizascend.modules.medical;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MedicalManager extends RealizModule implements Listener {

    private final NamespacedKey bandageKey;
    private final NamespacedKey splintKey;
    private final NamespacedKey antisepticKey;
    private final NamespacedKey antibioticsKey;

    private final NamespacedKey bandageRecipeKey;
    private final NamespacedKey splintRecipeKey;
    private final NamespacedKey antisepticRecipeKey;
    private final NamespacedKey antibioticsRecipeKey;

    public MedicalManager(RealizAscend plugin) {
        super(plugin);
        bandageKey = new NamespacedKey(plugin, "medical_bandage");
        splintKey = new NamespacedKey(plugin, "medical_splint");
        antisepticKey = new NamespacedKey(plugin, "medical_antiseptic");
        antibioticsKey = new NamespacedKey(plugin, "medical_antibiotics");
        bandageRecipeKey = new NamespacedKey(plugin, "bandage_recipe");
        splintRecipeKey = new NamespacedKey(plugin, "splint_recipe");
        antisepticRecipeKey = new NamespacedKey(plugin, "antiseptic_recipe");
        antibioticsRecipeKey = new NamespacedKey(plugin, "antibiotics_recipe");
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerRecipes();
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        removeRecipes();
    }

    private void registerRecipes() {
        tryAddRecipe(new ShapelessRecipe(bandageRecipeKey, createBandage(2))
            .addIngredient(Material.PAPER).addIngredient(Material.STRING));
        tryAddRecipe(new ShapelessRecipe(splintRecipeKey, createSplint())
            .addIngredient(Material.STICK).addIngredient(Material.STRING));
        tryAddRecipe(new ShapelessRecipe(antisepticRecipeKey, createAntiseptic())
            .addIngredient(Material.GLASS_BOTTLE).addIngredient(Material.SUGAR).addIngredient(Material.CHARCOAL));
        tryAddRecipe(new ShapelessRecipe(antibioticsRecipeKey, createAntibiotics())
            .addIngredient(Material.GLASS_BOTTLE).addIngredient(Material.SUGAR).addIngredient(Material.RED_MUSHROOM));
    }

    private void tryAddRecipe(Recipe recipe) {
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
        }
    }

    private void removeRecipes() {
        NamespacedKey[] keys = {bandageRecipeKey, splintRecipeKey, antisepticRecipeKey, antibioticsRecipeKey};
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        List<NamespacedKey> toRemove = new ArrayList<>();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            for (NamespacedKey key : keys) {
                if (recipe instanceof org.bukkit.Keyed k && k.getKey().equals(key)) {
                    toRemove.add(key);
                }
            }
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
    }

    @EventHandler
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (isMedicalItem(item, bandageKey)) {
            event.setCancelled(true);
            useBandage(event.getPlayer());
        } else if (isMedicalItem(item, splintKey)) {
            event.setCancelled(true);
            useSplint(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrinkMedicine(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (isMedicalItem(item, antisepticKey)) {
            event.setCancelled(true);
            drinkAntiseptic(event.getPlayer());
        } else if (isMedicalItem(item, antibioticsKey)) {
            event.setCancelled(true);
            drinkAntibiotics(event.getPlayer());
        }
    }

    private void useBandage(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        boolean treatable = data.isHeadInjured() || data.isTorsoInjured() || data.isLegsInjured()
            || data.getBlood() < 90 || data.isInfected();

        if (treatable) {
            consumeOne(player);
            // 応急処置: 処置が速くなる / 神の手: 効果アップ
            double treatBoost = plugin.getSkillManager().getAbilityEffectValue(player, "ALL_TREAT_BOOST");
            double bandageSpeed = plugin.getSkillManager().getAbilityEffectValue(player, "BANDAGE_SPEED");
            int healSeconds = (int) Math.max(5, 15 / bandageSpeed);
            plugin.getRecoveryManager().addBloodRegen(player, 15 * treatBoost, healSeconds);
            plugin.getRecoveryManager().addInfectionCure(player, 20 * treatBoost, healSeconds);
            if (data.isHeadInjured()) plugin.getRecoveryManager().healInjury(player, "HEAD", healSeconds);
            if (data.isTorsoInjured()) plugin.getRecoveryManager().healInjury(player, "TORSO", healSeconds);
            if (data.isLegsInjured()) plugin.getRecoveryManager().healInjury(player, "LEGS", healSeconds);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
            player.sendMessage(ChatColor.GREEN + "包帯で傷を処置した。じわじわと回復する...");
        } else {
            player.sendMessage(ChatColor.GRAY + "処置すべき傷がない。");
        }
    }

    private void useSplint(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);
        if (data.isFractured()) {
            consumeOne(player);
            double treatBoost = plugin.getSkillManager().getAbilityEffectValue(player, "ALL_TREAT_BOOST");
            int seconds = (int) Math.max(10, 30 / treatBoost);
            plugin.getRecoveryManager().healFracture(player, seconds);
            player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f);
            player.sendMessage(ChatColor.GREEN + "添え木で骨折を固定した。" + seconds + "秒で治る見込みだ。");
        } else {
            player.sendMessage(ChatColor.GRAY + "骨折していない。");
        }
    }

    private void drinkAntiseptic(Player player) {
        consumeOne(player);
        // 薬学特化/神の手: 薬効上昇
        double medsBoost = plugin.getSkillManager().getAbilityEffectValue(player, "MEDS_BOOST")
            * plugin.getSkillManager().getAbilityEffectValue(player, "ALL_TREAT_BOOST");
        plugin.getRecoveryManager().addInfectionCure(player, 30 * medsBoost, 15);
        player.sendMessage(ChatColor.AQUA + "消毒液を飲んだ。感染の進行が徐々に抑えられる...");
    }

    private void drinkAntibiotics(Player player) {
        consumeOne(player);
        double medsBoost = plugin.getSkillManager().getAbilityEffectValue(player, "MEDS_BOOST")
            * plugin.getSkillManager().getAbilityEffectValue(player, "ALL_TREAT_BOOST");
        plugin.getRecoveryManager().addInfectionCure(player, 100 * medsBoost, 20);
        player.sendMessage(ChatColor.AQUA + "抗生物質を服用した。感染症がじわじわと治っていく...");
    }

    private void consumeOne(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();
        ItemStack target = null;
        if (main != null && isAnyMedical(main)) target = main;
        else if (off != null && isAnyMedical(off)) target = off;
        if (target == null) return;

        if (target.getAmount() > 1) {
            target.setAmount(target.getAmount() - 1);
        } else if (target == main) {
            inv.setItemInMainHand(null);
        } else {
            inv.setItemInOffHand(null);
        }
    }

    private boolean isAnyMedical(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(bandageKey, PersistentDataType.BYTE)
            || pdc.has(splintKey, PersistentDataType.BYTE)
            || pdc.has(antisepticKey, PersistentDataType.BYTE)
            || pdc.has(antibioticsKey, PersistentDataType.BYTE);
    }

    private boolean isMedicalItem(ItemStack item, NamespacedKey key) {
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private ItemStack createBandage(int count) {
        ItemStack item = new ItemStack(Material.PAPER, count);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "包帯");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "右クリックで出血・負傷を処置する");
        lore.add(ChatColor.GRAY + "感染の進行も少し抑える");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(bandageKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSplint() {
        ItemStack item = new ItemStack(Material.STICK);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "添え木");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "右クリックで骨折を固定する");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(splintKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAntiseptic() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionData(new PotionData(PotionType.WATER));
        meta.setDisplayName(ChatColor.AQUA + "消毒液");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "飲むと感染の進行を抑える");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(antisepticKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAntibiotics() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionData(new PotionData(PotionType.WATER));
        meta.setDisplayName(ChatColor.AQUA + "抗生物質");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "飲むと感染症を完治させる");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(antibioticsKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
