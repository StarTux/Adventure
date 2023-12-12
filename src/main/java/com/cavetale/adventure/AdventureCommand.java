package com.cavetale.adventure;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.mytems.util.Text;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class AdventureCommand extends AbstractCommand<AdventurePlugin> {
    public AdventureCommand(final AdventurePlugin plugin) {
        super(plugin, "adventure");
    }

    @Override
    protected void onEnable() {
        rootNode.description("List all maps")
            .playerCaller(this::list);
        rootNode.addChild("start").arguments("<path>")
            .hidden(true)
            .description("Start a map")
            .completers(CommandArgCompleter.supplyList(this::listMapPaths))
            .playerCaller(this::start);
        rootNode.addChild("quit").denyTabCompletion()
            .description("Quit the current map")
            .playerCaller(this::quit);
    }

    private List<String> listMapPaths() {
        List<String> result = new ArrayList<>();
        for (BuildWorld buildWorld : plugin.getBuildWorlds().values()) {
            result.add(buildWorld.getPath());
        }
        return result;
    }

    private boolean start(Player player, String[] args) {
        if (args.length != 1) return false;
        final String path = args[0];
        BuildWorld buildWorld = plugin.getBuildWorlds().get(path);
        if (buildWorld == null) throw new CommandWarn("Map not found: " + path);
        buildWorld.makeLocalCopyAsync(world -> {
                Adventure adventure = new Adventure(world, buildWorld);
                adventure.enable();
                plugin.getWorldAdventureMap().put(world.getName(), adventure);
                player.sendMessage(text("Map started: " + buildWorld.getName(), YELLOW));
                player.teleport(adventure.getSpawnLocation());
                adventure.onPlayerJoin(player);
            });
        return true;
    }

    private boolean list(Player player, String[] args) {
        if (args.length != 0) return false;
        Adventure adventure = plugin.adventureIn(player.getWorld());
        if (adventure != null) return false;
        player.sendMessage(empty());
        player.sendMessage(text("Adventure Maps", BLUE, BOLD));
        for (BuildWorld buildWorld : plugin.getBuildWorlds().values()) {
            player.sendMessage(text("\u2022 " + buildWorld.getName(), BLUE)
                               .hoverEvent(showText(tooltip(buildWorld)))
                               .clickEvent(runCommand("/adventure start " + buildWorld.getPath())));
        }
        player.sendMessage(empty());
        return true;
    }

    private Component tooltip(BuildWorld buildWorld) {
        List<Component> lines = new ArrayList<>();
        lines.add(text(buildWorld.getName(), GREEN));
        lines.add(textOfChildren(text("Made by ", GRAY), text(buildWorld.getOwnerName(), WHITE)));
        if (buildWorld.getRow().getDescription() != null) {
            lines.addAll(Text.wrapLore(tiny(buildWorld.getRow().getDescription()), c -> c.color(BLUE)));
        }
        return join(separator(newline()), lines);
    }

    private void quit(Player player) {
        Adventure adventure = plugin.adventureIn(player.getWorld());
        if (adventure == null) throw new CommandWarn("You're not in an adventure!");
        player.sendMessage(text("Quitting...", YELLOW));
        adventure.leavePlayer(player);
    }
}
