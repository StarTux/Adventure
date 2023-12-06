package com.cavetale.adventure;

import com.winthier.creative.BuildWorld;
import com.winthier.creative.BuildWorldPurpose;
import com.winthier.sql.SQLDatabase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class AdventurePlugin extends JavaPlugin {
    private static AdventurePlugin instance;
    protected SQLDatabase database;
    protected final Map<String, Adventure> worldAdventureMap = new HashMap<>();
    protected final Map<String, BuildWorld> buildWorlds = new HashMap<>();

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        new EventListener(this).enable();
        database = new SQLDatabase(this);
        database.registerTables(List.of(SQLMapFinish.class));
        database.createAllTables();
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        new AdventureCommand(this).enable();
        new AdventureAdminCommand(this).enable();
        loadBuildWorlds();
    }

    @Override
    public void onDisable() {
        for (Adventure adventure : worldAdventureMap.values()) {
            adventure.disable();
        }
        worldAdventureMap.clear();
    }

    protected void loadBuildWorlds() {
        buildWorlds.clear();
        for (BuildWorld buildWorld : BuildWorld.findPurposeWorlds(BuildWorldPurpose.ADVENTURE, false)) {
            buildWorlds.put(buildWorld.getRow().getPath(), buildWorld);
        }
    }

    public static AdventurePlugin plugin() {
        return instance;
    }

    public boolean applyAdventure(World world, Consumer<Adventure> callback) {
        Adventure adv = worldAdventureMap.get(world.getName());
        if (adv != null) {
            callback.accept(adv);
            return true;
        } else {
            return false;
        }
    }

    private void tick() {
        for (Adventure adv : List.copyOf(worldAdventureMap.values())) {
            if (adv.obsolete) {
                worldAdventureMap.remove(adv.world.getName());
                adv.disable();
            } else {
                adv.onTick();
            }
        }
    }

    public World getLobbyWorld() {
        return Bukkit.getWorlds().get(0);
    }
}
