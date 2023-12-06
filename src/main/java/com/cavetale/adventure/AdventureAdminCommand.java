package com.cavetale.adventure;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class AdventureAdminCommand extends AbstractCommand<AdventurePlugin> {
    public AdventureAdminCommand(final AdventurePlugin plugin) {
        super(plugin, "adventureadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("start").arguments("<path>")
            .description("Start a map")
            .completers(CommandArgCompleter.supplyList(this::listMapPaths))
            .playerCaller(this::start);
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
}
