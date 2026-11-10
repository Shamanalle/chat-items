package com.kasper.chatitems.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's game language ("ru_ru") on Minecraft up to 1.20.2, where {@code clientInformation()}
 * was added in 1.20.2, Forge adds {@code getLanguage()} on earlier versions, and Fabric 1.16.5 – 1.20.1
 * captures the client's {@code ServerboundClientInformationPacket} language on the Netty channel.
 */
public final class PlayerLanguage {

    private static final Map<UUID, String> PACKET_LANGUAGES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Field>> PACKET_LANG_FIELD = new ConcurrentHashMap<>();

    private static Method getLanguage;
    private static Method clientInformation;
    private static Method infoLanguage;
    private static volatile boolean initialized;

    private PlayerLanguage() {
    }

    private static void init(ServerPlayer player) {
        if (initialized || player == null) {
            return;
        }
        synchronized (PlayerLanguage.class) {
            if (initialized) {
                return;
            }
            try {
                getLanguage = player.getClass().getMethod("getLanguage");
            } catch (NoSuchMethodException ignored) {
                try {
                    clientInformation = player.getClass().getMethod("clientInformation");
                    infoLanguage = clientInformation.getReturnType().getMethod("language");
                } catch (NoSuchMethodException ignored2) {
                    // neither available (vanilla / Fabric 1.16.5 - 1.20.1)
                }
            }
            initialized = true;
        }
    }

    public static boolean hasNativeMethod(ServerPlayer player) {
        init(player);
        return getLanguage != null || (clientInformation != null && infoLanguage != null);
    }

    public static void capturePacket(UUID playerId, Object packet) {
        if (playerId == null || packet == null) {
            return;
        }
        Optional<Field> fieldOpt = PACKET_LANG_FIELD.computeIfAbsent(
                packet.getClass(), PlayerLanguage::findClientInfoLangField);
        if (!fieldOpt.isPresent()) {
            return;
        }
        try {
            Object raw = fieldOpt.get().get(packet);
            if (raw instanceof String && !((String) raw).isEmpty()) {
                PACKET_LANGUAGES.put(playerId, (String) raw);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // ignore unexpected packet access
        }
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            PACKET_LANGUAGES.remove(playerId);
        }
    }

    private static Optional<Field> findClientInfoLangField(Class<?> packetClass) {
        try {
            boolean hasChatVisibility = false;
            Field stringField = null;
            for (Field f : packetClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (f.getType() == ChatVisiblity.class) {
                    hasChatVisibility = true;
                } else if (f.getType() == String.class && stringField == null) {
                    stringField = f;
                }
            }
            if (hasChatVisibility && stringField != null) {
                stringField.setAccessible(true);
                return Optional.of(stringField);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // not the client information packet
        }
        return Optional.empty();
    }

    public static String of(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        try {
            init(player);
            if (getLanguage != null) {
                Object value = getLanguage.invoke(player);
                if (value != null && !value.toString().isEmpty()) {
                    return value.toString();
                }
            }
            if (clientInformation != null && infoLanguage != null) {
                Object info = clientInformation.invoke(player);
                if (info != null) {
                    Object value = infoLanguage.invoke(info);
                    if (value != null && !value.toString().isEmpty()) {
                        return value.toString();
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // fall through to packet cache
        }
        return PACKET_LANGUAGES.getOrDefault(player.getUUID(), "");
    }
}
