package com.realizascend.modules.cookingstation;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CookingStationManager extends RealizModule implements Listener {

    private final NamespacedKey stationKey;
    private final NamespacedKey dishKey;
    private final NamespacedKey calKey;
    private final NamespacedKey proKey;
    private final NamespacedKey vitKey;
    private final NamespacedKey saltKey;
    private final NamespacedKey hydKey;

    private final NamespacedKey stationRecipeKey;

    // 設置された調理台の管理
    private final Set<Location> stations = new java.util.HashSet<>();
    private final Map<Location, Inventory> stationGUIs = new HashMap<>();
    private final Map<UUID, Location> openStations = new HashMap<>();

    public CookingStationManager(RealizAscend plugin) {
        super(plugin);
        stationKey = new NamespacedKey(plugin, "cooking_station");
        stationRecipeKey = new NamespacedKey(plugin, "cooking_station_recipe");
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
            if (recipe instanceof org.bukkit.Keyed keyed && keyed.getKey().equals(stationRecipeKey)) {
                toRemove.add(stationRecipeKey);
            }
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
        stationGUIs.clear();
        stations.clear();
        openStations.clear();
    }

    private void registerStationRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(stationRecipeKey, createStationItem());
        recipe.shape("SSS", "SIS", "SSS");
        recipe.setIngredient('S', Material.STONE);
        recipe.setIngredient('I', Material.IRON_INGOT);
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
        }
    }

    private ItemStack createStationItem() {
        ItemStack item = new ItemStack(Material.SMOKER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "調理台");
        meta.setLore(List.of(ChatColor.GRAY + "地面に設置して右クリック"));
        meta.getPersistentDataContainer().set(stationKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ===== 設置 =====
    @EventHandler
    public void onPlaceStation(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(stationKey, PersistentDataType.BYTE)) return;

        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (target.getType() != Material.AIR) return;

        event.setCancelled(true);
        target.setType(Material.SMOKER);
        stations.add(target.getLocation());

        PlayerInventory inv = event.getPlayer().getInventory();
        ItemStack held = inv.getItemInMainHand();
        if (held.getType() == Material.SMOKER || held.hasItemMeta()) {
            // 手に持っているのが調理台なら消費
        }
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                inv.setItemInOffHand(null);
            } else {
                inv.setItemInMainHand(null);
            }
        }
        event.getPlayer().sendMessage(ChatColor.GREEN + "調理台を設置した。右クリックで調理を開始。");
    }

    // ===== 設置済み調理台を右クリックで GUI オープン =====
    @EventHandler
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SMOKER) return;
        if (!stations.contains(block.getLocation())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Location loc = block.getLocation();

        Inventory gui = stationGUIs.computeIfAbsent(loc,
            k -> CookingStationMenu.createGUI());
        CookingStationMenu.refreshDishes(gui, player);
        CookingStationMenu.open(player, gui);
    }

    // ===== 調理台を壊したら中身をドロップ =====
    @EventHandler
    public void onStationBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!stations.contains(loc)) return;

        event.setCancelled(true);
        Inventory gui = stationGUIs.remove(loc);
        if (gui != null) {
            for (int i = 4; i <= 7; i++) {
                ItemStack item = gui.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), item);
                }
            }
        }
        stations.remove(loc);
        event.getPlayer().sendMessage(ChatColor.YELLOW + "調理台を回収した。");
        loc.getBlock().setType(Material.AIR);
        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), createStationItem());
    }

    // ===== GUI クリック処理 =====
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(CookingStationMenu.TITLE)) return;

        int slot = event.getRawSlot();

        // プレイヤーインベントリ (slot >= 54) は自由に操作させる
        if (slot >= 54) return;

        // 調理GUI内
        if (slot <= 3) {
            // 材料スロットは自由に出し入れOK、結果を更新
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

    // ===== GUIを閉じたら材料を返却 =====
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(CookingStationMenu.TITLE)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        Location stationLoc = openStations.remove(player.getUniqueId());
        if (stationLoc == null) return;

        // 材料スロットのアイテムを返却
        for (int i = 0; i <= 3; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
                inv.setItem(i, null);
            }
        }
    }

    // ===== 料理を食べた時の処理 =====
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
        // 飽きシステム: 同じ料理を続けると栄養効率が下がる
        String dishName = meta.getDisplayName();
        double varietyMult = plugin.getNutritionManager().getFoodVarietyMultiplier(player, dishName);
        mult *= varietyMult;

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

        // event.getItem()はコピーの可能性があるため、インベントリから確実に1つ消費する
        consumeOne(player);

        player.sendMessage(ChatColor.GRAY + "美味しい! (味覚経験+1)");
    }

    private boolean isDishItem(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(dishKey, PersistentDataType.BYTE);
    }

    private void consumeOne(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();

        if (isDishItem(main)) {
            if (main.getAmount() > 1) main.setAmount(main.getAmount() - 1);
            else inv.setItemInMainHand(null);
            return;
        }
        if (isDishItem(off)) {
            if (off.getAmount() > 1) off.setAmount(off.getAmount() - 1);
            else inv.setItemInOffHand(null);
            return;
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isDishItem(s)) {
                if (s.getAmount() > 1) s.setAmount(s.getAmount() - 1);
                else inv.setItem(i, null);
                return;
            }
        }
    }
}