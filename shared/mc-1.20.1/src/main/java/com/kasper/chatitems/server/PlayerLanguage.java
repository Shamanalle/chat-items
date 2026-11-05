package com.kasper.chatitems.server;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * A player's game language ("ru_ru") on Minecraft up to 1.20.1, where {@code clientInformation()}
 * did not exist yet and Forge adds {@code getLanguage()}.
 */
public final class PlayerLanguage {

    private static Method getter;
    private static boolean missing;

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        if (missing) {
            return "";
        }
        try {
            if (getter == null) {
                getter = player.getClass().getMethod("getLanguage");
            }
            Object value = getter.invoke(player);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            missing = true;
            return "";
        }
    }
}
