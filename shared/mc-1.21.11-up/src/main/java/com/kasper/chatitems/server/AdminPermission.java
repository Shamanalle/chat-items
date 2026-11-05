package com.kasper.chatitems.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * Who may change the server's settings (Minecraft 1.21.11 and later, which replaced permission levels
 * with permission sets): the console, game masters (operators of level 2 or more), and the singleplayer host.
 */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.getPlayer() == null
                || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || source.getServer().isSingleplayer();
    }
}
