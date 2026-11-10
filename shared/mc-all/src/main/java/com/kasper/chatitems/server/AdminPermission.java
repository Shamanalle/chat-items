package com.kasper.chatitems.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may change the server's settings (Minecraft up to 1.21.10): the console, operators of level 2
 * or more, and the singleplayer world host (not LAN guests).
 */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = McCompat.player(source);
        return player == null
                || source.hasPermission(2)
                || McCompat.isSingleplayerHost(source.getServer(), player);
    }
}
