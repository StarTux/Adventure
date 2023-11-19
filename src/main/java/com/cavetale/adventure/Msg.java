package com.cavetale.adventure;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.json.simple.JSONValue;

final class Msg {
    private Msg() { }

    static String format(String msg, Object... args) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) msg = String.format(msg, args);
        return msg;
    }

    static void send(CommandSender sender, String msg, Object... args) {
        sender.sendMessage(format(msg, args));
    }

    static void sendRaw(Player player, Object json) {
        String js;
        try {
            js = JSONValue.toJSONString(json);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        consoleCommand("minecraft:tellraw " + player.getName() + " " + js);
    }

    static void info(CommandSender to, String msg, Object... args) {
        to.sendMessage(format("&r[&3Adventure&r] ") + format(msg, args));
    }

    static void warn(CommandSender to, String msg, Object... args) {
        to.sendMessage(format("&r[&cAdventure&r] &c") + format(msg, args));
    }

    static void consoleCommand(String cmd, Object... args) {
        if (args.length > 0) cmd = String.format(cmd, args);
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd);
    }

    static void raw(Player player, Object... obj) {
        if (obj.length == 0) return;
        if (obj.length == 1) {
            consoleCommand("minecraft:tellraw %s %s", player.getName(), JSONValue.toJSONString(obj[0]));
        } else {
            consoleCommand("minecraft:tellraw %s %s", player.getName(), JSONValue.toJSONString(Arrays.asList(obj)));
        }
    }

    static void sendActionBar(Player player, String msg, Object... args) {
        Object o = button(null, format(msg, args), null, null);
        consoleCommand("minecraft:title %s actionbar %s", player.getName(), JSONValue.toJSONString(o));
    }

    static Object button(ChatColor color, String chat, String tooltip, String command) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", format(chat));
        if (color != null) map.put("color", color.name().toLowerCase());
        if (command != null) {
            Map<String, Object> clickEvent = new HashMap<>();
            map.put("clickEvent", clickEvent);
            clickEvent.put("action", command.endsWith(" ") ? "suggest_command" : "run_command");
            clickEvent.put("value", command);
        }
        if (tooltip != null) {
            Map<String, Object> hoverEvent = new HashMap<>();
            map.put("hoverEvent", hoverEvent);
            hoverEvent.put("action", "show_text");
            hoverEvent.put("value", format(tooltip));
        }
        return map;
    }

    static Object button(String chat, String tooltip, String command) {
        return button(ChatColor.WHITE, chat, tooltip, command);
    }

    static String camelCase(String msg) {
        StringBuilder sb = new StringBuilder();
        for (String tok: msg.split("_")) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(tok.substring(0, 1).toUpperCase());
            sb.append(tok.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    static String jsonToString(Object json) {
        if (json == null) {
            return "";
        } else if (json instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object o: (List) json) {
                sb.append(jsonToString(o));
            }
            return sb.toString();
        } else if (json instanceof Map) {
            Map map = (Map) json;
            StringBuilder sb = new StringBuilder();
            sb.append(map.get("text"));
            sb.append(map.get("extra"));
            return sb.toString();
        } else if (json instanceof String) {
            return (String) json;
        } else {
            return json.toString();
        }
    }

    static void sendTitle(Player player, String title, String subtitle) {
        player.sendTitle(ChatColor.translateAlternateColorCodes('&', title), ChatColor.translateAlternateColorCodes('&', subtitle));
    }

    static void announce(String msg, Object... args) {
        msg = format(msg, args);
        for (Player player: Bukkit.getServer().getOnlinePlayers()) player.sendMessage(msg);
    }

    static void announceTitle(String t, String s) {
        for (Player player: Bukkit.getServer().getOnlinePlayers()) sendTitle(player, t, s);
    }
}
