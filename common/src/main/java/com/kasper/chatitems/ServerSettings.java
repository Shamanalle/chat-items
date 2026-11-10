package com.kasper.chatitems;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The server's settings file, {@value #FILE_NAME}: master switch, per-token switches ({@code [item]},
 * {@code [inv]}, {@code [ender]}), per-message token limit, per-player cooldown, and snapshot TTL.
 * Written with comments in English and Russian on first start, hot-reloaded when modified on disk,
 * and saved when an admin command changes a value.
 */
public final class ServerSettings {

    public static final String FILE_NAME = "chatitems-server.properties";

    private final Path file;
    private long modified = Long.MIN_VALUE;

    private volatile boolean enabled = ChatItems.DEFAULT_ENABLED;
    private volatile boolean allowItem = ChatItems.DEFAULT_ALLOW_ITEM;
    private volatile boolean allowInv = ChatItems.DEFAULT_ALLOW_INV;
    private volatile boolean allowEnder = ChatItems.DEFAULT_ALLOW_ENDER;
    private volatile int maxTokens = ChatItems.DEFAULT_MAX_TOKENS;
    private volatile int cooldownSeconds = ChatItems.DEFAULT_COOLDOWN_SECONDS;
    private volatile int snapshotTtlSeconds = ChatItems.DEFAULT_SNAPSHOT_TTL_SECONDS;
    private volatile String language = ServerText.DEFAULT_LANGUAGE;

