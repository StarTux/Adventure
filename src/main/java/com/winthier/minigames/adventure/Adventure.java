package com.winthier.minigames.adventure;

import com.winthier.minigames.MinigamesPlugin;
import com.winthier.minigames.event.player.PlayerLeaveEvent;
import com.winthier.minigames.game.Game;
import com.winthier.minigames.util.BukkitFuture;
import com.winthier.minigames.util.Console;
import com.winthier.minigames.util.Msg;
import com.winthier.minigames.util.Players;
import com.winthier.minigames.util.Title;
import com.winthier.minigames.util.WorldLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.material.Button;
import org.bukkit.material.MaterialData;
import org.bukkit.material.PressurePlate;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

public class Adventure extends Game implements Listener {
    @Value static class ChunkCoord { int x, z; }
    @Value static class SpawnMob { String id; String tagData; }
    static interface Trigger { void call(Block block, Player player); }
    // const
    final String SIDEBAR_OBJECTIVE = "Sidebar";
    final List<String> mobList = Arrays.asList(
        "MHF_Blaze",
        "MHF_CaveSpider",
        "MHF_Chicken",
        "MHF_Cow",
        "MHF_Creeper",
        "MHF_EnderDragon",
        "MHF_Enderman",
        "MHF_Ghast",
        "MHF_Golem",
        "MHF_LavaSlime",
        "MHF_MushroomCow",
        "MHF_Ocelot",
        "MHF_Pig",
        "MHF_PigZombie",
        "MHF_Sheep",
        "MHF_Skeleton",
        "MHF_Slime",
        "MHF_Spider",
        "MHF_Squid",
        "MHF_Steve",
        "MHF_Villager",
        "MHF_WSkeleton",
        "MHF_Zombie"
        );
    // minigame stuf
    World world;
    BukkitRunnable tickTask;
    boolean solo = false;
    // chunk processing
    Set<ChunkCoord> processedChunks = new HashSet<>();
    Map<Block, SpawnMob> spawnMobs = new HashMap<>();
    boolean expectMob = false;
    LivingEntity spawnedMob = null;
    boolean didSomeoneJoin = false;
    // level config
    String mapId = "Test";
    String mapPath = "Adventure/Test";
    boolean debug = false;
    final List<Location> spawns = new ArrayList<>();
    Location lookAt = null;
    int spawnIter = 0;
    final List<ItemStack> drops = new ArrayList<>();
    final List<String> dropperSkulls = new ArrayList<>();
    final List<Material> dropperBlocks = new ArrayList<>();
    final List<String> credits = new ArrayList<>();
    final List<ItemStack> kit = new ArrayList<>();
    ItemStack exitItem;
    Location winLocation;
    final Map<Block, Trigger> triggers = new HashMap<>();
    Difficulty difficulty = Difficulty.NORMAL;
    final Set<EntityType> lockedEntities = EnumSet.noneOf(EntityType.class);
    // players
    final Map<UUID, Integer> scores = new HashMap<>();
    final Set<UUID> playersOutOfTheGame = new HashSet<>();
    final Map<UUID, Integer> winCounters = new HashMap<>();
    final Set<UUID> finished = new HashSet<>();
    // score keeping
    Scoreboard scoreboard;
    final Highscore highscore = new Highscore();
    Date startTime;
    // state
    final Random random = new Random(System.currentTimeMillis());
    long ticks;
    long emptyTicks;
    
    // Setup event handlers
    
