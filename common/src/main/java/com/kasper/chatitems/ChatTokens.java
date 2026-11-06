package com.kasper.chatitems;

import com.github.bsideup.jabel.Desugar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Parses chat messages for {@code [item]} (aliases {@code [i]}, {@code [hand]}),
 * {@code [offhand]} (alias {@code [off]}), {@code [armor]} (alias {@code [armour]}),
 * {@code [slot1]}..{@code [slot9]} (aliases {@code [s1]}..{@code [s9]}),
 * {@code [inv]} (alias {@code [inventory]}), and {@code [ender]} (aliases {@code [enderchest]}, {@code [ec]}).
 * <p>
 * Supports backslash escaping ({@code \[item]} becomes literal {@code [item]}), respects per-token
 * switches in {@link ServerSettings}, and enforces the per-message {@code max_tokens} limit.
 */
public final class ChatTokens {

    private ChatTokens() {
    }

    /** One segment of a parsed chat message: either literal text or a matched placeholder token. */
    @Desugar
    public record Segment(String text, TokenType tokenType) {

        public static Segment text(String text) {
            return new Segment(text, null);
        }

        public static Segment token(TokenType type, String rawText) {
            return new Segment(rawText, type);
        }

        public boolean isToken() {
            return tokenType != null;
        }
    }

    /** Result of parsing a chat message. */
    @Desugar
    public record ParseResult(List<Segment> segments, int tokenCount, boolean limitReached) {

        public boolean hasTokens() {
            return tokenCount > 0;
        }

        public boolean hasSnapshotTokens() {
            for (Segment seg : segments) {
                if (seg.tokenType() == TokenType.INV || seg.tokenType() == TokenType.ENDER) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Parses {@code message} according to {@code settings}.
     */
    public static ParseResult parse(String message, ServerSettings settings) {
        return parse(message, settings, null);
    }

    /**
     * Parses {@code message} according to {@code settings} and an optional per-token permission predicate.
     */
    public static ParseResult parse(String message, ServerSettings settings, Predicate<TokenType> permissionCheck) {
        if (message == null || message.isEmpty()) {
            return new ParseResult(Collections.emptyList(), 0, false);
        }
        if (settings != null && !settings.enabled()) {
            return new ParseResult(Jv.listOf(Segment.text(message)), 0, false);
        }
        int maxTokens = settings == null ? ChatItems.DEFAULT_MAX_TOKENS : settings.maxTokens();
        List<Segment> segments = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int tokenCount = 0;
        boolean limitReached = false;

        int i = 0;
        int len = message.length();
        while (i < len) {
            char c = message.charAt(i);
            // Escaped token: \[item] -> literal [item]
            if (c == '\\' && i + 1 < len && message.charAt(i + 1) == '[') {
                int close = message.indexOf(']', i + 2);
                if (close > i + 2) {
                    String candidate = message.substring(i + 1, close + 1);
                    if (TokenType.fromBracketed(candidate) != null) {
                        plain.append(candidate);
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (c == '[') {
                int close = message.indexOf(']', i + 1);
                if (close > i + 1) {
                    String candidate = message.substring(i, close + 1);
                    TokenType type = TokenType.fromBracketed(candidate);
                    if (type != null && isAllowed(type, settings) && (permissionCheck == null || permissionCheck.test(type))) {
                        if (tokenCount < maxTokens) {
                            if (plain.length() > 0) {
                                segments.add(Segment.text(plain.toString()));
                                plain.setLength(0);
                            }
                            segments.add(Segment.token(type, candidate));
                            tokenCount++;
                        } else {
                            limitReached = true;
                            plain.append(candidate);
                        }
                        i = close + 1;
                        continue;
                    }
                }
            }
            plain.append(c);
            i++;
        }
        if (plain.length() > 0) {
            segments.add(Segment.text(plain.toString()));
        }
        return new ParseResult(Jv.copyOf(segments), tokenCount, limitReached);
    }

    private static boolean isAllowed(TokenType type, ServerSettings settings) {
        if (settings == null) {
            return true;
        }
        if (type.isItemLike()) {
            return settings.allowItem();
        }
        switch (type) {
            case INV:
                return settings.allowInv();
            case ENDER:
                return settings.allowEnder();
            default:
                return false;
        }
    }
}