    /** @param file the settings file; null keeps everything in memory (unit tests) */
    public ServerSettings(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean allowItem() {
        return allowItem;
    }

    public boolean allowInv() {
        return allowInv;
    }

    public boolean allowEnder() {
        return allowEnder;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int snapshotTtlSeconds() {
        return snapshotTtlSeconds;
    }

    public String language() {
        return language;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public void setAllowItem(boolean value) {
        allowItem = value;
    }

    public void setAllowInv(boolean value) {
        allowInv = value;
    }

    public void setAllowEnder(boolean value) {
        allowEnder = value;
    }

    public void setMaxTokens(int value) {
        maxTokens = clamp(value, ChatItems.MIN_MAX_TOKENS, ChatItems.MAX_MAX_TOKENS);
    }

    public void setCooldownSeconds(int value) {
        cooldownSeconds = clamp(value, ChatItems.MIN_COOLDOWN_SECONDS, ChatItems.MAX_COOLDOWN_SECONDS);
    }

    public void setSnapshotTtlSeconds(int value) {
        snapshotTtlSeconds = clamp(value, ChatItems.MIN_SNAPSHOT_TTL_SECONDS, ChatItems.MAX_SNAPSHOT_TTL_SECONDS);
    }

    public void setLanguage(String value) {
        language = Jv.isBlank(value) ? ServerText.DEFAULT_LANGUAGE : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validMaxTokens(int value) {
        return value >= ChatItems.MIN_MAX_TOKENS && value <= ChatItems.MAX_MAX_TOKENS;
    }

    public static boolean validCooldown(int value) {
        return value >= ChatItems.MIN_COOLDOWN_SECONDS && value <= ChatItems.MAX_COOLDOWN_SECONDS;
    }

    public static boolean validTtl(int value) {
        return value >= ChatItems.MIN_SNAPSHOT_TTL_SECONDS && value <= ChatItems.MAX_SNAPSHOT_TTL_SECONDS;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Reads the file, or writes it with the defaults when there is none yet. */
    public synchronized void load() {
        if (file == null) {
            return;
        }
        try {
            if (!Files.exists(file)) {
                save();
                return;
            }
            modified = Files.getLastModifiedTime(file).toMillis();
            read(Jv.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ModLog.failure("Reading " + file, e);
        }
    }

    /** Reads the file again when it changed on disk since the last read or write. @return whether it did */
    public synchronized boolean reloadIfChanged() {
        if (file == null) {
            return false;
        }
        try {
            if (!Files.exists(file) || Files.getLastModifiedTime(file).toMillis() == modified) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        load();
        return true;
    }

    /** Parses the text of a settings file into this object. Unknown or broken lines keep the defaults. */
    synchronized void read(String text) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));
        enabled = bool(p, "enabled", ChatItems.DEFAULT_ENABLED);
        allowItem = bool(p, "allow_item", ChatItems.DEFAULT_ALLOW_ITEM);
        allowInv = bool(p, "allow_inv", ChatItems.DEFAULT_ALLOW_INV);
        allowEnder = bool(p, "allow_ender", ChatItems.DEFAULT_ALLOW_ENDER);
        String maxTokensKey = p.getProperty("max_tokens") != null || p.getProperty("max_tokens_per_message") == null
                ? "max_tokens" : "max_tokens_per_message";
        maxTokens = integer(p, maxTokensKey, ChatItems.DEFAULT_MAX_TOKENS,
                ChatItems.MIN_MAX_TOKENS, ChatItems.MAX_MAX_TOKENS);
        cooldownSeconds = integer(p, "cooldown_seconds", ChatItems.DEFAULT_COOLDOWN_SECONDS,
                ChatItems.MIN_COOLDOWN_SECONDS, ChatItems.MAX_COOLDOWN_SECONDS);
        snapshotTtlSeconds = integer(p, "snapshot_ttl_seconds", ChatItems.DEFAULT_SNAPSHOT_TTL_SECONDS,
                ChatItems.MIN_SNAPSHOT_TTL_SECONDS, ChatItems.MAX_SNAPSHOT_TTL_SECONDS);
        String lang = p.getProperty("language", ServerText.DEFAULT_LANGUAGE).trim();
        language = Jv.isBlank(lang) ? ServerText.DEFAULT_LANGUAGE : lang.toLowerCase(Locale.ROOT);
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        String trimmed = v.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("true") || trimmed.equals("on") || trimmed.equals("yes") || trimmed.equals("1")) {
            return true;
        }
        if (trimmed.equals("false") || trimmed.equals("off") || trimmed.equals("no") || trimmed.equals("0")) {
            return false;
        }
        ModLog.warn(FILE_NAME + ": " + key + "=" + v + " is not true/false; using " + fallback);
        return fallback;
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(v.trim());
            if (n >= min && n <= max) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        ModLog.warn(FILE_NAME + ": " + key + "=" + v + " is not an integer from " + min + " to " + max
                + "; using " + fallback);
        return fallback;
    }

    /** Writes the current values, preserving custom comments and unknown keys if the file already exists. */
    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            String existing = Files.exists(file) ? Jv.readString(file, StandardCharsets.UTF_8) : null;
            Jv.writeString(file, existing == null ? write() : updateExisting(existing), StandardCharsets.UTF_8);
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException e) {
            ModLog.failure("Writing " + file, e);
        }
    }

    /** Updates known keys in-place inside an existing settings file while keeping custom comments and unknown keys. */
    synchronized String updateExisting(String existing) {
        String[][] entries = {
                {"enabled", String.valueOf(enabled)},
                {"allow_item", String.valueOf(allowItem)},
                {"allow_inv", String.valueOf(allowInv)},
                {"allow_ender", String.valueOf(allowEnder)},
                {"max_tokens", String.valueOf(maxTokens)},
                {"cooldown_seconds", String.valueOf(cooldownSeconds)},
                {"snapshot_ttl_seconds", String.valueOf(snapshotTtlSeconds)},
                {"language", language},
        };
        boolean[] seen = new boolean[entries.length];
        String[] rawLines = existing.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int count = rawLines.length;
        if (count > 0 && rawLines[count - 1].isEmpty()) {
            count--;
        }
        List<String> out = new ArrayList<>(count + entries.length);
        for (int i = 0; i < count; i++) {
            String line = rawLines[i];
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                int sep = findKeySeparator(trimmed);
                if (sep > 0) {
                    String key = trimmed.substring(0, sep).trim();
                    String lookup = key.equals("max_tokens_per_message") ? "max_tokens" : key;
                    int idx = indexOfKey(entries, lookup);
                    if (idx >= 0) {
                        if (!seen[idx]) {
                            out.add(entries[idx][0] + "=" + entries[idx][1]);
                            seen[idx] = true;
                        }
                        continue;
                    }
                }
            }
            out.add(line);
        }
        for (int i = 0; i < entries.length; i++) {
            if (!seen[i]) {
                out.add(entries[i][0] + "=" + entries[i][1]);
            }
        }
        return String.join(System.lineSeparator(), out) + System.lineSeparator();
    }

