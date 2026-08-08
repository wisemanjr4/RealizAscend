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
            sender.sendMessage(ChatColor.YELLOW + "/realiz reload " + ChatColor.GRAY + "- Reload config");
            sender.sendMessage(ChatColor.YELLOW + "/realiz info " + ChatColor.GRAY + "- Plugin info");
            sender.sendMessage(ChatColor.YELLOW + "/realiz codex " + ChatColor.GRAY + "- Open codex");
            sender.sendMessage(ChatColor.YELLOW + "/realiz skill " + ChatColor.GRAY + "- Open skill menu");
            sender.sendMessage(ChatColor.YELLOW + "/realiz reset <player> " + ChatColor.GRAY + "- Reset player data");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                if (!sender.hasPermission("realizascend.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "RealizAscend reloaded.");
                break;

            case "info":
                sender.sendMessage(ChatColor.GOLD + "RealizAscend v1.0.0");
                sender.sendMessage(ChatColor.GRAY + "Hardcore realistic survival plugin");
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    PlayerData data = plugin.getDataManager().getData(player);
                    sender.sendMessage(ChatColor.YELLOW + "Nutrition Balance: " + String.format("%.1f", data.getNutritionBalance()));
                    sender.sendMessage(ChatColor.YELLOW + "Hydration: " + String.format("%.1f", data.getHydration()));
                    sender.sendMessage(ChatColor.YELLOW + "Blood: " + String.format("%.1f", data.getBlood()));
                    sender.sendMessage(ChatColor.YELLOW + "Stamina: " + String.format("%.1f", data.getStamina()));
                    sender.sendMessage(ChatColor.YELLOW + "Stress: " + String.format("%.1f", data.getStress()));
                    sender.sendMessage(ChatColor.YELLOW + "Temperature: " + String.format("%.1f", data.getBodyTemperature()));
                    sender.sendMessage(ChatColor.YELLOW + "Season: " + plugin.getSeasonManager().getCurrentSeason());
                }
                break;

            case "codex":
                if (sender instanceof Player) {
                    plugin.getCodexManager().openCodex((Player) sender);
                }
                break;

            case "skill":
                if (sender instanceof Player) {
                    plugin.getSkillManager().openSkillMenu((Player) sender);
                }
                break;

            case "reset":
                if (!sender.hasPermission("realizascend.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /realiz reset <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                plugin.getDataManager().removeData(target.getUniqueId());
                plugin.getDataManager().getData(target);
                sender.sendMessage(ChatColor.GREEN + "Player data reset for " + target.getName());
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /realiz for help.");
                break;
        }
        return true;
    }
}
