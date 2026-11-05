package com.kasper.chatitems.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit, Spigot, Paper and Purpur: one main thread for everything. */
final class BukkitScheduling implements Scheduling {

    private final JavaPlugin plugin;

    BukkitScheduling(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void everyTick(Runnable tick) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, tick, 1L, 1L);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void cancelAll() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }
}
