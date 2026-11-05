package com.kasper.chatitems.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Where the plugin's work runs. On Bukkit, Spigot and Paper everything runs on the main thread; on
 * Folia each region ticks on its own thread and player inventory operations run on the player's entity
 * scheduler.
 */
public interface Scheduling {

    /** Runs {@code tick} every server tick: on the main thread, or on Folia's global region. */
    void everyTick(Runnable tick);

    /** Runs {@code task} on the thread owning {@code player} (main thread on Bukkit/Paper, entity scheduler on Folia). */
    void runForPlayer(Player player, Runnable task);

    void cancelAll();

    static Scheduling create(JavaPlugin plugin) {
        if (isFolia()) {
            try {
                return (Scheduling) Class.forName("com.kasper.chatitems.bukkit.FoliaScheduling")
                        .getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia scheduling is unavailable", e);
            }
        }
        return new BukkitScheduling(plugin);
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
