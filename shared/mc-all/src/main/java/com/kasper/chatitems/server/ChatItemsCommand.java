package com.kasper.chatitems.server;

import com.kasper.chatitems.AdminCommands;
import com.kasper.chatitems.Jv;
import com.kasper.chatitems.Reply;
import com.kasper.chatitems.ServerText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /chatitems} on a Fabric, Forge or NeoForge server. Any player can run
 * {@code /chatitems view <id>} (triggered by clicking {@code [inv]} or {@code [ender]} in chat),
 * {@code /chatitems status}, or {@code /chatitems help}; changing settings requires admin permission.
 */
public final class ChatItemsCommand {

    private ChatItemsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(AdminCommands.NAME)
                .executes(ctx -> run(ctx, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(ChatItemsCommand::suggest)
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "args")))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String args) {
        CommandSourceStack source = ctx.getSource();
        String trimmed = args == null ? "" : args.trim();
        String[] parts = Jv.isBlank(trimmed) ? new String[0] : trimmed.split("\\s+");
        if (parts.length >= 2 && parts[0].equalsIgnoreCase("view")) {
            ServerPlayer player = McCompat.player(source);
            if (player == null) {
                return 0;
            }
            return ChatItemsServer.openSnapshot(player, parts[1]) ? 1 : 0;
        }
        String sub = parts.length == 0 ? "status" : parts[0].toLowerCase(Locale.ROOT);
        boolean readOnlySub = sub.equals("status") || sub.equals("help");
        if (!readOnlySub && !AdminPermission.isAdmin(source)) {
            String lang = ChatItemsServer.languageOf(McCompat.player(source));
            Component denied = McCompat.literal(ServerText.translate(lang, "chatitems.permission.denied"))
                    .withStyle(ChatFormatting.RED);
            McCompat.sendCommandLine(source, denied);
            return 0;
        }
        Reply reply = AdminCommands.execute(trimmed, context(source));
        for (Reply.Line line : reply.lines()) {
            Component text = McCompat.literal(line.text()).withStyle(color(line.kind()));
            McCompat.sendCommandLine(source, text);
        }
        return reply.refused() ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        for (String s : AdminCommands.suggestions(typed)) {
            word.suggest(s);
        }
        return word.buildFuture();
    }

    private static AdminCommands.Context context(CommandSourceStack source) {
        String language = ChatItemsServer.languageOf(McCompat.player(source));
        int active = ChatItemsServer.cache().activeCount(System.currentTimeMillis());
        return new AdminCommands.Context() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public int activeSnapshots() {
                return active;
            }
        };
    }

    private static ChatFormatting color(Reply.Kind kind) {
        switch (kind) {
            case TITLE:
                return ChatFormatting.GOLD;
            case DONE:
                return ChatFormatting.GREEN;
            case ERROR:
                return ChatFormatting.RED;
            case HINT:
                return ChatFormatting.GRAY;
            default:
                return ChatFormatting.WHITE;
        }
    }
}
