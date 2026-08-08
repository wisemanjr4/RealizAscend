package com.realizascend.modules.cookingstation;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CookingStationManager extends RealizModule implements Listener {

    private final NamespacedKey stationKey;
    private final NamespacedKey recipeKey;
    private final NamespacedKey dishKey;
    private final NamespacedKey calKey;
    private final NamespacedKey proKey;
    private final NamespacedKey vitKey;
    private final NamespacedKey saltKey;
    private final NamespacedKey hydKey;

    public CookingStationManager(RealizAscend plugin) {
        super(plugin);
        stationKey = new NamespacedKey(plugin, "cooking_station");
        recipeKey = new NamespacedKey(plugin, "cooking_station_recipe");
        dishKey = new NamespacedKey(plugin, "dish_item");
        calKey = new NamespacedKey(plugin, "dish_cal");
        proKey = new NamespacedKey(plugin, "dish_pro");
        vitKey = new NamespacedKey(plugin, "dish_vit");
        saltKey = new NamespacedKey(plugin, "dish_salt");
        hydKey = new NamespacedKey(plugin, "dish_hyd");
        CookingStationMenu.init(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerStationRecipe();
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        List<NamespacedKey> toRemove = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof org.bukkit.Keyed keyed && keyed.getKey().equals(recipeKey)) {
                toRemove.add(recipeKey);
            }
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
    }

    private void registerStationRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createStationItem());
        recipe.shape("SSS", "SIS", "SSS");
        recipe.setIngredient('S', Material.STONE);
        recipe.setIngredient('I', Material.IRON_INGOT);
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
        }
    }

    private ItemStack createStationItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "調理台");
        meta.setLore(List.of(ChatColor.GRAY + "右クリックで調理を開始する"));
        meta.getPersistentDataContainer().set(stationKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(stationKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        CookingStationMenu.open(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(CookingStationMenu.TITLE)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            event.setCancelled(true);
            return;
        }
        if (slot <= 3) {
            Bukkit.getScheduler().runTask(plugin,
                () -> CookingStationMenu.updateResult(event.getInventory(), player));
            return;
        }
        event.setCancelled(true);
        if (slot == 17) {
            CookingStationMenu.cook(event.getInventory(), player);
        } else if (slot == 18) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(dishKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getData(player);
        double mult = 1.0 + data.getTasteLevel() * 0.01;

        data.setCalories(data.getCalories()
            + meta.getPersistentDataContainer().getOrDefault(calKey, PersistentDataType.INTEGER, 0) * mult);
        data.setProtein(data.getProtein()
            + meta.getPersistentDataContainer().getOrDefault(proKey, PersistentDataType.INTEGER, 0) * mult);
        data.setVitamins(data.getVitamins()
            + meta.getPersistentDataContainer().getOrDefault(vitKey, PersistentDataType.INTEGER, 0) * mult);
        data.setSalt(data.getSalt()
            + meta.getPersistentDataContainer().getOrDefault(saltKey, PersistentDataType.INTEGER, 0) * mult);
        data.setHydration(data.getHydration()
            + meta.getPersistentDataContainer().getOrDefault(hydKey, PersistentDataType.INTEGER, 0) * mult);
        data.addTasteLevel(1);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            PlayerInventory inv = player.getInventory();
            ItemStack main = inv.getItemInMainHand();
            if (main.isSimilar(item)) {
                inv.setItemInMainHand(null);
            } else if (inv.getItemInOffHand().isSimilar(item)) {
                inv.setItemInOffHand(null);
            } else {
                inv.removeItem(item);
            }
        }

        player.sendMessage(ChatColor.GRAY + "美味しい! (味覚経験+1)");
    }
}
