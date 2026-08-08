package com.realizascend.modules.codex;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.Keyed;

import java.util.*;

public class CodexManager extends RealizModule implements Listener {

    private static final Map<String, String> entryDescriptions = new LinkedHashMap<>();

    static {
        entryDescriptions.put("punching_trees", "Bare hands against trees? That's a good way to get hurt. Use an axe to chop wood efficiently. Craft one with sticks and planks - oh wait, you need wood for that. Try finding some sticks on the ground or breaking leaves to get started.");
        entryDescriptions.put("first_death", "Death is not the end, but it's costly. Your health, hunger, hydration, and skills all take a hit. Find your corpse quickly to recover items, or have a teammate grab them for you.");
        entryDescriptions.put("first_craft", "Crafting is essential for survival. Better tools mean better quality items. Each crafting profession requires specific tools - check the skill menu for details.");
        entryDescriptions.put("first_cook", "Raw food is barely nutritious and can make you sick. Cook your meals for better nutrition. As your cooking skill grows, you'll unlock better recipes and higher quality meals.");
        entryDescriptions.put("season_change", "Seasons affect everything from crop growth to body temperature. Each season lasts 7 days. Prepare for winter by stockpiling preserved food and warm clothing.");
        entryDescriptions.put("starvation", "Your body is running out of fuel. Without calories, you can't maintain body temperature, stamina regeneration slows, and eventually your health will deteriorate. Eat something - anything!");
        entryDescriptions.put("dehydration", "Water is life. Without it, your body shuts down. Find fresh water sources, but remember - raw water carries infection risk. Boil it first or find clean sources.");
        entryDescriptions.put("bleeding", "You're losing blood. Apply bandages (paper + string) to stop bleeding. Severe wounds may require stitching. Untreated wounds can become infected, especially if dirty.");
        entryDescriptions.put("first_kill", "You've taken down your first hostile creature. Combat skills improve with practice. Different weapons have different reach and damage profiles. Watch your stamina during fights.");
    }

    private final Set<UUID> hasDiedBefore = new HashSet<>();
    private final Set<UUID> hasCraftedBefore = new HashSet<>();
    private final Set<UUID> hasCookedBefore = new HashSet<>();
    private final Set<UUID> hasKilledHostile = new HashSet<>();
    private final Set<UUID> hasExperiencedSeason = new HashSet<>();

