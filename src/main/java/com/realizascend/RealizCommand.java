package com.realizascend;

import com.realizascend.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RealizCommand implements CommandExecutor {

    private final RealizAscend plugin;

    public RealizCommand(RealizAscend plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== RealizAscend ===");
            sender.sendMessage(ChatColor.YELLOW + "/realiz reload " + ChatColor.GRAY + "- 設定を再読み込み");
            sender.sendMessage(ChatColor.YELLOW + "/realiz info " + ChatColor.GRAY + "- プラグイン情報");
            sender.sendMessage(ChatColor.YELLOW + "/realiz codex " + ChatColor.GRAY + "- コーデックスを開く");
            sender.sendMessage(ChatColor.YELLOW + "/realiz skill " + ChatColor.GRAY + "- スキルメニューを開く");
            sender.sendMessage(ChatColor.YELLOW + "/realiz status " + ChatColor.GRAY + "- 現在の異常状態を確認");
            sender.sendMessage(ChatColor.YELLOW + "/realiz reset <player> " + ChatColor.GRAY + "- プレイヤーデータをリセット");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                if (!sender.hasPermission("realizascend.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "RealizAscend を再読み込みしました。");
                break;

            case "info":
                sender.sendMessage(ChatColor.GOLD + "RealizAscend v1.0.0");
                sender.sendMessage(ChatColor.GRAY + "Hardcore realistic survival plugin");
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    PlayerData data = plugin.getDataManager().getData(player);
                    sender.sendMessage(ChatColor.YELLOW + "栄養バランス: " + String.format("%.1f", data.getNutritionBalance()));
                    sender.sendMessage(ChatColor.YELLOW + "水分: " + String.format("%.1f", data.getHydration()));
                    sender.sendMessage(ChatColor.YELLOW + "血液: " + String.format("%.1f", data.getBlood()));
                    sender.sendMessage(ChatColor.YELLOW + "スタミナ: " + String.format("%.1f", data.getStamina()));
                    sender.sendMessage(ChatColor.YELLOW + "ストレス: " + String.format("%.1f", data.getStress()));
                    sender.sendMessage(ChatColor.YELLOW + "気温: " + String.format("%.1f", data.getBodyTemperature()));
                    sender.sendMessage(ChatColor.YELLOW + "季節: " + plugin.getSeasonManager().getCurrentSeason());
                }
                break;

            case "codex":
                if (sender instanceof Player) {
                    plugin.getCodexManager().openCodex((Player) sender);
                }
                break;

            case "status":
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    java.util.List<String> conditions = plugin.getStatusManager().getActiveConditions(player);
                    player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== 現在の状態 ===");
                    if (conditions.isEmpty()) {
                        player.sendMessage(ChatColor.GREEN + "特に異常なし。元気だ。");
                    } else {
                        for (String condition : conditions) {
                            String reason = plugin.getStatusManager().getReason(condition);
                            player.sendMessage(ChatColor.YELLOW + condition
                                + (reason.isEmpty() ? "" : ChatColor.GRAY + " (" + reason + ")"));
                        }
                    }
                }
                break;

            case "skill":
                if (sender instanceof Player) {
                    plugin.getSkillManager().openSkillMenu((Player) sender);
                }
                break;

            case "reset":
                if (!sender.hasPermission("realizascend.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /realiz reset <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません。");
                    return true;
                }
                plugin.getDataManager().removeData(target.getUniqueId());
                plugin.getDataManager().getData(target);
                sender.sendMessage(ChatColor.GREEN + "プレイヤーデータをリセットしました: " + target.getName());
                break;

            default:
                sender.sendMessage(ChatColor.RED + "不明なサブコマンド。/realiz でヘルプを確認。");
                break;
        }
        return true;
    }
}
