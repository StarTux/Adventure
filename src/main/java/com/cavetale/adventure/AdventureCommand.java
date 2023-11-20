package com.cavetale.adventure;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class AdventureCommand implements CommandExecutor {
    protected final AdventurePlugin plugin;

    protected void enable() {
        plugin.getCommand("adventure").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if ("quit".equals(command)) {
            if (!plugin.applyAdventure(player.getWorld(), adv -> {
                        adv.leavePlayer(player);
                    })) {
                player.sendMessage(text("No map!", RED));
            }
        } else if ("finish".equals(command)) {
            if (!player.isOp()) return false;
            if (!plugin.applyAdventure(player.getWorld(), adv -> {
                        adv.setFinished(player.getUniqueId());
                        adv.recordPlayerScore(player);
                        if (adv.winLocation != null) player.teleport(adv.winLocation);
                        adv.winCounters.put(player.getUniqueId(), 0);
                        player.getInventory().setItem(8, adv.exitItem.clone());
                        player.getInventory().clear();
                    })) {
                player.sendMessage(text("No map!", RED));
            }
        } else if ("item".equals(command)) {
            if (!player.isOp()) return false;
            if (!plugin.applyAdventure(player.getWorld(), adv -> {
                        adv.randomDrop(player.getLocation());
                    })) {
                player.sendMessage(text("No map!", RED));
            }
        } else if (command.equalsIgnoreCase("highscore") || command.equalsIgnoreCase("hi")) {
            if (!plugin.applyAdventure(player.getWorld(), adv -> {
                        adv.showHighscore(player);
                    })) {
                player.sendMessage(text("No map!", RED));
            }
        } else {
            return false;
        }
        return true;
    }
}
