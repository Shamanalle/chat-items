package com.kasper.chatitems;

import java.nio.file.Path;

/**
 * Shared server-side state and constants for Chat Items across Fabric, Forge, NeoForge, and Paper/Folia/Spigot.
 */
public final class ChatItems {

    public static final String MOD_ID = "chatitems";
    public static final String MOD_NAME = "Chat Items";
    public static final String VIEW_COMMAND_PREFIX = "/chatitems view ";

    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_ALLOW_ITEM = true;
    public static final boolean DEFAULT_ALLOW_INV = true;
    public static final boolean DEFAULT_ALLOW_ENDER = true;

    public static final int DEFAULT_MAX_TOKENS = 3;
    public static final int MIN_MAX_TOKENS = 1;
    public static final int MAX_MAX_TOKENS = 16;

    public static final int DEFAULT_COOLDOWN_SECONDS = 3;
    public static final int MIN_COOLDOWN_SECONDS = 0;
    public static final int MAX_COOLDOWN_SECONDS = 300;

    public static final int DEFAULT_SNAPSHOT_TTL_SECONDS = 300;
    public static final int MIN_SNAPSHOT_TTL_SECONDS = 10;
    public static final int MAX_SNAPSHOT_TTL_SECONDS = 3600;

    public static final int DEFAULT_MAX_SNAPSHOTS = 256;
    public static final int MIN_MAX_SNAPSHOTS = 16;
    public static final int MAX_MAX_SNAPSHOTS = 4096;

    private static volatile ServerSettings settings = new ServerSettings(null);
    private static volatile String platform = "server";
    private static final CooldownTracker COOLDOWNS = new CooldownTracker();

    private ChatItems() {
    }

    /**
     * Initializes the server-side settings file and platform label.
     */
    public static void init(Path configFile, String platformName) {
        if (!Jv.isBlank(platformName)) {
            platform = platformName;
        }
        ServerSettings s = new ServerSettings(configFile);
        s.load();
        settings = s;
        COOLDOWNS.clearAll();
    }

    public static void useSettings(ServerSettings s) {
        if (s != null) {
            settings = s;
        }
    }

    public static ServerSettings settings() {
        return settings;
    }

    public static String platform() {
        return platform;
    }

    public static CooldownTracker cooldowns() {
        return COOLDOWNS;
    }

    /**
     * Formats the stack count suffix inside a bracketed item tag: empty for single items,
     * {@code " ×64"} for stacks of 2 or more.
     */
    public static String countSuffix(int count) {
        return count > 1 ? " \u00d7" + count : "";
    }
}
