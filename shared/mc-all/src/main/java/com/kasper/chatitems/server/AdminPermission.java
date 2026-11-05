package com.kasper.chatitems.server;

import net.minecraft.commands.CommandSourceStack;

/**
 * Who may change the server's settings (Minecraft up to 1.21.10): the console, operators of level 2
 * or more, and the singleplayer world host.
 */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return McCompat.player(source) == null || source.hasPermission(2) || source.getServer().isSingleplayer();
    }
}
