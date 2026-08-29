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
        entryDescriptions.put("punching_trees", "素手で木を叩くのは危険。斧をクラフトして木材を集めよう。まずは落ちている棒や、葉を壊して材料を集めることから始まる。");
        entryDescriptions.put("first_death", "死は終わりではないが代償は重い。HP・空腹・水分・スキルにペナルティ。素早く死体を回収するか、仲間に回収してもらおう。");
        entryDescriptions.put("first_craft", "クラフトは生存の基本。道具が良ければ成果物の品質も上がる。クラフトにはジャンルごとの工具が必要な場合がある。");
        entryDescriptions.put("first_cook", "生の食材は栄養が乏しく、病気になることも。火を通して調理しよう。料理スキルが上がるほど高品質な料理が作れる。");
        entryDescriptions.put("season_change", "季節は作物の成長から体温まですべてに影響する。各季節は7日間。冬に備えて保存食を備蓄し、暖かい服装を用意しよう。");
        entryDescriptions.put("starvation", "体の燃料が尽きかけている。カロリーがなければ体温維持もスタミナ回復もできない。何か食べよう。");
        entryDescriptions.put("dehydration", "水は命。水分が尽きると体は機能しない。安全な水源を探そう。ただし生水は感染リスクがある。煮沸するか浄化しよう。");
        entryDescriptions.put("bleeding", "血液が減っている。包帯(紙+糸)で止血しよう。深い傷は縫合が必要なことも。手当てしないと感染する恐れがある。");
        entryDescriptions.put("first_kill", "初めて敵性生物を倒した。戦闘スキルは経験で向上する。武器によってリーチやダメージ特性が異なる。戦闘中のスタミナ管理を忘れずに。");
        entryDescriptions.put("stress_management", "ストレスが高まっている。下げるには、明るい場所に居る・焚き火やかまどのそばで休む・栄養バランスと水分を保つ・よく眠る・酒を飲む。暗い場所や敵のそば、極端な温度はストレスを上げる。");
    }

    // 常時閲覧できる生存知識ページ (経験で解放されるエントリとは別)
    private static final List<String> REFERENCE_PAGES = new ArrayList<>();

    static {
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "生存知識: ストレス\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "ストレスは暗い場所・敵のそば・極端な温度・睡眠不足・負傷で上がる。\n\n"
            + "下げるには:\n・明るい場所に居る\n・焚き火やかまどのそばで休む\n・栄養バランスと水分を保つ\n・よく眠る\n・酒を飲む");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "生存知識: 体温\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "寒い時は焚き火のそばや屋内で過ごし、熱い料理を食べる。\n"
            + "暑い時は日陰に入り、通気の良い装備に着替え、冷たい物を食べる。\n\n"
            + "防具には保温値と遮熱値があり、砂漠で重装備は逆効果。\n"
            + "体温が危険域に達するとHPが減る。");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "生存知識: 栄養と水分\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "カロリー・タンパク質・ビタミン・塩分をバランス良く摂ろう。\n"
            + "・生水は煮沸するかフィルターで浄化\n"
            + "・生肉は食中毒の恐れがある\n"
            + "・燻製・塩漬け・乾燥は保存が利く\n"
            + "・塩分過多は喉が渇きやすくなる");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "生存知識: 負傷と感染\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "負傷は放置すると危険。適切に手当てしよう。\n"
            + "・出血・負傷 → 包帯(紙+糸)\n"
            + "・骨折 → 添え木(棒+糸)\n"
            + "・感染 → 消毒液(瓶+砂糖+木炭) 抗生物質(瓶+砂糖+赤キノコ)\n"
            + "・負傷したまま水や土に触れると感染しやすい");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "生存知識: 疲労・睡眠・重量\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "疲労が貯まると眠くなる。疲労が30以上で睡眠可能。\n"
            + "眠ると疲労が回復し、健康度も少し戻る。\n\n"
            + "重量に注意: オーバーで鈍足、重度オーバーでジャンプ不可。\n"
            + "スキルの筋力や工具で上限を上げられる。");
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

        TextComponent header = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "=== コーデックス解放! ===");
        player.spigot().sendMessage(header);

        TextComponent entryLine = new TextComponent(ChatColor.YELLOW + "\"" + description + "\"");
        player.spigot().sendMessage(entryLine);

        TextComponent hint = new TextComponent(ChatColor.GRAY + "コーデックス本を右クリックして全項目を読もう。");
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
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "あなたの生存ガイド - 経験とともに項目が解放される"));
            meta.addPage(ChatColor.GOLD + "" + ChatColor.BOLD + "RealizAscend Codex\n\n"
                + ChatColor.RESET + ChatColor.GRAY + "右クリックで読む。\n"
                + "経験とともに項目が解放される。");
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
            + ChatColor.RESET + ChatColor.GRAY + "解放済みエントリ: "
            + ChatColor.WHITE + data.getCodexEntries().size() + "/" + entryDescriptions.size() + "\n\n"
            + ChatColor.GRAY + "冒頭の生存知識はいつでも読める。\n"
            + "エントリは経験とともに解放される。");

        // 常時閲覧できる生存知識ページ
        for (String refPage : REFERENCE_PAGES) {
            pages.add(refPage);
        }

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
            player.sendMessage(ChatColor.GRAY + "すべてのクラフトレシピが解放された!");
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
