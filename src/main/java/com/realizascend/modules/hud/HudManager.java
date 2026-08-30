package com.realizascend.modules.hud;

import com.realizascend.RealizAscend;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HudManager extends RealizModule implements Listener {

    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private BukkitRunnable updateTask;

    public HudManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().hudEnabled) return;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            setupPlayerBoard(player);
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayerBoard(player);
                }
            }
        };
        updateTask.runTaskTimer(plugin, 20L, plugin.getConfigManager().hudUpdateInterval);
    }

    private void setupPlayerBoard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("realiz_hud", "dummy",
            ChatColor.GOLD + "" + ChatColor.BOLD + "RealizAscend");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        playerBoards.put(player.getUniqueId(), board);
    }

    private void updatePlayerBoard(Player player) {
        Scoreboard board = playerBoards.get(player.getUniqueId());
        if (board == null) {
            setupPlayerBoard(player);
            board = playerBoards.get(player.getUniqueId());
            if (board == null) return;
        }

        Objective objective = board.getObjective("realiz_hud");
        if (objective == null) {
            objective = board.registerNewObjective("realiz_hud", "dummy",
                ChatColor.GOLD + "" + ChatColor.BOLD + "RealizAscend");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : new java.util.ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }

        PlayerData data = plugin.getDataManager().getData(player);

        int line = 14;
        setScore(objective, ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━", line--);

        double blood = data.getBlood();
        String bloodBar = buildBar(blood, 100, 10);
        setScore(objective, ChatColor.RED + "\u2764 血液: " + ChatColor.WHITE + bloodBar
            + " §f" + formatValue(blood), line--);

        double hydration = data.getHydration();
        String hydrationBar = buildBar(hydration, 100, 10);
        setScore(objective, ChatColor.AQUA + "\uD83D\uDCA7 水分: " + ChatColor.WHITE + hydrationBar
            + " §f" + formatValue(hydration), line--);

        double stamina = data.getStamina();
        String staminaBar = buildBar(stamina, 100, 10);
        setScore(objective, ChatColor.YELLOW + "\u26A1 スタミナ: " + ChatColor.WHITE + staminaBar
            + " §f" + formatValue(stamina), line--);

        double nutrition = data.getNutritionBalance();
        setScore(objective, ChatColor.GREEN + "\uD83C\uDF3F 栄養: " + ChatColor.WHITE + formatValue(nutrition), line--);

        double stress = data.getStress();
        String stressEmoji = getStressEmoji(stress);
        setScore(objective, ChatColor.LIGHT_PURPLE + "\uD83D\uDE30 ストレス: " + ChatColor.WHITE
            + String.format("%.0f", stress) + "/100", line--);

        setScore(objective, ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━", line--);

        String seasonName = "Spring";
        try {
            seasonName = capitalize(plugin.getSeasonManager().getCurrentSeason().name());
        } catch (Exception ignored) {}
        setScore(objective, ChatColor.GOLD + "季節: " + ChatColor.WHITE + seasonName, line--);

        double temp = 20.0;
        try {
            temp = plugin.getDataManager().getData(player).getBodyTemperature();
        } catch (Exception ignored) {}
        setScore(objective, ChatColor.AQUA + "気温: " + ChatColor.WHITE + formatValue(temp) + "°C", line--);

        long dayCount = player.getWorld().getFullTime() / 24000;
        setScore(objective, ChatColor.DARK_GRAY + "日目: " + ChatColor.WHITE + dayCount, line--);

        setScore(objective, " ", line);
    }

    private void setScore(Objective objective, String text, int score) {
        Score s = objective.getScore(text);
        s.setScore(score);
    }

    private String buildBar(double value, double max, int length) {
        int filled = (int) Math.round((value / max) * length);
        filled = Math.max(0, Math.min(filled, length));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                sb.append(ChatColor.GREEN).append("|");
            } else {
                sb.append(ChatColor.GRAY).append("|");
            }
        }
        return sb.toString();
    }

    private String formatValue(double value) {
        return value % 1 == 0 ? String.valueOf((int) value) : String.format("%.1f", value);
    }

    private String getStressEmoji(double stress) {
        if (stress < 20) return ChatColor.GREEN + "(^-^)";
        if (stress < 40) return ChatColor.YELLOW + "(o_o)";
        if (stress < 60) return ChatColor.GOLD + "(>_<)";
        if (stress < 80) return ChatColor.RED + "(x_x)";
        return ChatColor.DARK_RED + "(;_;)";
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        String[] words = name.toLowerCase().replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1));
        }
        return sb.toString();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().hudEnabled) return;
        setupPlayerBoard(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playerBoards.remove(player.getUniqueId());
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        playerBoards.clear();
        PlayerJoinEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
    }
}