    public CodexManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void unlockEntry(Player player, String entryKey) {
        PlayerData data = plugin.getDataManager().getData(player);
        if (data.hasCodexEntry(entryKey)) return;

        data.unlockCodexEntry(entryKey);
        String description = entryDescriptions.get(entryKey);
        if (description == null) return;

        TextComponent header = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Codex Unlocked! ===");
        player.spigot().sendMessage(header);

        TextComponent entryLine = new TextComponent(ChatColor.YELLOW + "\"" + description + "\"");
        player.spigot().sendMessage(entryLine);

        TextComponent hint = new TextComponent(ChatColor.GRAY + "Right-click your Codex book to read all entries.");
        hint.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/realiz codex"));
        hint.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to open Codex")));
        player.spigot().sendMessage(hint);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack[] contents = player.getInventory().getContents();
        boolean hasCodex = false;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null && "Codex".equals(meta.getTitle())) {
                    hasCodex = true;
                    break;
                }
            }
        }

        if (!hasCodex) {
            ItemStack codexBook = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) codexBook.getItemMeta();
            meta.setTitle("Codex");
            meta.setAuthor("RealizAscend");
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Your survival guide - new entries unlock as you learn"));
            meta.addPage(ChatColor.GOLD + "" + ChatColor.BOLD + "RealizAscend Codex\n\n"
                + ChatColor.RESET + ChatColor.GRAY + "Right-click to read.\n"
                + "Entries unlock as you survive and explore.");
            codexBook.setItemMeta(meta);
            player.getInventory().addItem(codexBook);
        }
    }

    @EventHandler
    public void onCodexClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null || !"Codex".equals(meta.getTitle())) return;

        event.setCancelled(true);
        openCodex(player);
    }

    public void openCodex(Player player) {
        PlayerData data = plugin.getDataManager().getData(player);

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Codex");
        meta.setAuthor("RealizAscend");

        List<String> pages = new ArrayList<>();

        pages.add(ChatColor.GOLD + "" + ChatColor.BOLD + "RealizAscend Codex\n\n"
            + ChatColor.RESET + ChatColor.GRAY + "Entries unlocked: "
            + ChatColor.WHITE + data.getCodexEntries().size() + "/" + entryDescriptions.size());

        for (Map.Entry<String, String> entry : entryDescriptions.entrySet()) {
            if (data.hasCodexEntry(entry.getKey())) {
                String displayKey = entry.getKey().replace("_", " ");
                String page = ChatColor.GREEN + "" + ChatColor.BOLD + displayKey.toUpperCase() + "\n\n"
                    + ChatColor.RESET + ChatColor.GRAY + entry.getValue();
                pages.add(page);
            }
        }

        meta.setPages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstDeath(PlayerDeathEvent event) {
        if (hasDiedBefore.add(event.getEntity().getUniqueId())) {
            unlockEntry(event.getEntity(), "first_death");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (hasCraftedBefore.add(player.getUniqueId())) {
                unlockEntry(player, "first_craft");
                discoverAllRecipes(player);
            }
        }
    }

    private void discoverAllRecipes(Player player) {
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        List<NamespacedKey> keys = new ArrayList<>();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof Keyed keyed) {
                keys.add(keyed.getKey());
            }
        }
        if (!keys.isEmpty()) {
            player.discoverRecipes(keys);
            player.sendMessage(ChatColor.GRAY + "All crafting recipes have been unlocked!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstCook(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item.getType().name().startsWith("COOKED_") || item.getType() == Material.BAKED_POTATO
            || item.getType() == Material.DRIED_KELP || item.getType() == Material.BREAD
            || item.getType() == Material.PUMPKIN_PIE || item.getType() == Material.MUSHROOM_STEW
            || item.getType() == Material.BEETROOT_SOUP || item.getType() == Material.RABBIT_STEW) {
            if (hasCookedBefore.add(player.getUniqueId())) {
                unlockEntry(player, "first_cook");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;

        org.bukkit.entity.EntityType type = event.getEntity().getType();
        if (type == org.bukkit.entity.EntityType.ZOMBIE || type == org.bukkit.entity.EntityType.SKELETON
            || type == org.bukkit.entity.EntityType.SPIDER || type == org.bukkit.entity.EntityType.CREEPER
            || type == org.bukkit.entity.EntityType.ENDERMAN || type == org.bukkit.entity.EntityType.WITCH
            || type == org.bukkit.entity.EntityType.BLAZE || type == org.bukkit.entity.EntityType.GHAST
            || type == org.bukkit.entity.EntityType.MAGMA_CUBE || type == org.bukkit.entity.EntityType.SLIME
            || type == org.bukkit.entity.EntityType.WITHER_SKELETON || type == org.bukkit.entity.EntityType.GUARDIAN
            || type == org.bukkit.entity.EntityType.ELDER_GUARDIAN || type == org.bukkit.entity.EntityType.EVOKER
            || type == org.bukkit.entity.EntityType.VINDICATOR || type == org.bukkit.entity.EntityType.PILLAGER
            || type == org.bukkit.entity.EntityType.RAVAGER || type == org.bukkit.entity.EntityType.VEX
            || type == org.bukkit.entity.EntityType.PHANTOM || type == org.bukkit.entity.EntityType.DROWNED
            || type == org.bukkit.entity.EntityType.HUSK || type == org.bukkit.entity.EntityType.STRAY
            || type == org.bukkit.entity.EntityType.PIGLIN || type == org.bukkit.entity.EntityType.PIGLIN_BRUTE
            || type == org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN || type == org.bukkit.entity.EntityType.HOGLIN
            || type == org.bukkit.entity.EntityType.ZOGLIN || type == org.bukkit.entity.EntityType.WARDEN
            || type == org.bukkit.entity.EntityType.SHULKER || type == org.bukkit.entity.EntityType.SILVERFISH
            || type == org.bukkit.entity.EntityType.ENDERMITE) {
            if (hasKilledHostile.add(event.getEntity().getKiller().getUniqueId())) {
                unlockEntry(event.getEntity().getKiller(), "first_kill");
            }
        }
    }

    @Override
    public void onDisable() {
        PlayerJoinEvent.getHandlerList().unregister(this);
        PlayerDeathEvent.getHandlerList().unregister(this);
        CraftItemEvent.getHandlerList().unregister(this);
        PlayerInteractEvent.getHandlerList().unregister(this);
        PlayerItemConsumeEvent.getHandlerList().unregister(this);
        EntityDeathEvent.getHandlerList().unregister(this);
    }
}