    @Override
    public void onEnable() {
        System.out.println(getConfig().getValues(true));
        mapId = getConfig().getString("MapID", mapId);
        mapPath = getConfig().getString("MapPath", mapPath);
        debug = getConfig().getBoolean("Debug", debug);
        solo = getConfig().getBoolean("Solo", solo);
        WorldLoader.loadWorlds(this, new BukkitFuture<WorldLoader>() {
            @Override public void run() {
                onWorldsLoaded(get());
            }
        }, mapPath);
    }

    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        processedChunks.clear();
        spawnMobs.clear();
    }

    void onWorldsLoaded(WorldLoader worldLoader) {
        this.world = worldLoader.getWorld(0);
        world.setDifficulty(difficulty);
        world.setPVP(false);
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobLoot", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
        if (ticks % 20L == 0L) {
            world.setTime(18000);
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(99999);
        }
        this.tickTask = new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        };
        tickTask.runTaskTimer(MinigamesPlugin.getInstance(), 1L, 1L);
        MinigamesPlugin.getInstance().getEventManager().registerEvents(this, this);
        setupScoreboard();
        processChunkArea(world.getSpawnLocation().getChunk());
        startTime = new Date();
        highscore.init();
        ready();
    }

    void onTick() {
        final long ticks = this.ticks++;
        if (getPlayerUuids().isEmpty()) {
            cancel();
            return;
        }
        if (ticks >= 1200L) {
            if (!didSomeoneJoin) {
                cancel();
                return;
            } else if (getOnlinePlayers().isEmpty()) {
                final long emptyTicks = this.emptyTicks++;
                if (emptyTicks >= 1200L) {
                    cancel();
                    return;
                }
            } else {
                emptyTicks = 0L;
            }
        }
        processPlayerChunks();
        processSpawnMobs();
        processWinners();
        countPlayerScores();
    }

    @Override
    public void onPlayerReady(Player player) {
        didSomeoneJoin = this.didSomeoneJoin;
        this.didSomeoneJoin = true;
        Players.reset(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setScoreboard(scoreboard);
        if (exitItem != null) player.getInventory().setItem(8, exitItem.clone());
        for (ItemStack kitItem : kit) player.getInventory().addItem(kitItem.clone());
        if (!didSomeoneJoin) startTime = new Date();
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isValid()) {
                    player.sendMessage("");
                    showHighscore(player);
                    player.sendMessage("");
                    showCredits(player);
                    player.sendMessage("");
                }
            }
        }.runTaskLater(MinigamesPlugin.getInstance(), 20*5);
    }

    @Override
    public Location getSpawnLocation(Player player)
    {
        Location spawn;
        if (spawns.isEmpty()) {
            spawn = world.getSpawnLocation();
        } else {
            if (spawnIter >= spawns.size()) spawnIter = 0;
            spawn = spawns.get(spawnIter++);
        }
        if (lookAt != null) {
            Vector vec = lookAt.toVector().subtract(spawn.toVector());
            spawn = spawn.setDirection(vec);
        }
        return spawn;
    }

    @Override
    public boolean onCommand(Player player, String command, String[] args) {
        if ("quit".equals(command)) {
            if (!player.isOp()) return false;
            cancel();
            player.sendMessage("Cancelling the game");
        } else if ("item".equals(command)) {
            if (!player.isOp()) return false;
            randomDrop(player.getLocation());
        } else if (command.equalsIgnoreCase("highscore") || command.equalsIgnoreCase("hi")) {
            showHighscore(player);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean joinPlayers(List<UUID> uuids) {
        if (ticks > 20 * 60) return false;
        if (solo) return false;
        return super.joinPlayers(uuids);
    }

    void processWinners() {
        final List<UUID> removePlayers = new ArrayList<>();
        for (UUID uuid : winCounters.keySet()) {
            final Player player = Bukkit.getServer().getPlayer(uuid);
            if (player == null || !player.getWorld().equals(world)) {
                removePlayers.add(uuid);
                continue;
            }
            int counter = winCounters.get(uuid);
            winCounters.put(uuid, counter + 1);
            switch (counter) {
            case 20:
                Title.show(player, "&9Congratulations", String.format("&9You completed the %s adventure", mapId)); // TODO name
                break;
            case 200: {
                if (!credits.isEmpty()) {
                    StringBuilder sb = new StringBuilder("&9");
                    sb.append(credits.get(0));
                    for (int i = 1; i < credits.size(); ++i) sb.append(" ").append(credits.get(i));
                    Title.show(player, "&9Map created by", sb.toString());
                }
                break;
            }
            case 1200:
                MinigamesPlugin.getInstance().leavePlayer(player);
                removePlayers.add(uuid);
                break;
            }
        }
        for (UUID uuid : removePlayers) winCounters.remove(uuid);
        removePlayers.clear();
    }

    void processPlayerChunks() {
        for (Player player : getOnlinePlayers()) {
            Chunk chunk = player.getLocation().getChunk();
            processChunkArea(chunk.getX(), chunk.getZ());
        }
    }

    void processChunkArea(Chunk chunk) {
        processChunkArea(chunk.getX(), chunk.getZ());
    }
    
    void processChunkArea(int cx, int cz) {
        final int RADIUS = 4;
        for (int dx = -RADIUS; dx <= RADIUS; ++dx) {
            for (int dz = -RADIUS; dz <= RADIUS; ++dz) {
                final int x = cx + dx;
                final int z = cz + dz;
                processChunk(x, z);
            }
        }
    }

    void processChunk(int x, int z) {
        final ChunkCoord cc = new ChunkCoord(x, z);
        if (processedChunks.contains(cc)) return;
        processedChunks.add(cc);
        // Process
        final Chunk chunk = world.getChunkAt(cc.getX(), cc.getZ());
        chunk.load();
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Skull) {
                SpawnMob spawnMob = null;
                final Skull skull = (Skull)state;
                if (skull.hasOwner() && skull.getOwner() != null) {
                    String owner = skull.getOwner();
                    if ("MHF_Skeleton".equals(owner)) {
                        spawnMob = new SpawnMob("Skeleton", "{SkeletonType:0,Equipment:[{id:bow},{},{},{},{}]}");
                    } else if ("MHF_WSkeleton".equals(owner)) {
                        spawnMob = new SpawnMob("Skeleton", "{SkeletonType:1,Equipment:[{id:stone_sword},{},{},{},{}]}");
                    } else if ("MHF_Golem".equals(owner)) {
                        spawnMob = new SpawnMob("VillagerGolem", "{}");
                    } else if (mobList.contains(owner)) {
                        spawnMob = new SpawnMob(owner.substring(4), "{}");
                    }
                }
                if (spawnMob != null) {
                    spawnMobs.put(state.getBlock(), spawnMob);
                    state.getBlock().setType(Material.AIR);
                }
            } else if (state instanceof Chest) {
                final Inventory inv = ((Chest)state).getInventory();
                String name = inv.getName().toLowerCase();
                boolean removeThis = true;
                if ("[droppers]".equals(name)) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            if (item.getType() == Material.SKULL_ITEM) {
                                SkullMeta meta = (SkullMeta)item.getItemMeta();
                                String owner = meta.getOwner();
                                if (owner != null) {
                                    dropperSkulls.add(meta.getOwner());
                                }
                            } else {
                                dropperBlocks.add(item.getType());
                            }
                        }
                    }
                    inv.clear();
                } else if ("[drops]".equals(name)) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            drops.add(item.clone());
                        }
                    }
                    inv.clear();
                } else if ("[kit]".equals(name)) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            kit.add(item.clone());
                        }
                    }
                    inv.clear();
                } else if ("[exit]".equals(name)) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            this.exitItem = item.clone();
                        }
                    }
                    inv.clear();
                } else if ("[win]".equals(name)) {
                    this.winLocation = state.getBlock().getLocation().add(0.5, 0.0, 0.5);
                } else {
                    removeThis = false;
                }
                if (removeThis) state.getBlock().setType(Material.AIR);
            } else if (state instanceof Sign) {
                final Sign sign = (Sign)state;
                String firstLine = sign.getLine(0).toLowerCase();
                boolean removeThis = true;
                if (firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]")) {
                    if ("[spawn]".equals(firstLine)) {
                        spawns.add(state.getLocation().add(0.5, 0.0, 0.5));
                    } else if ("[lookat]".equals(firstLine)) {
                        this.lookAt = state.getLocation().add(0.5, 0.0, 0.5);
                    } else if ("[time]".equals(firstLine)) {
                        long time = 0;
                        String arg = sign.getLine(1).toLowerCase();
                        if ("day".equals(arg)) {
                            time = 1000;
                        } else if ("night".equals(arg)) {
                            time = 13000;
                        } else if ("noon".equals(arg)) {
                            time = 6000;
                        } else if ("midnight".equals(arg)) {
                            time = 18000;
                        } else {
                            try {
                                time = Long.parseLong(sign.getLine(1));
                            } catch (NumberFormatException nfe) {}
                        }
                        world.setTime(time);
                        if ("lock".equalsIgnoreCase(sign.getLine(2))) {
                            world.setGameRuleValue("doDaylightCycle", "false");
                        } else {
                            world.setGameRuleValue("doDaylightCycle", "true");
                        }
                    } else if ("[weather]".equals(firstLine)) {
                        int duration = 60;
                        if (!sign.getLine(2).isEmpty()) {
                            String arg = sign.getLine(2).toLowerCase();
                            if ("lock".equals(arg)) {
                                duration = 99999;
                            } else {
                                try {
                                    duration = Integer.parseInt(sign.getLine(2));
                                } catch (NumberFormatException nfe) {}
                            }
                        }
                        String weather = sign.getLine(1).toLowerCase();
                        if ("clear".equals(weather)) {
                            world.setStorm(false);
                            world.setThundering(false);
                        } else if ("rain".equals(weather)) {
                            world.setStorm(true);
                            world.setThundering(false);
                        } else if ("thunder".equals(weather)) {
                            world.setStorm(true);
                            world.setThundering(true);
                        }
                        world.setWeatherDuration(duration * 20);
                    } else if ("[options]".equals(firstLine)) {
                        for (int i = 1; i < 4; ++i) {
                            String line = sign.getLine(i);
                            if ("LockArmorStands".equalsIgnoreCase(line)) {
                                lockedEntities.add(EntityType.ARMOR_STAND);
                            } else if ("LockItemFrames".equalsIgnoreCase(line)) {
                                lockedEntities.add(EntityType.ITEM_FRAME);
                            } else if ("LockPaintings".equalsIgnoreCase(line)) {
                                lockedEntities.add(EntityType.PAINTING);
                            } else if ("NoFireTick".equalsIgnoreCase(line)) {
                                world.setGameRuleValue("doFireTick", "false");
                            } else if ("NoMobGriefing".equalsIgnoreCase(line)) {
                                world.setGameRuleValue("mobGriefing", "false");
                            }
                        }
                    } else if (firstLine.equals("[win]")) {
                        this.winLocation = state.getBlock().getLocation().add(0.5, 0.0, 0.5);
                    } else if (firstLine.equals("[credits]")) {
                        for (int i = 1; i < 4; ++i) {
                            String credit = sign.getLine(i);
                            if (credit != null) credits.add(credit);
                        }
                    } else if (firstLine.equals("[finish]")) {
                        triggers.put(state.getBlock(), new Trigger() {
                            @Override public void call(Block block, Player player) {
                                setFinished(player.getUniqueId());
                                recordPlayerScore(player);
                                if (winLocation != null) player.teleport(winLocation);
                                winCounters.put(player.getUniqueId(), 0);
                                player.getInventory().setItem(8, exitItem.clone());
                                player.getInventory().clear();
                            }
                        });
                    } else if (firstLine.equals("[teleport]")) {
                        String[] tokens = sign.getLine(1).split(" ");
                        if (tokens.length == 3) {
                            final int[] coords = new int[3];
                            coords[0] = state.getX();
                            coords[1] = state.getY();
                            coords[2] = state.getZ();
                            for (int i = 0; i < 3; ++i) {
                                String token = tokens[i];
                                boolean relative = false;
                                if (token.startsWith("~")) {
                                    relative = true;
                                    token = token.substring(1, token.length());
                                }
                                try {
                                    int val = Integer.parseInt(token);
                                    if (relative) {
                                        coords[i] += val;
                                    } else {
                                        coords[i] = val;
                                    }
                                } catch (NumberFormatException nfe) {
                                    getLogger().warning(String.format("Bad teleport sign at %d,%d,%d: Number expected, got %s", state.getX(), state.getY(), state.getZ(), token));
                                }
                            }
                            triggers.put(state.getBlock(), new Trigger() {
                                @Override public void call(Block block, Player player) {
                                    Location loc = player.getLocation();
                                    loc.setX((double)coords[0] + 0.5);
                                    loc.setY((double)coords[1] + 0.5);
                                    loc.setZ((double)coords[2] + 0.5);
                                    player.teleport(loc);
                                }
                            });
                        } else {
                            getLogger().warning(String.format("Bad teleport sign at %d,%d,%d", state.getX(), state.getY(), state.getZ()));
                        }
                    } else if (firstLine.equals("[difficulty]")) {
                        try {
                            this.difficulty = Difficulty.valueOf(sign.getLine(1).toUpperCase());
                            world.setDifficulty(this.difficulty);
                            getLogger().info("Set difficulty to " + this.difficulty);
                        } catch (IllegalArgumentException iae) {
                            getLogger().warning(String.format("Bad difficulty sign at %d,%d,%d", state.getX(), state.getY(), state.getZ()));
                        }
                    } else {
                        getLogger().warning("Unrecognized sign: " + firstLine);
                        removeThis = false;
                    }
                    if (removeThis) state.getBlock().setType(Material.AIR);
                }
            }
        }
    }

    Player isNearAnyPlayer(Block block) {
        final int RADIUS = 16;
        final int VRADIUS = 8;
        for (Player player : getOnlinePlayers()) {
            final int px, py, pz;
            {
                final Location tmp = player.getLocation();
                px = tmp.getBlockX();
                py = tmp.getBlockY();
                pz = tmp.getBlockZ();
            }
            final int dx = Math.abs(px - block.getX());
            if (dx > RADIUS) continue;
            final int dy = Math.abs(py - block.getY());
            if (dy > VRADIUS) continue;
            final int dz = Math.abs(pz - block.getZ());
            if (dz > RADIUS) continue;
            return player;
        }
        return null;
    }

    void processSpawnMobs() {
        final List<Block> removeBlocks = new ArrayList<>();
        for (Block block : spawnMobs.keySet()) {
            Player player = isNearAnyPlayer(block);
            if (player == null) continue;
            final SpawnMob spawnMob = spawnMobs.get(block);
            String command = String.format("minecraft:execute %s ~ ~ ~ summon %s %d %d %d %s",
                                           player.getUniqueId(),
                                           spawnMob.getId(),
                                           block.getX(), block.getY(), block.getZ(),
                                           spawnMob.getTagData() != null ? spawnMob.getTagData() : "");
            expectMob = true;
            try {
                Console.command(command);
            } catch (Exception e) {
                e.printStackTrace();
                expectMob = false;
                continue;
            }
            if (spawnedMob != null) {
                spawnedMob.setRemoveWhenFarAway(false);
                spawnedMob.setCanPickupItems(false);
                getLogger().info("Mob spawned: " + spawnedMob.getType() + " " + spawnMob.getId() + " " + spawnMob.getTagData());
                spawnedMob = null;
                removeBlocks.add(block);
            } else {
                getLogger().warning("Mob did not spawn: " + spawnMob.getId() + " " + spawnMob.getTagData());
            }
        }
        for (Block block : removeBlocks) spawnMobs.remove(block);
        removeBlocks.clear();
    }

    ItemStack randomDrop() {
        if (drops.isEmpty()) return null;
        return drops.get(random.nextInt(drops.size())).clone();
    }

    void randomDrop(Location loc) {
        final ItemStack stack = randomDrop();
        if (stack == null) return;
        final Item item = loc.getWorld().dropItem(loc, stack);
        item.setPickupDelay(0);
    }

    boolean isDropper(Block block)
    {
        if (block.getType() == Material.SKULL) {
            BlockState state = block.getState();
            if (state instanceof Skull) {
                Skull skull = (Skull)state;
                String owner = skull.getOwner();
                return owner != null && dropperSkulls.contains(owner);
            } else {
                return false;
            }
        } else {
            return dropperBlocks.contains(block.getType());
        }
    }

    boolean isDroppedItem(ItemStack item)
    {
        for (ItemStack drop : drops) {
            if (drop.isSimilar(item)) return true;
        }
        return false;
    }

    void setupScoreboard() {
        scoreboard = MinigamesPlugin.getInstance().getServer().getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(SIDEBAR_OBJECTIVE, "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(Msg.format("&9Score"));
    }

    int getPlayerScore(Player player) {
        return getPlayerScore(player.getUniqueId());
    }

    int getPlayerScore(UUID uuid) {
        Integer score = scores.get(uuid);
        if (score == null) return 0;
        return score;
    }

    void setPlayerScore(Player player, int score) {
        if (setPlayerScore(player.getUniqueId(), score)) {
            scoreboard.getObjective(SIDEBAR_OBJECTIVE).getScore(player.getName()).setScore(score);
        }
    }

    boolean setPlayerScore(UUID uuid, int score) {
        Integer oldScore = scores.get(uuid);
        if (oldScore == null || oldScore != score) {
            scores.put(uuid, score);
            return true;
        } else {
            return false;
        }
    }

    int countPlayerScore(Player player) {
        int result = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!isDroppedItem(item)) continue;
            result += item.getAmount();
        }
        return result;
    }

    void countPlayerScores() {
        for (Player player : getOnlinePlayers()) {
            if (playersOutOfTheGame.contains(player.getUniqueId())) continue;
            int score = countPlayerScore(player);
            setPlayerScore(player, score);
        }
    }

    boolean hasFinished(UUID uuid) {
        return finished.contains(uuid);
    }

    void setFinished(UUID uuid) {
        finished.add(uuid);
    }

    void recordPlayerScore(Player player) {
        if (debug) return;
        if (playersOutOfTheGame.contains(player.getUniqueId())) return;
        playersOutOfTheGame.add(player.getUniqueId());
        player.getInventory().clear();
        final int score = getPlayerScore(player);
        final boolean finished = hasFinished(player.getUniqueId());
        highscore.store(player.getUniqueId(), player.getName(), mapId, startTime, new Date(), score, finished);
    }

    // Event Handlers

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract2(PlayerInteractEvent event) {
        switch (event.getAction()) {
        case LEFT_CLICK_BLOCK:
        case RIGHT_CLICK_BLOCK:
            Block block = event.getClickedBlock();
            if (isDropper(block)) {
                event.setCancelled(true);
                randomDrop(block.getLocation().add(0.5, 0.0, 0.50));
                block.setType(Material.AIR);
            }
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        event.setCancelled(true);
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        final List<Block> addBlocks = new ArrayList<>();
        event.setYield(0.0f);
        for (Block block : event.blockList()) {
            if (block.getType() == Material.ICE) {
                block.setType(Material.WATER);
            } else if (block.getType() == Material.PORTAL) {
                // ignore
            } else {
                addBlocks.add(block);
            }
        }
        event.blockList().clear();
        event.blockList().addAll(addBlocks);
        addBlocks.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        event.setDroppedExp(0);
        event.getDrops().clear();
        if (entity instanceof Monster) {
            if (entity.getKiller() != null) {
                final Location loc = entity.getLocation();
                randomDrop(loc);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        event.getDrops().clear();
        Players.reset(player);
        player.setGameMode(GameMode.SPECTATOR);
        new BukkitRunnable() {
            @Override public void run() {
                MinigamesPlugin.getInstance().leavePlayer(player);
            }
        }.runTaskLater(MinigamesPlugin.getInstance(), 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        MinigamesPlugin.getInstance().leavePlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event)
    {
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (playersOutOfTheGame.contains(event.getPlayer().getUniqueId())) {
            MinigamesPlugin.getInstance().leavePlayer(event.getPlayer());
            return;
        }
        event.getPlayer().setScoreboard(scoreboard);
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (!isDroppedItem(event.getItem().getItemStack())) return;
        final Player player = event.getPlayer();
        player.playSound(player.getEyeLocation(), Sound.SUCCESSFUL_HIT, 1.0f, 1.0f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        MaterialData data;
        switch (event.getAction()) {
        case PHYSICAL:
            data = event.getClickedBlock().getState().getData();
            if (data instanceof PressurePlate) {
                onPressurePlate(event.getPlayer(), event.getClickedBlock(), (PressurePlate)data);
            } else {
                event.setCancelled(true);
            }
            return;
        case RIGHT_CLICK_AIR:
            break;
        case RIGHT_CLICK_BLOCK:
            data = event.getClickedBlock().getState().getData();
            if (data instanceof Button) {
                onButtonPush(event.getPlayer(), event.getClickedBlock(), (Button)data);
                return;
            }
            break;
        default:
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack item = event.getPlayer().getItemInHand();
        if (item.isSimilar(this.exitItem)) {
            event.setCancelled(true);
            MinigamesPlugin.getInstance().leavePlayer(player);
        }
    }

    void onClickEntity(Player player, Entity e, Cancellable event)
    {
        if (lockedEntities.contains(e.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getPlayer().getItemInHand();
        if (item.isSimilar(this.exitItem)) {
            event.setCancelled(true);
            MinigamesPlugin.getInstance().leavePlayer(event.getPlayer());
            return;
        }
        onClickEntity(player, event.getRightClicked(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getPlayer().getItemInHand();
        if (item.isSimilar(this.exitItem)) {
            event.setCancelled(true);
            MinigamesPlugin.getInstance().leavePlayer(event.getPlayer());
            return;
        }
        onClickEntity(player, event.getRightClicked(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event)
    {
        if (event.getDamager() instanceof Player) {
            onClickEntity((Player)event.getDamager(), event.getEntity(), event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event)
    {
        if (lockedEntities.contains(event.getEntity().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLeave(PlayerLeaveEvent event) {
        final Player player = event.getPlayer();
        if (player != null) {
            recordPlayerScore(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (expectMob) {
            spawnedMob = (LivingEntity)event.getEntity();
            expectMob = false;
        } else {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof org.bukkit.entity.Projectile) {
            event.getEntity().remove();
        }
    }

    final BlockFace[] ALL_FACES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;
        event.setCancelled(true);
        final Player player = event.getPlayer();
        final Location loc = player.getLocation();
        final World world = loc.getWorld();
        for (Block block : getPortalNear(loc.getBlock())) {
            for (BlockFace face : ALL_FACES) {
                Block otherBlock = block.getRelative(face);
                if (triggers.containsKey(otherBlock)) {
                    triggers.get(otherBlock).call(otherBlock, player);
                }
            }
        }
    }
    
    void checkPortalBlock(final Block block, Set<Block> blocks, Set<Block> checked) {
        if (checked.contains(block)) return;
        checked.add(block);
        Material type = block.getType();
        if (type.isSolid()) {
            blocks.add(block);
        }
        if (type == Material.PORTAL) {
            for (BlockFace face : ALL_FACES) {
                Block otherBlock = block.getRelative(face);
                checkPortalBlock(otherBlock, blocks, checked);
            }
        }
    }

    Set<Block> getPortalNear(final Block block) {
        Set<Block> blocks = new HashSet<Block>();
        Set<Block> checked = new HashSet<Block>();
        if (block.getType() == Material.PORTAL) checkPortalBlock(block, blocks, checked);
        for (BlockFace face : ALL_FACES) {
            Block otherBlock = block.getRelative(face);
            if (otherBlock.getType() == Material.PORTAL) checkPortalBlock(otherBlock, blocks, checked);
        }
        return blocks;
    }

    void onButtonPush(Player player, Block block, Button button) {
        //if (button.isPowered()) return;
        BlockFace face = button.getAttachedFace();
        block = block.getRelative(face);
        boolean result = false;
        while (true) {
            block = block.getRelative(face);
            if (triggers.containsKey(block)) {
                triggers.get(block).call(block, player);
            } else {
                return;
            }
        }
    }

    void onPressurePlate(Player player, Block block, PressurePlate plate) {
        //if (plate.isPressed()) return;
        block = block.getRelative(BlockFace.DOWN);
        boolean result = false;
        while (true) {
            block = block.getRelative(BlockFace.DOWN);
            if (triggers.containsKey(block)) {
                triggers.get(block).call(block, player);
            } else {
                return;
            }
        }
    }

    void showHighscore(Player player, List<Highscore.Entry> entries)
    {
        int i = 1;
        Msg.send(player, "&b&l" + mapId + " Highscore");
        Msg.send(player, "&3Rank &fScore &3Time &fName");
        for (Highscore.Entry entry : entries) {
            long seconds = entry.getTime() / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            Msg.send(player, "&3#%02d &f%d &3%02d&f:&3%02d&f:&3%02d &f%s", i++, entry.getScore(), hours, minutes % 60, seconds % 60, entry.getName());
        }
    }

    void showHighscore(Player player)
    {
        List<Highscore.Entry> entries = highscore.list(mapId);
        showHighscore(player, entries);
    }

    void showCredits(Player player)
    {
        StringBuilder sb = new StringBuilder();
        for (String credit : credits) sb.append(" ").append(credit);
        Msg.send(player, "&b&l%s&r built by&b%s", mapId, sb.toString());
    }
}
