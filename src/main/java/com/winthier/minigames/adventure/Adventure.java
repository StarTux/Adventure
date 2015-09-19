package com.winthier.minigames.adventure;

import com.winthier.minigames.MinigamesPlugin;
import com.winthier.minigames.event.player.PlayerLeaveEvent;
import com.winthier.minigames.game.Game;
import com.winthier.minigames.util.BukkitFuture;
import com.winthier.minigames.util.Msg;
import com.winthier.minigames.util.Players;
import com.winthier.minigames.util.Title;
import com.winthier.minigames.util.WorldLoader;
import java.util.ArrayList;
import java.util.Date;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
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

public class Adventure extends Game implements Listener {
    @Value static class ChunkCoord { int x, z; };
    static interface Trigger {
        void call(Block block, Player player);
    }
    // const
    private final String SIDEBAR_OBJECTIVE = "Sidebar";
    // minigame stuf
    private World world;
    private BukkitRunnable tickTask;
    private boolean solo = false;
    // chunk processing
    private Set<ChunkCoord> processedChunks = new HashSet<>();
    private Map<Block, EntityType> spawnEntities = new HashMap<>();
    private boolean didSomeoneJoin = false;
    // level config
    private String mapId = "Test";
    private String mapPath = "Adventure/Test";
    private boolean debug = false;
    private final List<ItemStack> drops = new ArrayList<>();
    private final List<String> dropperSkulls = new ArrayList<>();
    private final List<Material> dropperBlocks = new ArrayList<>();
    private final List<String> credits = new ArrayList<>();
    private final List<ItemStack> kit = new ArrayList<>();
    private ItemStack exitItem;
    private Location winLocation;
    private final Map<Block, Trigger> triggers = new HashMap<>();
    Difficulty difficulty = Difficulty.NORMAL;
    // players
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Set<UUID> playersOutOfTheGame = new HashSet<>();
    private final Map<UUID, Integer> winCounters = new HashMap<>();
    private final Set<UUID> finished = new HashSet<>();
    // score keeping
    private Scoreboard scoreboard;
    private final Highscore highscore = new Highscore();
    private Date startTime;
    // state
    private final Random random = new Random(System.currentTimeMillis());
    private long ticks;
    private long emptyTicks;
    
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
        spawnEntities.clear();
    }

    private void onWorldsLoaded(WorldLoader worldLoader) {
        this.world = worldLoader.getWorld(0);
        world.setDifficulty(difficulty);
        world.setPVP(false);
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobLoot", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
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

    private void onTick() {
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
        if (ticks % 20L == 0L) {
            world.setTime(18000);
            world.setStorm(true);
            world.setThundering(false);
        }
        processPlayerChunks();
        processSpawnEntities();
        processWinners();
        countPlayerScores();
    }

    @Override
    public void onPlayerReady(Player player) {
        didSomeoneJoin = this.didSomeoneJoin;
        this.didSomeoneJoin = true;
        Players.reset(player);
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
        return world.getSpawnLocation();
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

    private void processWinners() {
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

    private void processPlayerChunks() {
        for (Player player : getOnlinePlayers()) {
            Chunk chunk = player.getLocation().getChunk();
            processChunkArea(chunk.getX(), chunk.getZ());
        }
    }

    private void processChunkArea(Chunk chunk) {
        processChunkArea(chunk.getX(), chunk.getZ());
    }
    
    private void processChunkArea(int cx, int cz) {
        final int RADIUS = 4;
        for (int dx = -RADIUS; dx <= RADIUS; ++dx) {
            for (int dz = -RADIUS; dz <= RADIUS; ++dz) {
                final int x = cx + dx;
                final int z = cz + dz;
                processChunk(x, z);
            }
        }
    }

    private void processChunk(int x, int z) {
        final ChunkCoord cc = new ChunkCoord(x, z);
        if (processedChunks.contains(cc)) return;
        processedChunks.add(cc);
        // Process
        final Chunk chunk = world.getChunkAt(cc.getX(), cc.getZ());
        chunk.load();
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Skull) {
                EntityType entityType = null;
                final Skull skull = (Skull)state;
                // switch (skull.getSkullType()) {
                // case CREEPER:
                //     entityType = EntityType.CREEPER;
                //     break;
                // case SKELETON:
                //     entityType = EntityType.SKELETON;
                //     break;
                // case ZOMBIE:
                //     entityType = EntityType.ZOMBIE;
                //     break;
                // }
                if (skull.hasOwner()) {
                    String owner = skull.getOwner();
                    if (owner.equals("MHF_Creeper")) entityType = EntityType.CREEPER;
                    if (owner.equals("MHF_Skeleton")) entityType = EntityType.SKELETON;
                    if (owner.equals("MHF_Zombie")) entityType = EntityType.ZOMBIE;
                    if (owner.equals("MHF_Spider")) entityType = EntityType.SPIDER;
                    if (owner.equals("MHF_CaveSpider")) entityType = EntityType.CAVE_SPIDER;
                    if (owner.equals("MHF_Blaze")) entityType = EntityType.BLAZE;
                    if (owner.equals("MHF_Enderman")) entityType = EntityType.ENDERMAN;
                    if (owner.equals("MHF_Ghast")) entityType = EntityType.GHAST;
                    if (owner.equals("MHF_Golem")) entityType = EntityType.BLAZE;
                    if (owner.equals("MHF_LavaSlime")) entityType = EntityType.MAGMA_CUBE;
                    if (owner.equals("MHF_PigZombie")) entityType = EntityType.PIG_ZOMBIE;
                    if (owner.equals("MHF_Slime")) entityType = EntityType.SLIME;
                    if (owner.equals("MHF_WSkeleton")) entityType = EntityType.SKELETON; // TODO
                    if (owner.equals("MHF_Wither")) entityType = EntityType.WITHER;
                    // Non-hostile
                    if (owner.equals("MHF_Cow")) entityType = EntityType.COW;
                    if (owner.equals("MHF_Chicken")) entityType = EntityType.CHICKEN;
                    if (owner.equals("MHF_Sheep")) entityType = EntityType.SHEEP;
                    if (owner.equals("MHF_Squid")) entityType = EntityType.SQUID;
                    if (owner.equals("MHF_Pig")) entityType = EntityType.PIG;
                }
                if (entityType != null) {
                    spawnEntities.put(state.getBlock(), entityType);
                    state.getBlock().setType(Material.AIR);
                }
            } else if (state instanceof Chest) {
                final Inventory inv = ((Chest)state).getInventory();
                if ("[DROPPERS]".equals(inv.getName())) {
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
                    state.getBlock().setType(Material.AIR);
                } else if ("[DROPS]".equals(inv.getName())) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            drops.add(item.clone());
                        }
                    }
                    inv.clear();
                    state.getBlock().setType(Material.AIR);
                } else if ("[KIT]".equals(inv.getName())) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            kit.add(item.clone());
                        }
                    }
                    inv.clear();
                    state.getBlock().setType(Material.AIR);
                } else if ("[EXIT]".equals(inv.getName())) {
                    for (ItemStack item : inv.getContents()) {
                        if (item != null) {
                            this.exitItem = item.clone();
                        }
                    }
                    inv.clear();
                    state.getBlock().setType(Material.AIR);
                } else if ("[WIN]".equals(inv.getName())) {
                    this.winLocation = state.getBlock().getLocation().add(0.5, 0.0, 0.5);
                    state.getBlock().setType(Material.AIR);
                }
            } else if (state instanceof Sign) {
                final Sign sign = (Sign)state;
                String firstLine = sign.getLine(0).toLowerCase();
                if (firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]")) {
                    if (firstLine.equals("[win]")) {
                        this.winLocation = state.getBlock().getLocation().add(0.5, 0.0, 0.5);
                        state.getBlock().setType(Material.AIR);
                    } else if (firstLine.equals("[credits]")) {
                        for (int i = 1; i < 4; ++i) {
                            String credit = sign.getLine(i);
                            if (credit != null) credits.add(credit);
                        }
                        state.getBlock().setType(Material.AIR);
                    } else if (firstLine.equals("[finish]")) {
                        triggers.put(state.getBlock(), new Trigger() {
                            @Override public void call(Block block, Player player) {
                                setFinished(player.getUniqueId());
                                recordPlayerScore(player);
                                if (winLocation != null) player.teleport(winLocation);
                                winCounters.put(player.getUniqueId(), 0);
                                player.getInventory().setItem(8, exitItem.clone());
                            }
                        });
                        state.getBlock().setType(Material.AIR);
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
                        state.getBlock().setType(Material.AIR);
                    } else if (firstLine.equals("[difficulty]")) {
                        try {
                            this.difficulty = Difficulty.valueOf(sign.getLine(1).toUpperCase());
                            world.setDifficulty(this.difficulty);
                            getLogger().info("Set difficulty to " + this.difficulty);
                        } catch (IllegalArgumentException iae) {
                            getLogger().warning(String.format("Bad difficulty sign at %d,%d,%d", state.getX(), state.getY(), state.getZ()));
                        }
                        state.getBlock().setType(Material.AIR);
                    } else {
                        getLogger().warning("Unrecognized sign: " + firstLine);
                    }
                }
            }
        }
    }

    private boolean isNearAnyPlayer(Block block) {
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
            return true;
        }
        return false;
    }

    private void processSpawnEntities() {
        final List<Block> removeBlocks = new ArrayList<>();
        for (Block block : spawnEntities.keySet()) {
            if (!isNearAnyPlayer(block)) continue;
            removeBlocks.add(block);
            final EntityType entityType = spawnEntities.get(block);
            LivingEntity entity = (LivingEntity)world.spawnEntity(block.getLocation().add(0.5, 0.0, 0.5), entityType);
            entity.setRemoveWhenFarAway(false);
        }
        for (Block block : removeBlocks) spawnEntities.remove(block);
        removeBlocks.clear();
    }

    private ItemStack randomDrop() {
        if (drops.isEmpty()) return null;
        return drops.get(random.nextInt(drops.size())).clone();
    }

    private void randomDrop(Location loc) {
        final ItemStack stack = randomDrop();
        if (stack == null) return;
        final Item item = loc.getWorld().dropItem(loc, stack);
        item.setPickupDelay(0);
    }

    private boolean isDropper(Block block)
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

    private boolean isDroppedItem(ItemStack item)
    {
        for (ItemStack drop : drops) {
            if (drop.isSimilar(item)) return true;
        }
        return false;
    }

    private void setupScoreboard() {
        scoreboard = MinigamesPlugin.getInstance().getServer().getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(SIDEBAR_OBJECTIVE, "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(Msg.format("&9Score"));
    }

    private int getPlayerScore(Player player) {
        return getPlayerScore(player.getUniqueId());
    }

    private int getPlayerScore(UUID uuid) {
        Integer score = scores.get(uuid);
        if (score == null) return 0;
        return score;
    }

    private void setPlayerScore(Player player, int score) {
        if (setPlayerScore(player.getUniqueId(), score)) {
            scoreboard.getObjective(SIDEBAR_OBJECTIVE).getScore(player.getName()).setScore(score);
        }
    }

    private boolean setPlayerScore(UUID uuid, int score) {
        Integer oldScore = scores.get(uuid);
        if (oldScore == null || oldScore != score) {
            scores.put(uuid, score);
            return true;
        } else {
            return false;
        }
    }

    private int countPlayerScore(Player player) {
        int result = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!isDroppedItem(item)) continue;
            result += item.getAmount();
        }
        return result;
    }

    private void countPlayerScores() {
        for (Player player : getOnlinePlayers()) {
            if (playersOutOfTheGame.contains(player.getUniqueId())) continue;
            int score = countPlayerScore(player);
            setPlayerScore(player, score);
        }
    }

    private boolean hasFinished(UUID uuid) {
        return finished.contains(uuid);
    }

    private void setFinished(UUID uuid) {
        finished.add(uuid);
    }

    private void recordPlayerScore(Player player) {
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
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (isDropper(event.getBlock())) {
            event.setCancelled(true);
            randomDrop(event.getBlock().getLocation().add(0.5, 0.0, 0.50));
            event.getBlock().setType(Material.AIR);
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
    public void onHangingBreak(HangingBreakEvent event) {
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

    // @EventHandler(ignoreCancelled = true)
    // public void onFoodLevelChange(FoodLevelChangeEvent event)
    // {
    //     event.setCancelled(true);
    // }

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


    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getPlayer().getItemInHand();
        if (item.isSimilar(this.exitItem)) {
            event.setCancelled(true);
            MinigamesPlugin.getInstance().leavePlayer(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getPlayer().getItemInHand();
        if (item.isSimilar(this.exitItem)) {
            event.setCancelled(true);
            MinigamesPlugin.getInstance().leavePlayer(event.getPlayer());
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
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        event.setCancelled(true);
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
    
    private void checkPortalBlock(final Block block, Set<Block> blocks, Set<Block> checked) {
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

    private Set<Block> getPortalNear(final Block block) {
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