    private static int findKeySeparator(String s) {
        int eq = s.indexOf('=');
        int col = s.indexOf(':');
        if (eq < 0) {
            return col;
        }
        if (col < 0) {
            return eq;
        }
        return Math.min(eq, col);
    }

    private static int indexOfKey(String[][] entries, String key) {
        for (int i = 0; i < entries.length; i++) {
            if (entries[i][0].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** The text of the settings file for the current values. */
    synchronized String write() {
        List<String> lines = new ArrayList<>();
        lines.add("# Chat Items: server settings. Changes made here are read without a restart.");
        lines.add("# Chat Items: настройки сервера. Изменения в этом файле применяются без перезапуска.");
        lines.add("");
        lines.add("# Master switch for all chat placeholders ([item], [armor], [offhand], [slot1-9], [inv], [ender]): true or false.");
        lines.add("# Главный переключатель всех тегов в чате ([item], [armor], [offhand], [slot1-9], [inv], [ender]): true или false.");
        lines.add("enabled=" + enabled);
        lines.add("");
        lines.add("# Allow item placeholders ([item], [i], [hand], [offhand], [armor], [slot1] - [slot9]): true or false.");
        lines.add("# Разрешить предметные теги ([item], [i], [hand], [offhand], [armor], [slot1] - [slot9]): true или false.");
        lines.add("allow_item=" + allowItem);
        lines.add("");
        lines.add("# Allow [inv] (and [inventory]) to share a read-only inventory snapshot: true or false.");
        lines.add("# Разрешить [inv] (и [inventory]) для показа неизменяемого снимка инвентаря: true или false.");
        lines.add("allow_inv=" + allowInv);
        lines.add("");
        lines.add("# Allow [ender] (and [enderchest], [ec]) to share a read-only ender chest snapshot: true or false.");
        lines.add("# Разрешить [ender] (и [enderchest], [ec]) для показа снимка эндер-сундука: true или false.");
        lines.add("allow_ender=" + allowEnder);
        lines.add("");
        lines.add("# Maximum number of tokens replaced in a single chat message (1 - 16).");
        lines.add("# Максимальное число тегов в одном сообщении чата (1 - 16).");
        lines.add("max_tokens=" + maxTokens);
        lines.add("");
        lines.add("# Cooldown in seconds between chat messages with tokens per player (0 - 300, 0 = no cooldown).");
        lines.add("# Кулдаун в секундах между сообщениями с тегами для одного игрока (0 - 300, 0 = без кулдауна).");
        lines.add("cooldown_seconds=" + cooldownSeconds);
        lines.add("");
        lines.add("# How long [inv], [ender], shulker, and bundle snapshots stay clickable in seconds (10 - 3600).");
        lines.add("# Время жизни снимков [inv], [ender], шалкеров и мешков в кэше в секундах (10 - 3600).");
        lines.add("snapshot_ttl_seconds=" + snapshotTtlSeconds);
        lines.add("");
        lines.add("# Default language for console replies and players with unknown locale (en_us, ru_ru, ...).");
        lines.add("# Язык ответов консоли и игрокам с неизвестной локалью (en_us, ru_ru, ...).");
        lines.add("language=" + language);
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }
}
