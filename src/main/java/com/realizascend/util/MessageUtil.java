package com.realizascend.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class MessageUtil {

    public static void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    public static void sendCodexUnlock(Player player, String entry) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.BOLD + "Codex Unlocked!" + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.YELLOW + "\"" + entry + "\"");
        player.sendMessage("");
    }

    public static String formatStat(String label, double value, double max) {
        double pct = value / max;
        ChatColor color;
        if (pct > 0.6) color = ChatColor.GREEN;
        else if (pct > 0.3) color = ChatColor.YELLOW;
        else color = ChatColor.RED;
        String bar = buildBar(pct, 10);
        return color + label + ": " + bar + " " + String.format("%.0f", value);
    }

    public static String buildBar(double pct, int segments) {
        int filled = (int) Math.round(pct * segments);
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GREEN);
        for (int i = 0; i < filled; i++) sb.append("\u2588");
        sb.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < segments; i++) sb.append("\u2588");
        return sb.toString();
    }

    public static String buildBarYellow(double pct, int segments) {
        int filled = (int) Math.round(pct * segments);
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.YELLOW);
        for (int i = 0; i < filled; i++) sb.append("\u2588");
        sb.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < segments; i++) sb.append("\u2588");
        return sb.toString();
    }

    public static String getStressEmoji(double stress) {
        if (stress < 20) return "(-_-)";
        if (stress < 40) return "(^_^)";
        if (stress < 60) return "(o_o)";
        if (stress < 80) return "(x_x)";
        return "(T_T)";
    }
}
