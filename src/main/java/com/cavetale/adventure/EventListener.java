package com.cavetale.adventure;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
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
import org.bukkit.event.world.ChunkLoadEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    protected final AdventurePlugin plugin;

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, AdventurePlugin.plugin());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        plugin.applyAdventure(event.getBlock().getWorld(), adv -> adv.onBlockFade(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        plugin.applyAdventure(event.getBlock().getWorld(), adv -> adv.onBlockGrow(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        plugin.applyAdventure(event.getBlock().getWorld(), adv -> adv.onBlockForm(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        plugin.applyAdventure(event.getBlock().getWorld(), adv -> adv.onBlockSpread(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onEntityExplode(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onEntityDeath(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onPlayerDeath(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerRespawn(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onEntityRegainHealth(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerJoin(event.getPlayer()));
        if (event.getPlayer().getWorld().equals(plugin.getLobbyWorld())) {
            Adventure.resetPlayer(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onEntityPickupItem(event));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractLock(PlayerInteractEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerInteractLock(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractDropper(PlayerInteractEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerInteractDropper(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerInteract(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerInteractEntity(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerInteractAtEntity(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onEntityDamageByEntity(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onHangingBreak(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onCreatureSpawn(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        plugin.applyAdventure(event.getEntity().getWorld(), adv -> adv.onEntityPortal(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        plugin.applyAdventure(event.getPlayer().getWorld(), adv -> adv.onPlayerPortal(event));
    }

    @EventHandler
    protected void onChunkLoad(ChunkLoadEvent event) {
        plugin.applyAdventure(event.getChunk().getWorld(), adv -> adv.onChunkLoad(event));
    }
}
