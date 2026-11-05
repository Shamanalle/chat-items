package com.kasper.chatitems.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Folia: global region for periodic settings checks, entity scheduler for opening player inventories. */
public final class FoliaScheduling implements Scheduling {

    private final JavaPlugin plugin;

    public FoliaScheduling(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void everyTick(Runnable tick) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> tick.run(), 1L, 1L);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
