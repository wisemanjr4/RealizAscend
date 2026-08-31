package com.realizascend.modules.food;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Smoker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FoodManager extends RealizModule implements Listener {

    private static final long RAW_LIFE = 45 * 60 * 1000L;
    private static final long SMOKED_LIFE = 3 * 60 * 60 * 1000L;
    private static final long SALTED_LIFE = 6 * 60 * 60 * 1000L;
    private static final long BOTH_LIFE = 9 * 60 * 60 * 1000L;
    private static final long SPOIL_INTERVAL = 3000L;

    private final NamespacedKey cookedTimeKey;
    private final NamespacedKey preservedKey;
    private final NamespacedKey saltKey;
    private final NamespacedKey saltRecipeKey;

    private BukkitTask spoilTask;

    public FoodManager(RealizAscend plugin) {
        super(plugin);
        cookedTimeKey = new NamespacedKey(plugin, "cooked_time");
        preservedKey = new NamespacedKey(plugin, "preserved");
        saltKey = new NamespacedKey(plugin, "salt_item");
        saltRecipeKey = new NamespacedKey(plugin, "salt_recipe");
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tryAddRecipe(new ShapelessRecipe(saltRecipeKey, createSalt(4)).addIngredient(Material.KELP));
        spoilTask = new SpoilTask().runTaskTimer(plugin, SPOIL_INTERVAL, SPOIL_INTERVAL);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (spoilTask != null) {
            spoilTask.cancel();
        }
        List<NamespacedKey> toRemove = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof org.bukkit.Keyed k && k.getKey().equals(saltRecipeKey)) {
                toRemove.add(saltRecipeKey);
            }
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
    }

    private void tryAddRecipe(Recipe recipe) {
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
        }
    }

    public void markCooked(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(cookedTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        item.setItemMeta(meta);
    }

    public void markPreserved(ItemStack item, String type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(preservedKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCookExtract(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof FurnaceInventory fi)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;
        if (!isEdible(result.getType())) return;

        boolean smoked = fi.getHolder() instanceof Smoker;
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(cookedTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        if (smoked) {
            meta.getPersistentDataContainer().set(preservedKey, PersistentDataType.STRING, "SMOKED");
            String baseName = meta.hasDisplayName() ? meta.getDisplayName() : readableName(result.getType());
            meta.setDisplayName(ChatColor.GOLD + "燻製の" + ChatColor.RESET + baseName);
        }
        result.setItemMeta(meta);
    }

    @EventHandler
    public void onSaltUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (hand == null || !isSalt(hand)) return;

        Player player = event.getPlayer();
        ItemStack[] contents = player.getInventory().getContents();
        int foodSlot = -1;
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && hasCookedTime(contents[i])) {
                foodSlot = i;
                break;
            }
        }

        if (foodSlot == -1) {
            player.sendMessage(ChatColor.GRAY + "塩漬けできる調理済み食品がない。");
            return;
        }

        event.setCancelled(true);
        ItemStack food = contents[foodSlot];
        ItemMeta meta = food.getItemMeta();
        String preserved = meta.getPersistentDataContainer().get(preservedKey, PersistentDataType.STRING);
        String newPreserved = "SMOKED".equals(preserved) ? "SMOKED_SALTED" : "SALTED";
        meta.getPersistentDataContainer().set(preservedKey, PersistentDataType.STRING, newPreserved);
        String baseName = meta.hasDisplayName() ? meta.getDisplayName() : readableName(food.getType());
        meta.setDisplayName(ChatColor.WHITE + "塩漬けの" + ChatColor.RESET + baseName);
        food.setItemMeta(meta);

        boolean offhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else if (offhand) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.sendMessage(ChatColor.GREEN + "食品を塩漬けにした。保存期間が大幅に延びた。");
    }

    private boolean isEdible(Material mat) {
        return mat.isEdible();
    }

    private boolean hasCookedTime(ItemStack item) {
        return item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(cookedTimeKey, PersistentDataType.LONG);
    }

    private boolean isSalt(ItemStack item) {
        return item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(saltKey, PersistentDataType.BYTE);
    }

    private String readableName(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private ItemStack createSalt(int count) {
        ItemStack salt = new ItemStack(Material.SUGAR, count);
        ItemMeta meta = salt.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "塩");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "右クリックで調理済み食品を塩漬けにする");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(saltKey, PersistentDataType.BYTE, (byte) 1);
        salt.setItemMeta(meta);
        return salt;
    }

    public boolean isSalted(ItemStack item) {
        String preserved = getPreserved(item);
        return "SALTED".equals(preserved) || "SMOKED_SALTED".equals(preserved);
    }

    public boolean isSmoked(ItemStack item) {
        String preserved = getPreserved(item);
        return "SMOKED".equals(preserved) || "SMOKED_SALTED".equals(preserved);
    }

    private String getPreserved(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(preservedKey, PersistentDataType.STRING);
    }

    private void updateFreshnessLore(ItemStack item, String preserved, long created) {
        long shelfLife = getShelfLife(preserved);
        long remaining = shelfLife - (System.currentTimeMillis() - created);
        double ratio = (double) remaining / shelfLife;

        ChatColor freshnessColor;
        String freshnessText;
        if (ratio > 0.5) {
            freshnessColor = ChatColor.GREEN;
            freshnessText = "新鮮";
        } else if (ratio >= 0.2) {
            freshnessColor = ChatColor.YELLOW;
            freshnessText = "まあまあ";
        } else {
            freshnessColor = ChatColor.RED;
            freshnessText = "傷みかけ";
        }

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> {
            String stripped = ChatColor.stripColor(line);
            return stripped != null
                && (stripped.startsWith("鮮度:") || stripped.startsWith("賞味期限まで:"));
        });
        lore.add(freshnessColor + "鮮度: " + freshnessText);
        lore.add(ChatColor.GRAY + "賞味期限まで: " + formatDuration(remaining));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private String formatDuration(long millis) {
        long minutes = Math.max(0, millis / 60000);
        long hours = minutes / 60;
        minutes %= 60;
        if (hours > 0) return hours + "時間" + minutes + "分";
        return minutes + "分";
    }

    private long getShelfLife(String preserved) {
        if (preserved == null) return RAW_LIFE;
        switch (preserved) {
            case "SMOKED": return SMOKED_LIFE;
            case "SALTED": return SALTED_LIFE;
            case "SMOKED_SALTED": return BOTH_LIFE;
            case "DRIED": return 12 * 60 * 60 * 1000L;
            default: return RAW_LIFE;
        }
    }

    private class SpoilTask extends BukkitRunnable {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!com.realizascend.RealizAscend.isSurvival(player)) continue;
                org.bukkit.inventory.PlayerInventory inv = player.getInventory();
                List<ItemStack> all = new ArrayList<>();
                all.addAll(java.util.Arrays.asList(inv.getContents()));
                all.addAll(java.util.Arrays.asList(inv.getArmorContents()));
                all.add(inv.getItemInOffHand());

                for (int i = 0; i < all.size(); i++) {
                    ItemStack item = all.get(i);
                    if (item == null || item.getType() == Material.AIR) continue;
                    if (!item.hasItemMeta()) continue;
                    Long created = item.getItemMeta().getPersistentDataContainer()
                        .get(cookedTimeKey, PersistentDataType.LONG);
                    if (created == null) continue;

                    String preserved = item.getItemMeta().getPersistentDataContainer()
                        .get(preservedKey, PersistentDataType.STRING);
                    long shelfLife = getShelfLife(preserved);
                    updateFreshnessLore(item, preserved, created);
                    if (now - created > shelfLife) {
                        ItemStack rotten = new ItemStack(Material.ROTTEN_FLESH, item.getAmount());
                        ItemMeta rm = rotten.getItemMeta();
                        rm.setDisplayName(ChatColor.DARK_GRAY + "腐った食べ物");
                        rm.setLore(List.of(ChatColor.GRAY + "食べるべきではない..."));
                        rotten.setItemMeta(rm);
                        setSlot(player, inv, i, rotten);
                        player.sendMessage(ChatColor.RED + "所持中の食品が腐ってしまった!");
                    } else if (now - created > shelfLife * 4 / 5) {
                        long remaining = shelfLife - (now - created);
                        player.sendMessage(ChatColor.RED + "食品が傷みかけている! (残り"
                            + formatDuration(remaining) + ")");
                    }
                }
            }
        }

        private void setSlot(Player player, org.bukkit.inventory.PlayerInventory inv, int index, ItemStack item) {
            int contentSize = inv.getContents().length;
            if (index < contentSize) {
                inv.setItem(index, item);
            } else if (index < contentSize + inv.getArmorContents().length) {
                int armorIndex = index - contentSize;
                inv.getArmorContents()[armorIndex] = item;
                // armor contents array is a copy; set back via the proper setter
                inv.setArmorContents(inv.getArmorContents());
            } else {
                inv.setItemInOffHand(item);
            }
        }
    }
}
