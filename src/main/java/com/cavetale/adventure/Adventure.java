package com.cavetale.adventure;

import com.winthier.sql.SQLDatabase;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;
import org.json.simple.JSONValue;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class Adventure extends JavaPlugin implements Listener {
    @Value
    static class ChunkCoord {
        int x;
        int z;
    }
    @Value
    static class SpawnMob {
        String id;
        String tagData;
    }
    interface Trigger {
        void call(Block block, Player player);
    }
    // const
    static final String SIDEBAR_OBJECTIVE = "Sidebar";
    final List<String> mobList = Arrays.asList(
        "MHF_Blaze",
        "MHF_Bunny",
        "MHF_CaveSpider",
        "MHF_Chicken",
        "MHF_Cow",
        "MHF_Creeper",
        "MHF_EnderDragon",
        "MHF_Enderman",
        "MHF_Ghast",
        "MHF_Golem",
        "MHF_KillerRabbit",
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
        "MHF_Witch",
        "MHF_Zombie",
        "MHF_SnowGolem",
        "MHF_Wolf",
        "MHF_Wither"
        );
    // minigame stuf
    protected World world;
    protected BukkitRunnable tickTask;
    // chunk processing
    protected Set<ChunkCoord> processedChunks = new HashSet<>();
    protected Map<Block, SpawnMob> spawnMobs = new HashMap<>();
    protected boolean expectMob = false;
    protected LivingEntity spawnedMob = null;
    protected boolean didSomeoneJoin = false;
    // level config
    protected String mapId;
    protected UUID gameId;
    protected boolean debug = false;
    protected final List<Location> spawns = new ArrayList<>();
    protected Location lookAt = null;
    protected int spawnIter = 0;
    protected final List<ItemStack> drops = new ArrayList<>();
    protected final List<String> dropperSkulls = new ArrayList<>();
    protected final List<Material> dropperBlocks = new ArrayList<>();
    protected final List<String> credits = new ArrayList<>();
    protected final List<ItemStack> kit = new ArrayList<>();
    protected ItemStack exitItem;
    protected Location winLocation;
    protected final Map<Block, Trigger> triggers = new HashMap<>();
    protected Difficulty difficulty = Difficulty.NORMAL;
    protected final Set<EntityType> lockedEntities = EnumSet.noneOf(EntityType.class);
    protected final Map<Block, String> lockedBlocks = new HashMap<>();
    // players
    protected final Map<UUID, Integer> scores = new HashMap<>();
    protected final Set<UUID> playersOutOfTheGame = new HashSet<>();
    protected final Map<UUID, Integer> winCounters = new HashMap<>();
    protected final Set<UUID> finished = new HashSet<>();
    protected final Set<UUID> joined = new HashSet<>();
    // score keeping
    protected Scoreboard scoreboard;
    protected final Highscore highscore = new Highscore(this);
    protected Date startTime;
    // state
    protected final Random random = new Random(System.currentTimeMillis());
    protected long ticks;
    protected long emptyTicks;
    //
    protected SQLDatabase db;

    // Setup event handlers

    @Override
    public void onEnable() {
        db = new SQLDatabase(this);
        getServer().getPluginManager().registerEvents(this, this);
        // Begin copy-pasted, modified
        ConfigurationSection gameConfig;
        ConfigurationSection worldConfig;
        try {
            gameConfig = new YamlConfiguration().createSection("tmp", (Map<String, Object>) JSONValue.parse(new FileReader("game_config.json")));
            worldConfig = YamlConfiguration.loadConfiguration(new FileReader("GameWorld/config.yml"));
        } catch (Throwable t) {
            t.printStackTrace();
            getServer().shutdown();
            return;
        }
        mapId = gameConfig.getString("map_id", mapId);
        gameId = UUID.fromString(gameConfig.getString("unique_id"));
        debug = gameConfig.getBoolean("debug", debug);

        WorldCreator wc = WorldCreator.name("GameWorld");
        wc.generator("VoidGenerator");
        wc.type(WorldType.FLAT);
        try {
            wc.environment(World.Environment.valueOf(worldConfig.getString("world.Environment").toUpperCase()));
        } catch (Throwable t) {
            wc.environment(World.Environment.NORMAL);
        }
        world = wc.createWorld();
        // End copy-pasted, modified

        world.setDifficulty(difficulty);
        world.setPVP(false);
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobLoot", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("naturalRegeneration", "false");
        world.setGameRuleValue("mobGriefing", "true");
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
        tickTask.runTaskTimer(this, 1L, 1L);
        setupScoreboard();
        processChunkArea(world.getSpawnLocation().getChunk());
        startTime = new Date();
        highscore.init();
    }

    protected void onTick() {
        final long currentTicks = this.ticks++;
        if (didSomeoneJoin && getServer().getOnlinePlayers().isEmpty()) {
            getServer().shutdown();
            return;
        }
        if (currentTicks >= 1200L) {
            if (!didSomeoneJoin) {
                getServer().shutdown();
                return;
            } else if (getServer().getOnlinePlayers().isEmpty()) {
                final long currentEmptyTicks = this.emptyTicks++;
                if (currentEmptyTicks >= 1200L) {
                    getServer().shutdown();
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

    public void onPlayerReady(Player player) {
        didSomeoneJoin = this.didSomeoneJoin;
        this.didSomeoneJoin = true;
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
        }.runTaskLater(this, 20 * 5);
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (joined.contains(event.getPlayer().getUniqueId())) return;
        event.setSpawnLocation(getSpawnLocation(event.getPlayer()));
    }

    public Location getSpawnLocation(Player player) {
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
    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if ("quit".equals(command)) {
            leavePlayer(player);
        } else if ("finish".equals(command)) {
            if (!player.isOp()) return false;
            setFinished(player.getUniqueId());
            recordPlayerScore(player);
            if (winLocation != null) player.teleport(winLocation);
            winCounters.put(player.getUniqueId(), 0);
            player.getInventory().setItem(8, exitItem.clone());
            player.getInventory().clear();
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

    protected void processWinners() {
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
                Msg.sendTitle(player, "&9Congratulations", String.format("&9You completed the %s adventure", mapId)); // TODO name
                break;
            case 200: {
                if (!credits.isEmpty()) {
                    StringBuilder sb = new StringBuilder("&9");
                    sb.append(credits.get(0));
                    for (int i = 1; i < credits.size(); ++i) sb.append(" ").append(credits.get(i));
                    Msg.sendTitle(player, "&9Map created by", sb.toString());
                }
                break;
            }
            case 1200:
                leavePlayer(player);
                removePlayers.add(uuid);
                break;
            default: break;
            }
        }
        for (UUID uuid : removePlayers) winCounters.remove(uuid);
        removePlayers.clear();
    }

    protected void processPlayerChunks() {
        for (Player player : getServer().getOnlinePlayers()) {
            Chunk chunk = player.getLocation().getChunk();
            processChunkArea(chunk.getX(), chunk.getZ());
        }
    }

    protected void processChunkArea(Chunk chunk) {
        processChunkArea(chunk.getX(), chunk.getZ());
    }

    protected void processChunkArea(int cx, int cz) {
        final int radius = 4;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                final int x = cx + dx;
                final int z = cz + dz;
                processChunk(x, z);
            }
        }
    }

    protected void processChunk(int x, int z) {
        final ChunkCoord cc = new ChunkCoord(x, z);
        if (processedChunks.contains(cc)) return;
        if (!world.isChunkLoaded(x, z)) return;
        final Chunk chunk = world.getChunkAt(cc.getX(), cc.getZ());
        if (!chunk.isLoaded()) return;
        if (chunk.getTileEntities().length == 0) return;
        processedChunks.add(cc);
        // Process
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Skull) {
                SpawnMob spawnMob = null;
                final Skull skull = (Skull) state;
                if (skull.hasOwner()) {
                    String owner = skull.getOwner();
                    if ("MHF_Skeleton".equals(owner)) {
                        spawnMob = new SpawnMob("skeleton", "{HandItems:[{id:bow,Count:1},{}]}");
                    } else if ("MHF_WSkeleton".equals(owner)) {
                        spawnMob = new SpawnMob("wither_skeleton", "{HandItems:[{id:stone_sword,Count:1},{}]}");
                    } else if ("MHF_PigZombie".equals(owner)) {
                        spawnMob = new SpawnMob("zombie_pigman", "{HandItems:[{id:golden_sword,Count:1},{}]}");
                    } else if ("MHF_Golem".equals(owner)) {
                        spawnMob = new SpawnMob("villager_golem", "{}");
                    } else if ("MHF_KillerRabbit".equals(owner)) {
                        spawnMob = new SpawnMob("rabbit", "{RabbitType:99}");
                    } else if ("MHF_Bunny".equals(owner)) {
                        spawnMob = new SpawnMob("rabbit", "{}");
                    } else if ("MHF_SnowGolem".equals(owner)) {
                        spawnMob = new SpawnMob("snowman", "{}");
                    } else if ("MHF_Wither".equals(owner)) {
                        spawnMob = new SpawnMob("wither", "{}");
                    } else if ("MHF_Ocelot".equals(owner)) {
                        spawnMob = new SpawnMob("ocelot", "{}");
                    } else if ("MHF_CaveSpider".equals(owner)) {
                        spawnMob = new SpawnMob("cave_spider", "{}");
                    } else if ("MHF_EnderDragon".equals(owner)) {
                        spawnMob = new SpawnMob("ender_dragon", "{}");
                    } else if ("MHF_LavaSlime".equals(owner)) {
                        spawnMob = new SpawnMob("magma_cube", "{}");
                    } else if ("MHF_MushroomCow".equals(owner)) {
                        spawnMob = new SpawnMob("mooshroom", "{}");
                    } else if (mobList.contains(owner)) {
                        spawnMob = new SpawnMob(owner.substring(4).toLowerCase(), "{}");
                    }
                }
                if (spawnMob != null) {
                    spawnMobs.put(state.getBlock(), spawnMob);
                    state.getBlock().setType(Material.AIR);
                }
            } else if (state instanceof Chest chest) {
                final Inventory inv = chest.getInventory();
                String name = chest.getCustomName();
                boolean removeThis = true;
                if ("[droppers]".equals(name)) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            if (item.getType() == Material.PLAYER_HEAD) {
                                SkullMeta meta = (SkullMeta) item.getItemMeta();
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
                final Sign sign = (Sign) state;
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
                            } catch (NumberFormatException nfe) { }
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
                                } catch (NumberFormatException nfe) { }
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
                                    loc.setX((double) coords[0] + 0.5);
                                    loc.setY((double) coords[1] + 0.5);
                                    loc.setZ((double) coords[2] + 0.5);
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
                    } else if (firstLine.equals("[lock]")) {
                        String lockName = sign.getLine(1) + sign.getLine(2) + sign.getLine(3);
                        Block attachedBlock = sign.getBlock().getRelative(((org.bukkit.material.Sign) sign.getData()).getAttachedFace());
                        Material mat = attachedBlock.getType();
                        LinkedList<Block> blocksToSearch = new LinkedList<>();
                        blocksToSearch.add(attachedBlock);
                        Set<Block> blocksSearched = new HashSet<>();
                        List<Block> result = new ArrayList<>();
                        int i = 0;
                        while (!blocksToSearch.isEmpty() && i++ < 64) {
                            Block block = blocksToSearch.removeFirst();
                            if (!blocksSearched.contains(block) && block.getType() == mat) {
                                blocksSearched.add(block);
                                result.add(block);
                                blocksToSearch.add(block.getRelative(0, 0, 1));
                                blocksToSearch.add(block.getRelative(0, 0, -1));
                                blocksToSearch.add(block.getRelative(0, 1, 0));
                                blocksToSearch.add(block.getRelative(0, -1, 0));
                                blocksToSearch.add(block.getRelative(1, 0, 0));
                                blocksToSearch.add(block.getRelative(-1, 0, 0));
                            }
                        }
                        for (Block block : result) lockedBlocks.put(block, lockName);
                    } else {
                        getLogger().warning("Unrecognized sign: " + firstLine);
                        removeThis = false;
                    }
                    if (removeThis) state.getBlock().setType(Material.AIR);
                }
            }
        }
    }

    protected Player isNearAnyPlayer(Block block) {
        final int radius = 16;
        final int vradius = 8;
        for (Player player : getServer().getOnlinePlayers()) {
            final int px;
            final int py;
            final int pz;
            {
                final Location tmp = player.getLocation();
                px = tmp.getBlockX();
                py = tmp.getBlockY();
                pz = tmp.getBlockZ();
            }
            final int dx = Math.abs(px - block.getX());
            if (dx > radius) continue;
            final int dy = Math.abs(py - block.getY());
            if (dy > vradius) continue;
            final int dz = Math.abs(pz - block.getZ());
            if (dz > radius) continue;
            return player;
        }
        return null;
    }

    protected void processSpawnMobs() {
        final List<Block> removeBlocks = new ArrayList<>();
        for (Block block : spawnMobs.keySet()) {
            Player player = isNearAnyPlayer(block);
            if (player == null) continue;
            final SpawnMob spawnMob = spawnMobs.get(block);
            String command = String.format("minecraft:execute at %s run summon %s %d %d %d %s",
                                           player.getUniqueId(),
                                           spawnMob.getId(),
                                           block.getX(), block.getY(), block.getZ(),
                                           spawnMob.getTagData() != null ? spawnMob.getTagData() : "");
            expectMob = true;
            try {
                Msg.consoleCommand(command);
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

    protected ItemStack randomDrop() {
        if (drops.isEmpty()) return null;
        return drops.get(random.nextInt(drops.size())).clone();
    }

    protected void randomDrop(Location loc) {
        final ItemStack stack = randomDrop();
        if (stack == null) return;
        final Item item = loc.getWorld().dropItem(loc, stack);
        item.setPickupDelay(0);
    }

    protected boolean isDropper(Block block) {
        if (block.getType() == Material.PLAYER_HEAD) {
            BlockState state = block.getState();
            if (state instanceof Skull) {
                Skull skull = (Skull) state;
                String owner = skull.getOwner();
                return owner != null && dropperSkulls.contains(owner);
            } else {
                return false;
            }
        } else {
            return dropperBlocks.contains(block.getType());
        }
    }

    protected boolean isDroppedItem(ItemStack item) {
        for (ItemStack drop : drops) {
            if (drop.isSimilar(item)) return true;
        }
        return false;
    }

    protected void setupScoreboard() {
        scoreboard = getServer().getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(SIDEBAR_OBJECTIVE, "dummy", "Dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(Msg.format("&9Score"));
    }

    protected int getPlayerScore(Player player) {
        return getPlayerScore(player.getUniqueId());
    }

    protected int getPlayerScore(UUID uuid) {
        Integer score = scores.get(uuid);
        if (score == null) return 0;
        return score;
    }

    protected void setPlayerScore(Player player, int score) {
        if (setPlayerScore(player.getUniqueId(), score)) {
            scoreboard.getObjective(SIDEBAR_OBJECTIVE).getScore(player.getName()).setScore(score);
        }
    }

    protected boolean setPlayerScore(UUID uuid, int score) {
        Integer oldScore = scores.get(uuid);
        if (oldScore == null || oldScore != score) {
            scores.put(uuid, score);
            return true;
        } else {
            return false;
        }
    }

    protected int countPlayerScore(Player player) {
        int result = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!isDroppedItem(item)) continue;
            result += item.getAmount();
        }
        return result;
    }

    protected void countPlayerScores() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (playersOutOfTheGame.contains(player.getUniqueId())) continue;
            int score = countPlayerScore(player);
            setPlayerScore(player, score);
        }
    }

    protected boolean hasFinished(UUID uuid) {
        return finished.contains(uuid);
    }

    protected void setFinished(UUID uuid) {
        finished.add(uuid);
    }

    private ConfigurationSection fixRewardConfig(ConfigurationSection config) {
        if (config == null) return null;
        if (config.isSet("Daily")) {
            String val = config.getString("Daily");
            val = val.replace("%map%", mapId);
            config.set("Daily", val);
        }
        return config;
    }

    protected void recordPlayerScore(Player player) {
        if (debug) return;
        if (playersOutOfTheGame.contains(player.getUniqueId())) return;
        playersOutOfTheGame.add(player.getUniqueId());
        player.getInventory().clear();
        final int score = getPlayerScore(player);
        final boolean hasFinished = hasFinished(player.getUniqueId());
        highscore.store(player.getUniqueId(), player.getName(), mapId, startTime, new Date(), score, hasFinished);
    }

    // Event Handlers

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
            } else if (block.getType() == Material.NETHER_PORTAL) {
                continue;
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
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        new BukkitRunnable() {
            @Override public void run() {
                leavePlayer(player);
            }
        }.runTaskLater(this, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        leavePlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        if (!joined.contains(uuid)) {
            joined.add(uuid);
            onPlayerReady(player);
        }
        if (playersOutOfTheGame.contains(uuid)) {
            leavePlayer(player);
            return;
        }
        player.setScoreboard(scoreboard);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!isDroppedItem(event.getItem().getItemStack())) return;
        final Player player = (Player) event.getEntity();
        player.playSound(player.getEyeLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
    }

    protected boolean playerHoldsKey(Player player, String lockName) {
        return itemIsKey(player.getInventory().getItemInMainHand(), lockName)
            || itemIsKey(player.getInventory().getItemInOffHand(), lockName);
    }

    protected boolean itemIsKey(ItemStack item, String lockName) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return lockName.equals(meta.getDisplayName());
    }

    protected boolean playerHoldsExitItem(Player player) {
        return isExitItem(player.getInventory().getItemInMainHand())
            || isExitItem(player.getInventory().getItemInOffHand());
    }

    protected boolean isExitItem(ItemStack item) {
        if (exitItem == null || item == null) return false;
        return exitItem.isSimilar(item);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractLock(PlayerInteractEvent event) {
        switch (event.getAction()) {
        case LEFT_CLICK_BLOCK: break;
        case RIGHT_CLICK_BLOCK: break;
        default: return;
        }
        Block block = event.getClickedBlock();
        String lockName = lockedBlocks.get(block);
        if (lockName == null) return;
        Player player = event.getPlayer();
        if (!lockName.isEmpty() && playerHoldsKey(player, lockName)) return;
        event.setCancelled(true);
        block.getWorld().playSound(block.getLocation().add(.5, .5, .5), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractDropper(PlayerInteractEvent event) {
        switch (event.getAction()) {
        case LEFT_CLICK_BLOCK:
        case RIGHT_CLICK_BLOCK:
            Block block = event.getClickedBlock();
            if (isDropper(block)) {
                event.setCancelled(true);
                randomDrop(block.getLocation().add(0.5, 0.0, 0.50));
                block.setType(Material.AIR);
            }
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        switch (event.getAction()) {
        case PHYSICAL:
            switch (block.getType()) {
            case ACACIA_PRESSURE_PLATE:
            case BIRCH_PRESSURE_PLATE:
            case DARK_OAK_PRESSURE_PLATE:
            case HEAVY_WEIGHTED_PRESSURE_PLATE:
            case JUNGLE_PRESSURE_PLATE:
            case LIGHT_WEIGHTED_PRESSURE_PLATE:
            case OAK_PRESSURE_PLATE:
            case SPRUCE_PRESSURE_PLATE:
            case STONE_PRESSURE_PLATE:
                onPressurePlate(event.getPlayer(), event.getClickedBlock());
                break;
            default:
                break;
            }
            return;
        case RIGHT_CLICK_AIR:
            break;
        case RIGHT_CLICK_BLOCK:
            switch (block.getType()) {
            case ACACIA_BUTTON:
            case BIRCH_BUTTON:
            case DARK_OAK_BUTTON:
            case JUNGLE_BUTTON:
            case OAK_BUTTON:
            case SPRUCE_BUTTON:
            case STONE_BUTTON:
            case LEGACY_STONE_BUTTON:
            case LEGACY_WOOD_BUTTON:
                onButtonPush(event.getPlayer(), event.getClickedBlock());
                return;
            default:
                break;
            }
            break;
        default:
            return;
        }
        final Player player = event.getPlayer();
        if (playerHoldsExitItem(player)) {
            event.setCancelled(true);
            leavePlayer(player);
        }
    }

    protected void onClickEntity(Player player, Entity e, Cancellable event) {
        if (lockedEntities.contains(e.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        if (playerHoldsExitItem(player)) {
            event.setCancelled(true);
            leavePlayer(event.getPlayer());
            return;
        }
        onClickEntity(player, event.getRightClicked(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        if (playerHoldsExitItem(player)) {
            event.setCancelled(true);
            leavePlayer(event.getPlayer());
            return;
        }
        onClickEntity(player, event.getRightClicked(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            onClickEntity((Player) event.getDamager(), event.getEntity(), event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (lockedEntities.contains(event.getEntity().getType())) {
            event.setCancelled(true);
        }
    }

    public void leavePlayer(Player player) {
        recordPlayerScore(player);
        player.kickPlayer("Leaving");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (expectMob) {
            spawnedMob = (LivingEntity) event.getEntity();
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

    protected static final BlockFace[] ALL_FACES = {
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN
    };

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;
        event.setCancelled(true);
        final Player player = event.getPlayer();
        final Location loc = player.getLocation();
        for (Block block : getPortalNear(loc.getBlock())) {
            for (BlockFace face : ALL_FACES) {
                Block otherBlock = block.getRelative(face);
                if (triggers.containsKey(otherBlock)) {
                    triggers.get(otherBlock).call(otherBlock, player);
                }
            }
        }
    }

    @EventHandler
    protected void onChunkLoad(ChunkLoadEvent event) {
        if (world == null) return;
        if (!world.equals(event.getWorld())) return;
        processChunk(event.getChunk().getX(), event.getChunk().getZ());
    }

    protected void checkPortalBlock(final Block block, Set<Block> blocks, Set<Block> checked) {
        if (checked.contains(block)) return;
        checked.add(block);
        Material type = block.getType();
        if (type.isSolid()) {
            blocks.add(block);
        }
        if (type == Material.NETHER_PORTAL) {
            for (BlockFace face : ALL_FACES) {
                Block otherBlock = block.getRelative(face);
                checkPortalBlock(otherBlock, blocks, checked);
            }
        }
    }

    protected Set<Block> getPortalNear(final Block block) {
        Set<Block> blocks = new HashSet<Block>();
        Set<Block> checked = new HashSet<Block>();
        if (block.getType() == Material.NETHER_PORTAL) checkPortalBlock(block, blocks, checked);
        for (BlockFace face : ALL_FACES) {
            Block otherBlock = block.getRelative(face);
            if (otherBlock.getType() == Material.NETHER_PORTAL) checkPortalBlock(otherBlock, blocks, checked);
        }
        return blocks;
    }

    protected void onButtonPush(Player player, Block block) {
        org.bukkit.block.data.Directional rot = (org.bukkit.block.data.Directional) block.getBlockData();
        BlockFace face = rot.getFacing().getOppositeFace();
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

    protected void onPressurePlate(Player player, Block block) {
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

    protected void showHighscore(Player player, List<Highscore.Entry> entries) {
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

    protected void showHighscore(Player player) {
        List<Highscore.Entry> entries = highscore.list(mapId);
        showHighscore(player, entries);
    }

    protected void showCredits(Player player) {
        StringBuilder sb = new StringBuilder();
        for (String credit : credits) sb.append(" ").append(credit);
        Msg.send(player, "&b&l%s&r built by&b%s", mapId, sb.toString());
    }
}
