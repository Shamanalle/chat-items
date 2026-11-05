package com.kasper.chatitems.server;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * A player's game language ("ru_ru") on Minecraft up to 1.20.2, where {@code clientInformation()}
 * was added in 1.20.2 and Forge adds {@code getLanguage()} on earlier versions.
 */
public final class PlayerLanguage {

    private static Method getLanguage;
    private static Method clientInformation;
    private static Method infoLanguage;
    private static boolean initialized;

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        try {
            if (!initialized) {
                initialized = true;
                try {
                    getLanguage = player.getClass().getMethod("getLanguage");
                } catch (NoSuchMethodException ignored) {
                    try {
                        clientInformation = player.getClass().getMethod("clientInformation");
                        infoLanguage = clientInformation.getReturnType().getMethod("language");
                    } catch (NoSuchMethodException ignored2) {
                        // neither available
                    }
                }
            }
            if (getLanguage != null) {
                Object value = getLanguage.invoke(player);
                return value == null ? "" : value.toString();
            }
            if (clientInformation != null && infoLanguage != null) {
                Object info = clientInformation.invoke(player);
                if (info != null) {
                    Object value = infoLanguage.invoke(info);
                    return value == null ? "" : value.toString();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // fall through
        }
        return "";
    }
}
