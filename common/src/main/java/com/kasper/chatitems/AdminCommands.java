package com.kasper.chatitems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /chatitems}, the same on every loader and on Bukkit: the platform passes the text after the
 * command and sends the {@link Reply} back.
 */
public final class AdminCommands {

    public static final String NAME = "chatitems";

    private static final List<String> SUBCOMMANDS = Jv.listOf(
            "status", "enabled", "item", "inv", "ender", "max_tokens", "cooldown", "ttl", "reload", "help");
    private static final List<String> PUBLIC_SUBCOMMANDS = Jv.listOf("status", "help");

    /** What the platform knows about who ran the command. */
    public interface Context {
        /** The language of the reply ("ru_ru"); empty for the settings' language. */
        String language();

        /** Number of currently active snapshots in the server's cache. */
        int activeSnapshots();
    }

    private AdminCommands() {
    }

    public static Reply execute(String args, Context ctx) {
        ServerSettings s = ChatItems.settings();
        s.reloadIfChanged();
        String lang = Jv.isBlank(ctx.language()) ? s.language() : ctx.language();
        String[] a = args == null || Jv.isBlank(args) ? new String[0] : args.trim().split("\\s+");
        try {
            String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "status":
                    return status(lang, s, ctx);
                case "help":
                    return help(lang);
                case "enabled":
                case "item":
                case "inv":
                case "ender":
                    return toggle(lang, s, sub, a);
                case "max_tokens":
                case "max-tokens":
                    return maxTokens(lang, s, a);
                case "cooldown":
                    return cooldown(lang, s, a);
                case "ttl":
                    return ttl(lang, s, a);
                case "reload":
                    s.load();
                    return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "reload.done", fileName(s)));
                default:
                    return unknown(lang, a[0]);
            }
        } catch (RuntimeException e) {
            ModLog.failure("/" + NAME + " " + args, e);
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "error"));
        }
    }

    private static Reply status(String lang, ServerSettings s, Context ctx) {
        Reply r = new Reply();
        r.add(Reply.Kind.TITLE, ServerText.get(lang, "status.title", BuildInfo.version(), ChatItems.platform()));
        r.add(Reply.Kind.INFO, ServerText.get(lang, s.enabled() ? "status.enabled.on" : "status.enabled.off"));
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.tokens",
                onOff(lang, s.allowItem()),
                onOff(lang, s.allowInv()),
                onOff(lang, s.allowEnder())));
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.limits",
                String.valueOf(s.maxTokens()),
                String.valueOf(s.cooldownSeconds()),
                String.valueOf(s.snapshotTtlSeconds())));
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.snapshots", String.valueOf(ctx.activeSnapshots())));
        r.add(Reply.Kind.HINT, ServerText.get(lang, "status.file", fileName(s)));
        return r;
    }

    private static Reply help(String lang) {
        Reply r = new Reply().add(Reply.Kind.TITLE, ServerText.get(lang, "help.title"));
        for (String sub : SUBCOMMANDS) {
            r.add(Reply.Kind.INFO, ServerText.get(lang, "help." + sub));
        }
        return r;
    }

    private static Reply toggle(String lang, ServerSettings s, String key, String[] a) {
        boolean current = getToggle(s, key);
        if (a.length < 2) {
            return new Reply()
                    .add(Reply.Kind.INFO, ServerText.get(lang, "toggle.show", key, onOff(lang, current)))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "help." + key));
        }
        Boolean parsed = parseBoolean(a[1]);
        if (parsed == null) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "toggle.usage", key));
        }
        setToggle(s, key, parsed);
        s.save();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "toggle.set", key, onOff(lang, parsed)));
    }

    private static boolean getToggle(ServerSettings s, String key) {
        switch (key) {
            case "enabled":
                return s.enabled();
            case "item":
                return s.allowItem();
            case "inv":
                return s.allowInv();
            case "ender":
                return s.allowEnder();
            default:
                return false;
        }
    }

    private static void setToggle(ServerSettings s, String key, boolean value) {
        switch (key) {
            case "enabled":
                s.setEnabled(value);
                break;
            case "item":
                s.setAllowItem(value);
                break;
            case "inv":
                s.setAllowInv(value);
                break;
            case "ender":
                s.setAllowEnder(value);
                break;
        }
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("on") || v.equals("true") || v.equals("yes") || v.equals("1")) {
            return Boolean.TRUE;
        }
        if (v.equals("off") || v.equals("false") || v.equals("no") || v.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Reply maxTokens(String lang, ServerSettings s, String[] a) {
        if (a.length < 2) {
            return new Reply()
                    .add(Reply.Kind.INFO, ServerText.get(lang, "max_tokens.show", String.valueOf(s.maxTokens())))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "help.max_tokens"));
        }
        Integer v = parseInt(a[1]);
        if (v == null || !ServerSettings.validMaxTokens(v)) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "number.bad", a[1],
                    String.valueOf(ChatItems.MIN_MAX_TOKENS), String.valueOf(ChatItems.MAX_MAX_TOKENS)));
        }
        s.setMaxTokens(v);
        s.save();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "max_tokens.set", String.valueOf(v)));
    }

    private static Reply cooldown(String lang, ServerSettings s, String[] a) {
        if (a.length < 2) {
            return new Reply()
                    .add(Reply.Kind.INFO, ServerText.get(lang, "cooldown.show", String.valueOf(s.cooldownSeconds())))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "help.cooldown"));
        }
        Integer v = parseInt(a[1]);
        if (v == null || !ServerSettings.validCooldown(v)) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "number.bad", a[1],
                    String.valueOf(ChatItems.MIN_COOLDOWN_SECONDS), String.valueOf(ChatItems.MAX_COOLDOWN_SECONDS)));
        }
        s.setCooldownSeconds(v);
        s.save();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "cooldown.set", String.valueOf(v)));
    }

    private static Reply ttl(String lang, ServerSettings s, String[] a) {
        if (a.length < 2) {
            return new Reply()
                    .add(Reply.Kind.INFO, ServerText.get(lang, "ttl.show", String.valueOf(s.snapshotTtlSeconds())))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "help.ttl"));
        }
        Integer v = parseInt(a[1]);
        if (v == null || !ServerSettings.validTtl(v)) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "number.bad", a[1],
                    String.valueOf(ChatItems.MIN_SNAPSHOT_TTL_SECONDS), String.valueOf(ChatItems.MAX_SNAPSHOT_TTL_SECONDS)));
        }
        s.setSnapshotTtlSeconds(v);
        s.save();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "ttl.set", String.valueOf(v)));
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String onOff(String lang, boolean on) {
        return ServerText.get(lang, on ? "state.on" : "state.off");
    }

    private static Reply unknown(String lang, String typed) {
        Reply r = new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "unknown", typed));
        String near = closest(typed.toLowerCase(Locale.ROOT), SUBCOMMANDS);
        if (near != null) {
            r.add(Reply.Kind.HINT, ServerText.get(lang, "unknown.suggest", near));
        } else {
            r.add(Reply.Kind.HINT, ServerText.get(lang, "unknown.help"));
        }
        return r;
    }

    /** Words to suggest for what has been typed so far after the command (for an admin). */
    public static List<String> suggestions(String typed) {
        return suggestions(typed, true);
    }

    /** Words to suggest for what has been typed so far, filtered to read-only commands when {@code !isAdmin}. */
    public static List<String> suggestions(String typed, boolean isAdmin) {
        String t = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        String[] words = t.split("\\s+", -1);
        if (words.length > 0 && words[0].isEmpty() && words.length > 1) {
            words = java.util.Arrays.copyOfRange(words, 1, words.length);
        }
        List<String> options;
        if (words.length <= 1) {
            options = isAdmin ? SUBCOMMANDS : PUBLIC_SUBCOMMANDS;
        } else if (!isAdmin) {
            options = Collections.emptyList();
        } else if (words.length == 2) {
            switch (words[0]) {
                case "enabled":
                case "item":
                case "inv":
                case "ender":
                    options = Jv.listOf("on", "off");
                    break;
                case "max_tokens":
                case "max-tokens":
                    options = Jv.listOf("1", "3", "5", "10", "16");
                    break;
                case "cooldown":
                    options = Jv.listOf("0", "3", "5", "10", "30");
                    break;
                case "ttl":
                    options = Jv.listOf("60", "300", "600", "1800");
                    break;
                default:
                    options = Collections.emptyList();
            }
        } else {
            options = Collections.emptyList();
        }
        String last = words.length == 0 ? "" : words[words.length - 1];
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }

    /** If a reply line mentions a {@code /chatitems <subcommand>} command, returns a click-to-suggest prefix, or null. */
    public static String suggestedCommand(String lineText) {
        if (lineText == null) {
            return null;
        }
        int slash = lineText.indexOf("/chatitems ");
        if (slash < 0) {
            return null;
        }
        String rest = lineText.substring(slash + "/chatitems ".length()).trim();
        int end = 0;
        while (end < rest.length()) {
            char c = rest.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                end++;
            } else {
                break;
            }
        }
        if (end == 0) {
            return "/chatitems ";
        }
        String sub = rest.substring(0, end).toLowerCase(Locale.ROOT);
        boolean takesArg = sub.equals("enabled") || sub.equals("item") || sub.equals("inv") || sub.equals("ender")
                || sub.equals("max_tokens") || sub.equals("max-tokens") || sub.equals("cooldown") || sub.equals("ttl");
        return "/chatitems " + sub + (takesArg ? " " : "");
    }

    private static String fileName(ServerSettings s) {
        if (s.file() == null) {
            return ServerSettings.FILE_NAME;
        }
        java.nio.file.Path file = s.file().toAbsolutePath().normalize();
        java.nio.file.Path here = java.nio.file.Paths.get("").toAbsolutePath().normalize();
        return file.startsWith(here) ? here.relativize(file).toString() : file.toString();
    }

    /** The word within two typos of {@code typed}, or null. */
    static String closest(String typed, List<String> words) {
        String best = null;
        int bestDistance = 3;
        for (String w : words) {
            int d = distance(typed, w);
            if (d < bestDistance) {
                best = w;
                bestDistance = d;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
