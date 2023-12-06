package com.cavetale.adventure;

import com.cavetale.core.playercache.PlayerCache;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.file.Files;
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
import lombok.Getter;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
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
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;
import static com.cavetale.adventure.AdventurePlugin.plugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText;
import static net.kyori.adventure.title.Title.title;

@Getter
public final class Adventure {
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
    // minigame stuff
    protected World world;
    protected BuildWorld buildWorld;
    // chunk processing
    protected Set<ChunkCoord> processedChunks = new HashSet<>();
    protected Map<Block, SpawnMob> spawnMobs = new HashMap<>();
    protected boolean expectMob = false;
    protected LivingEntity spawnedMob = null;
    // level config
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
    protected Date startTime;
    // state
    protected final Random random = new Random(System.currentTimeMillis());
    protected long ticks;
    protected long emptyTicks;
    protected boolean obsolete = false;

    public Adventure(final World world, final BuildWorld buildWorld) {
        this.world = world;
        this.buildWorld = buildWorld;
    }

    protected void enable() {
        world.setDifficulty(difficulty);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.DO_MOB_LOOT, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        if (ticks % 20L == 0L) {
            world.setTime(18000);
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(99999);
        }
        processChunkArea(world.getSpawnLocation().getChunk());
        startTime = new Date();
    }

    protected void disable() {
        obsolete = true; // Should already be set!
        for (Player player : world.getPlayers()) {
            leavePlayer(player);
        }
        Files.deleteWorld(world);
    }

    protected void onTick() {
        final long currentTicks = this.ticks++;
        if (currentTicks >= 20L && world.getPlayers().isEmpty()) {
            final long currentEmptyTicks = this.emptyTicks++;
            if (currentEmptyTicks >= 1200L) {
                obsolete = true;
                return;
            }
        } else {
            emptyTicks = 0L;
        }
        processPlayerChunks();
        processSpawnMobs();
        processWinners();
        countPlayerScores();
    }

    /**
     * Joins and rejoins.
     */
    public void onPlayerJoin(Player player) {
        final UUID uuid = player.getUniqueId();
        if (!joined.contains(uuid)) {
            joined.add(uuid);
            onPlayerReady(player);
        }
        if (playersOutOfTheGame.contains(uuid)) {
            leavePlayer(player);
            return;
        }
    }

