package com.kasper.chatitems;

import java.util.List;
import java.util.Locale;

/**
 * Supported chat placeholders that players can type in chat messages.
 */
public enum TokenType {
    /** Held item in main hand (or offhand fallback), with aliases {@code [i]} and {@code [hand]}. */
    ITEM("[item]", Jv.listOf("[item]", "[i]", "[hand]"), -1),
    /** Item in off-hand, with alias {@code [off]}. */
    OFFHAND("[offhand]", Jv.listOf("[offhand]", "[off]"), -1),
    /** All worn armor pieces inline, with alias {@code [armour]}. */
    ARMOR("[armor]", Jv.listOf("[armor]", "[armour]"), -1),
    /** Hotbar slot 1 (inventory index 0), with alias {@code [s1]}. */
    SLOT_1("[slot1]", Jv.listOf("[slot1]", "[s1]"), 0),
    /** Hotbar slot 2 (inventory index 1), with alias {@code [s2]}. */
    SLOT_2("[slot2]", Jv.listOf("[slot2]", "[s2]"), 1),
    /** Hotbar slot 3 (inventory index 2), with alias {@code [s3]}. */
    SLOT_3("[slot3]", Jv.listOf("[slot3]", "[s3]"), 2),
    /** Hotbar slot 4 (inventory index 3), with alias {@code [s4]}. */
    SLOT_4("[slot4]", Jv.listOf("[slot4]", "[s4]"), 3),
    /** Hotbar slot 5 (inventory index 4), with alias {@code [s5]}. */
    SLOT_5("[slot5]", Jv.listOf("[slot5]", "[s5]"), 4),
    /** Hotbar slot 6 (inventory index 5), with alias {@code [s6]}. */
    SLOT_6("[slot6]", Jv.listOf("[slot6]", "[s6]"), 5),
    /** Hotbar slot 7 (inventory index 6), with alias {@code [s7]}. */
    SLOT_7("[slot7]", Jv.listOf("[slot7]", "[s7]"), 6),
    /** Hotbar slot 8 (inventory index 7), with alias {@code [s8]}. */
    SLOT_8("[slot8]", Jv.listOf("[slot8]", "[s8]"), 7),
    /** Hotbar slot 9 (inventory index 8), with alias {@code [s9]}. */
    SLOT_9("[slot9]", Jv.listOf("[slot9]", "[s9]"), 8),
    /** Full player inventory snapshot (main 36 slots + 4 armor + offhand). */
    INV("[inv]", Jv.listOf("[inv]", "[inventory]"), -1),
    /** Player's ender chest snapshot (27 slots). */
    ENDER("[ender]", Jv.listOf("[ender]", "[enderchest]", "[ec]"), -1);

    private final String canonical;
    private final List<String> aliases;
    private final int hotbarIndex;

    TokenType(String canonical, List<String> aliases, int hotbarIndex) {
        this.canonical = canonical;
        this.aliases = aliases;
        this.hotbarIndex = hotbarIndex;
    }

    public String canonical() {
        return canonical;
    }

    public List<String> aliases() {
        return aliases;
    }

    /**
     * Returns the 0-based hotbar index ({@code 0..8}) for {@code [slot1]}..{@code [slot9]},
     * or {@code -1} for non-hotbar tokens.
     */
    public int hotbarIndex() {
        return hotbarIndex;
    }

    /**
     * Whether this token displays one or more item/equipment slots (controlled by {@code allow_item}).
     */
    public boolean isItemLike() {
        return this != INV && this != ENDER;
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
