package com.kasper.chatitems;

import java.util.List;
import java.util.Locale;

/**
 * Supported chat placeholders that players can type in chat messages.
 */
public enum TokenType {
    /** Held item in main hand (or offhand fallback), with aliases {@code [i]} and {@code [hand]}. */
    ITEM("[item]", Jv.listOf("[item]", "[i]", "[hand]")),
    /** Full player inventory snapshot (main 36 slots + 4 armor + offhand). */
    INV("[inv]", Jv.listOf("[inv]", "[inventory]")),
    /** Player's ender chest snapshot (27 slots). */
    ENDER("[ender]", Jv.listOf("[ender]", "[enderchest]", "[ec]"));

    private final String canonical;
    private final List<String> aliases;

    TokenType(String canonical, List<String> aliases) {
        this.canonical = canonical;
        this.aliases = aliases;
    }

    public String canonical() {
        return canonical;
    }

    public List<String> aliases() {
        return aliases;
    }

    /**
     * Resolves a bracketed token such as {@code "[item]"}, {@code "[i]"}, {@code "[hand]"},
     * {@code "[inv]"}, or {@code "[ender]"} (case-insensitive), or returns {@code null} if unknown.
     */
    public static TokenType fromBracketed(String raw) {
        if (raw == null || raw.length() < 3 || raw.charAt(0) != '[' || raw.charAt(raw.length() - 1) != ']') {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        for (TokenType type : values()) {
            if (type.aliases.contains(lower)) {
                return type;
            }
        }
        return null;
    }
}