    /**
     * First time join.
     */
    public void onPlayerReady(Player player) {
        resetPlayer(player);
        if (exitItem != null) player.getInventory().setItem(8, exitItem.clone());
        for (ItemStack kitItem : kit) player.getInventory().addItem(kitItem.clone());
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (obsolete) return;
                if (!player.isValid() || !player.getWorld().equals(world)) return;
                player.sendMessage("");
                showHighscore(player);
                player.sendMessage("");
                showCredits(player);
                player.sendMessage("");
            }, 20L * 5L);
    }

    public Location getSpawnLocation() {
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
                player.showTitle(title(text("Congratulations", BLUE),
                                       text("You completed " + buildWorld.getName(), BLUE)));
                break;
            case 200: {
                if (!credits.isEmpty()) {
                    StringBuilder sb = new StringBuilder("&9");
                    sb.append(credits.get(0));
                    for (int i = 1; i < credits.size(); ++i) sb.append(" ").append(credits.get(i));
                    player.showTitle(title(text("Map created by", BLUE),
                                           text(sb.toString(), BLUE)));
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
        for (Player player : world.getPlayers()) {
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
            if (state instanceof Skull skull) {
                final PlayerProfile playerProfile = skull.getPlayerProfile();
                if (playerProfile == null) continue;
                final String owner = playerProfile.getName();
                if (owner == null) continue;
                final SpawnMob spawnMob = switch (owner) {
                case "MHF_Skeleton" -> new SpawnMob("skeleton", "{HandItems:[{id:bow,Count:1},{}]}");
                case "MHF_WSkeleton" -> new SpawnMob("wither_skeleton", "{HandItems:[{id:stone_sword,Count:1},{}]}");
                case "MHF_PigZombie" -> new SpawnMob("zombie_pigman", "{HandItems:[{id:golden_sword,Count:1},{}]}");
                case "MHF_Golem" -> new SpawnMob("villager_golem", "{}");
                case "MHF_KillerRabbit" -> new SpawnMob("rabbit", "{RabbitType:99}");
                case "MHF_Bunny" -> new SpawnMob("rabbit", "{}");
                case "MHF_SnowGolem" -> new SpawnMob("snowman", "{}");
                case "MHF_Wither" -> new SpawnMob("wither", "{}");
                case "MHF_Ocelot" -> new SpawnMob("ocelot", "{}");
                case "MHF_CaveSpider" -> new SpawnMob("cave_spider", "{}");
                case "MHF_EnderDragon" -> new SpawnMob("ender_dragon", "{}");
                case "MHF_LavaSlime" -> new SpawnMob("magma_cube", "{}");
                case "MHF_MushroomCow" -> new SpawnMob("mooshroom", "{}");
                default -> mobList.contains(owner) ? new SpawnMob(owner.substring(4).toLowerCase(), "{}") : null;
                };
                if (spawnMob != null) {
                    spawnMobs.put(state.getBlock(), spawnMob);
                    state.getBlock().setType(Material.AIR);
                }
            } else if (state instanceof Chest chest) {
                final Inventory inv = chest.getInventory();
                if (chest.customName() == null) continue;
                final String name = plainText().serialize(chest.customName()).toLowerCase();
                boolean removeThis = true;
                switch (name) {
                case "[droppers]": {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            if (item.getType() == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta meta) {
                                final PlayerProfile playerProfile = meta.getPlayerProfile();
                                if (playerProfile == null) continue;
                                final String owner = playerProfile.getName();
                                if (owner == null) continue;
                                dropperSkulls.add(owner);
                            } else {
                                dropperBlocks.add(item.getType());
                            }
                        }
                    }
                    inv.clear();
                    break;
                }
                case "[drops]": {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            drops.add(item.clone());
                        }
                    }
                    inv.clear();
                    break;
                }
                case "[kit]": {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            kit.add(item.clone());
                        }
                    }
                    inv.clear();
                    break;
                }
                case "[exit]": {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            this.exitItem = item.clone();
                        }
                    }
                    inv.clear();
                    break;
                }
                case "[win]": {
                    this.winLocation = state.getBlock().getLocation().add(0.5, 0.0, 0.5);
                    break;
                }
                default:
                    removeThis = false;
                    break;
                }
                if (removeThis) state.getBlock().setType(Material.AIR);
            } else if (state instanceof Sign sign) {
                String firstLine = plainText().serialize(sign.getSide(Side.FRONT).line(0)).toLowerCase();
                boolean removeThis = true;
                if (firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]")) {
                    if ("[spawn]".equals(firstLine)) {
                        spawns.add(state.getLocation().add(0.5, 0.0, 0.5));
                    } else if ("[lookat]".equals(firstLine)) {
                        this.lookAt = state.getLocation().add(0.5, 0.0, 0.5);
                    } else if ("[time]".equals(firstLine)) {
                        long time = 0;
                        String arg = plainText().serialize(sign.getSide(Side.FRONT).line(1)).toLowerCase();
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
                                time = Long.parseLong(plainText().serialize(sign.getSide(Side.FRONT).line(1)));
                            } catch (NumberFormatException nfe) { }
                        }
                        world.setTime(time);
                        if ("lock".equalsIgnoreCase(plainText().serialize(sign.getSide(Side.FRONT).line(2)))) {
                            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                        } else {
                            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                        }
                    } else if ("[weather]".equals(firstLine)) {
                        int duration = 60;
                        if (!plainText().serialize(sign.getSide(Side.FRONT).line(2)).isEmpty()) {
                            String arg = plainText().serialize(sign.getSide(Side.FRONT).line(2)).toLowerCase();
                            if ("lock".equals(arg)) {
                                duration = 99999;
                            } else {
                                try {
                                    duration = Integer.parseInt(plainText().serialize(sign.getSide(Side.FRONT).line(2)));
                                } catch (NumberFormatException nfe) { }
                            }
                        }
                        String weather = plainText().serialize(sign.getSide(Side.FRONT).line(1)).toLowerCase();
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
                            String line = plainText().serialize(sign.getSide(Side.FRONT).line(i));
                            if ("LockArmorStands".equalsIgnoreCase(line)) {
                                lockedEntities.add(EntityType.ARMOR_STAND);
                            } else if ("LockItemFrames".equalsIgnoreCase(line)) {
                                lockedEntities.add(EntityType.ITEM_FRAME);
                            } else if ("LockPaintings".equalsIgnoreCase(line)) {
                                lockedEntities.add(EntityType.PAINTING);
                            } else if ("NoFireTick".equalsIgnoreCase(line)) {
                                world.setGameRule(GameRule.DO_FIRE_TICK, false);
                            } else if ("NoMobGriefing".equalsIgnoreCase(line)) {
                                world.setGameRule(GameRule.MOB_GRIEFING, false);
                            }
                        }
                    } else if (firstLine.equals("[win]")) {
                        this.winLocation = state.getBlock().getLocation().add(0.5, 0.0, 0.5);
                    } else if (firstLine.equals("[credits]")) {
                        for (int i = 1; i < 4; ++i) {
                            String credit = plainText().serialize(sign.getSide(Side.FRONT).line(i));
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
                        String[] tokens = plainText().serialize(sign.getSide(Side.FRONT).line(1)).split(" ");
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
                                    plugin().getLogger().warning(String.format("Bad teleport sign at %d,%d,%d: Number expected, got %s", state.getX(), state.getY(), state.getZ(), token));
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
                            plugin().getLogger().warning(String.format("Bad teleport sign at %d,%d,%d", state.getX(), state.getY(), state.getZ()));
                        }
                    } else if (firstLine.equals("[difficulty]")) {
                        try {
                            this.difficulty = Difficulty.valueOf(plainText().serialize(sign.getSide(Side.FRONT).line(1)).toUpperCase());
                            world.setDifficulty(this.difficulty);
                            plugin().getLogger().info("Set difficulty to " + this.difficulty);
                        } catch (IllegalArgumentException iae) {
                            plugin().getLogger().warning(String.format("Bad difficulty sign at %d,%d,%d", state.getX(), state.getY(), state.getZ()));
                        }
                    } else if (firstLine.equals("[lock]")) {
                        final String lockName = plainText().serialize(sign.getSide(Side.FRONT).line(1))
                            + plainText().serialize(sign.getSide(Side.FRONT).line(2))
                            + plainText().serialize(sign.getSide(Side.FRONT).line(3));
                        final Block attachedBlock;
                        if (sign.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Sign) {
                            attachedBlock = sign.getBlock().getRelative(BlockFace.DOWN);
                        } else if (sign.getBlock().getBlockData() instanceof org.bukkit.block.data.type.WallSign wallSign) {
                            attachedBlock = sign.getBlock().getRelative(wallSign.getFacing().getOppositeFace());
                        } else {
                            continue;
                        }
                        final Material mat = attachedBlock.getType();
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
                        plugin().getLogger().warning("Unrecognized sign: " + firstLine);
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
        for (Player player : world.getPlayers()) {
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
                plugin().getLogger().info("Executing console command: " + command);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Exception e) {
                e.printStackTrace();
                expectMob = false;
                continue;
            }
            if (spawnedMob != null) {
                spawnedMob.setRemoveWhenFarAway(false);
                spawnedMob.setCanPickupItems(false);
                plugin().getLogger().info("Mob spawned: " + spawnedMob.getType() + " " + spawnMob.getId() + " " + spawnMob.getTagData());
                spawnedMob = null;
                removeBlocks.add(block);
            } else {
                plugin().getLogger().warning("Mob did not spawn: " + spawnMob.getId() + " " + spawnMob.getTagData());
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
            if (!(state instanceof Skull skull)) return false;
            final PlayerProfile playerProfile = skull.getPlayerProfile();
            if (playerProfile == null) return false;
            final String owner = playerProfile.getName();
            if (owner == null) return false;
            return dropperSkulls.contains(owner);
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

    protected int getPlayerScore(Player player) {
        return getPlayerScore(player.getUniqueId());
    }

    protected int getPlayerScore(UUID uuid) {
        Integer score = scores.get(uuid);
        if (score == null) return 0;
        return score;
    }

    protected void setPlayerScore(Player player, int score) {
        setPlayerScore(player.getUniqueId(), score);
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
        for (Player player : world.getPlayers()) {
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

    protected void recordPlayerScore(Player player) {
        if (debug) return;
        if (playersOutOfTheGame.contains(player.getUniqueId())) return;
        playersOutOfTheGame.add(player.getUniqueId());
        player.getInventory().clear();
        final int score = getPlayerScore(player);
        final boolean hasFinished = hasFinished(player.getUniqueId());
        SQLMapFinish.insert(player.getUniqueId(), buildWorld.getPath(), startTime, new Date(), score, hasFinished);
    }

    // Event Handlers

    public void onBlockFade(BlockFadeEvent event) {
        event.setCancelled(true);
    }

    public void onBlockGrow(BlockGrowEvent event) {
        event.setCancelled(true);
    }

    public void onBlockForm(BlockFormEvent event) {
        event.setCancelled(true);
    }

    public void onBlockSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }

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

    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        event.getDrops().clear();
        player.getInventory().clear();
        Bukkit.getScheduler().runTaskLater(plugin(), () -> leavePlayer(player), 20L);
    }

    public void onPlayerRespawn(PlayerRespawnEvent event) {
        leavePlayer(event.getPlayer());
    }

    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

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
        final String displayName = plainText().serialize(meta.displayName());
        return lockName.equals(displayName);
    }

    protected boolean playerHoldsExitItem(Player player) {
        return isExitItem(player.getInventory().getItemInMainHand())
            || isExitItem(player.getInventory().getItemInOffHand());
    }

    protected boolean isExitItem(ItemStack item) {
        if (exitItem == null || item == null) return false;
        return exitItem.isSimilar(item);
    }

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

    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        if (playerHoldsExitItem(player)) {
            event.setCancelled(true);
            leavePlayer(event.getPlayer());
            return;
        }
        onClickEntity(player, event.getRightClicked(), event);
    }

    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        if (playerHoldsExitItem(player)) {
            event.setCancelled(true);
            leavePlayer(event.getPlayer());
            return;
        }
        onClickEntity(player, event.getRightClicked(), event);
    }

    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            onClickEntity((Player) event.getDamager(), event.getEntity(), event);
        }
    }

    public void onHangingBreak(HangingBreakEvent event) {
        if (lockedEntities.contains(event.getEntity().getType())) {
            event.setCancelled(true);
        }
    }

    public void leavePlayer(Player player) {
        resetPlayer(player);
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
    }

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

    protected void showHighscore(Player player, List<SQLMapFinish> rows) {
        int i = 1;
        player.sendMessage(text(buildWorld.getName() + " Highscore", BLUE, BOLD));
        player.sendMessage(textOfChildren(text("Rank", DARK_AQUA),
                                          text(" Score", WHITE),
                                          text(" Time", DARK_AQUA),
                                          text(" Name", WHITE)));
        for (SQLMapFinish row : rows) {
            long seconds = row.getDuration() / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            player.sendMessage(textOfChildren(text("" + i, DARK_AQUA),
                                              text(" " + row.getScore(), WHITE),
                                              text(" " + hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s", DARK_AQUA),
                                              text(PlayerCache.nameForUuid(row.getPlayer()), WHITE)));
        }
    }

    protected void showHighscore(Player player) {
        List<SQLMapFinish> rows = SQLMapFinish.listForMap(buildWorld.getPath());
        showHighscore(player, rows);
    }

    protected void showCredits(Player player) {
        StringBuilder sb = new StringBuilder();
        for (String credit : credits) sb.append(" ").append(credit);
        player.sendMessage(text(buildWorld.getName() + " built by " + sb.toString(), BLUE));
    }

    public static void resetPlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        for (var pot : player.getActivePotionEffects()) {
            player.removePotionEffect(pot.getType());
        }
    }
}
