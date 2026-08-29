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
        entryDescriptions.put("stress_management", "心が休まらない。どうやらストレスが溜まっているようだ。明るい所で過ごし、火のそばで休み、満ち足りた状態を保ち、よく眠ると楽になる。暗い所や敵の気配、厳しい寒暑、痛みや寝不足が続くと悪化する。酒を飲めば一時的に楽になるが、喉は渇きやすくなる。");
    }

    // 常時閲覧できる生存知識ページ (経験で解放されるエントリとは別)
    private static final List<String> REFERENCE_PAGES = new ArrayList<>();

    private static final Map<String, String> ENTRY_TITLES = new LinkedHashMap<>();

    static {
        ENTRY_TITLES.put("punching_trees", "拳で木を叩いた");
        ENTRY_TITLES.put("first_death", "初めての死");
        ENTRY_TITLES.put("first_craft", "初めてのクラフト");
        ENTRY_TITLES.put("first_cook", "初めての料理");
        ENTRY_TITLES.put("season_change", "季節の移り変わり");
        ENTRY_TITLES.put("starvation", "飢餓");
        ENTRY_TITLES.put("dehydration", "脱水");
        ENTRY_TITLES.put("bleeding", "失血");
        ENTRY_TITLES.put("first_kill", "初めての戦い");
        ENTRY_TITLES.put("stress_management", "心のケア");
    }

    static {
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "心の持ちよう\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "心が休まらない時は、\n"
            + "明るい所で過ごすと落ち着く。\n"
            + "焚き火のそばに居ると\n"
            + "心が温まる気がする。\n\n"
            + "満ち足りていれば平気だし、\n"
            + "ぐっすり眠れば元に戻る。\n"
            + "酒を飲めば少し楽になるが、\n"
            + "喉は渇きやすくなる。\n\n"
            + "暗い所や物音、極端な寒暑、\n"
            + "痛みや眠れない夜が続くと\n"
            + "心がすり減っていく。");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "寒さと暑さ\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "寒いと感じたら、火のそばで\n"
            + "温まるか、温かい物を食べると\n"
            + "体が温まる。\n\n"
            + "暑い日は水を多めに飲み、\n"
            + "涼しい所で休むと楽だ。\n\n"
            + "着る物の素材や重さで、\n"
            + "暑さ寒さの感じ方が変わる。\n"
            + "砂漠で重装備は身を滅ぼす。\n\n"
            + "震えが止まらない、\n"
            + "息が苦しいほどなら\n"
            + "命に関わることもある。");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "腹ごしらえ\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "腹が減ると力が出ない。\n"
            + "肉・野菜・塩気を\n"
            + "バランスよく食べるのが\n"
            + "体には一番いい気がする。\n\n"
            + "生の水はそのまま飲むと\n"
            + "腹を壊すことがある。\n"
            + "沸かすか、ろ過すれば安心だ。\n\n"
            + "生の肉も危ない。火を通そう。\n"
            + "燻製や塩漬けは長持ちする。");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "傷の手当て\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "受けた傷の場所によって\n"
            + "具合が変わる。\n"
            + "頭をやられれば目まいがし、\n"
            + "胴をやられれば内側から\n"
            + "血が出る気がする。\n"
            + "脚をやられれば動けなくなり、\n"
            + "ひどい時は骨まで折れる。\n\n"
            + "血が出たら、紙と糸で\n"
            + "巻くと止血になる。\n"
            + "骨が折れたら、棒と糸で\n"
            + "固定すると具合がいい。\n\n"
            + "傷が化膿して熱を持つようなら、\n"
            + "瓶と砂糖に何かを混ぜて\n"
            + "薬らしきものを作れそうだ。\n"
            + "炭を混ぜれば消毒に、\n"
            + "赤い茸ならもっと強い薬に。");
        REFERENCE_PAGES.add(ChatColor.GOLD + "" + ChatColor.BOLD + "休み方と荷物\n\n"
            + ChatColor.RESET + ChatColor.GRAY
            + "眠くなったら寝るのが\n"
            + "一番いい。\n"
            + "ぐっすり眠れば疲れも取れて、\n"
            + "体も元に戻る。\n\n"
            + "荷物を運びすぎると\n"
            + "動きが鈍くなる。\n"
            + "重すぎると跳べなくなる。\n"
            + "身軽な方が長く生きられる。");
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
        hint.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("クリックで開く")));
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
                if (meta != null && "コーデックス".equals(meta.getTitle())) {
                    hasCodex = true;
                    break;
                }
            }
        }

        if (!hasCodex) {
            ItemStack codexBook = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) codexBook.getItemMeta();
            meta.setTitle("コーデックス");
            meta.setAuthor("RealizAscend");
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "あなたの生存ガイド - 経験とともに項目が解放される"));
            meta.addPage(ChatColor.GOLD + "" + ChatColor.BOLD + "RealizAscend コーデックス\n\n"
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
        meta.setTitle("コーデックス");
        meta.setAuthor("RealizAscend");

        List<String> pages = new ArrayList<>();

        pages.add(ChatColor.GOLD + "" + ChatColor.BOLD + "RealizAscend コーデックス\n\n"
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
                String displayKey = ENTRY_TITLES.getOrDefault(entry.getKey(), entry.getKey());
                String page = ChatColor.GREEN + "" + ChatColor.BOLD + displayKey + "\n\n"
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
