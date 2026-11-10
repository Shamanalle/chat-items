package com.kasper.chatitems;

import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.McCompat;
import com.kasper.chatitems.server.PlayerLanguage;
import com.kasper.chatitems.server.Slf4jLog;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric / Quilt entrypoint (Minecraft 1.16.5 – 1.18.2, before {@code ServerMessageDecoratorEvent}
 * and command API v2): registers {@code /chatitems}, captures client locale packets, and decorates
 * chat packets containing tokens while preserving vanilla chat validation and spam limits.
 */
public class ChatItemsFabric implements ModInitializer {

    private static final String HANDLER_NAME = "chatitems_chat";
    private static final int MAX_CHAT_LENGTH = 256;
    private static final Map<UUID, long[]> SPAM_TRACKER = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ModLog.use(new Slf4jLog());
        ChatItems.init(
                FabricLoader.getInstance().getConfigDir().resolve(ServerSettings.FILE_NAME),
                "Fabric");
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, dedicated) -> ChatItemsCommand.register(dispatcher));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                UUID id = handler.player.getUUID();
                PlayerLanguage.remove(id);
                SPAM_TRACKER.remove(id);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Channel channel = channelOf(handler.connection);
            if (channel == null || channel.pipeline().get(HANDLER_NAME) != null
                    || channel.pipeline().get("packet_handler") == null) {
                return;
            }
            UUID playerId = handler.player != null ? handler.player.getUUID() : null;
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (playerId != null) {
                        PlayerLanguage.capturePacket(playerId, msg);
                    }
                    if (msg instanceof ServerboundChatPacket) {
                        String raw = ((ServerboundChatPacket) msg).getMessage();
                        if (raw != null && !raw.startsWith("/") && raw.length() <= MAX_CHAT_LENGTH
                                && !hasIllegalChatChar(raw)) {
                            ServerSettings settings = ChatItems.settings();
                            settings.reloadIfChanged();
                            if (ChatTokens.parse(raw, settings).hasTokens()) {
                                server.execute(() -> {
                                    ServerPlayer player = handler.player;
                                    if (player == null) {
                                        return;
                                    }
                                    if (player.getChatVisibility() != ChatVisiblity.FULL) {
                                        McCompat.sendSystemMessage(player,
                                                new TranslatableComponent("chat.cannotSend").withStyle(ChatFormatting.RED));
                                        return;
                                    }
                                    player.resetLastActionTime();
                                    Component plain = McCompat.literal(raw);
                                    Component decorated = ChatItemsServer.decorateChat(player, raw, plain);
                                    if (decorated != plain) {
                                        McCompat.broadcastChat(player, decorated);
                                    }
                                    if (exceedsChatSpam(player.getUUID())
                                            && !server.getPlayerList().isOp(player.getGameProfile())
                                            && !server.isSingleplayerOwner(player.getGameProfile())) {
                                        handler.disconnect(new TranslatableComponent("disconnect.spam"));
                                    }
                                });
                                return;
                            }
                        }
                    }
                    super.channelRead(ctx, msg);
                }
            });
        });
    }

    private static boolean hasIllegalChatChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!SharedConstants.isAllowedChatCharacter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean exceedsChatSpam(UUID playerId) {
        long now = System.currentTimeMillis();
        long[] state = SPAM_TRACKER.computeIfAbsent(playerId, k -> new long[]{now, 0L});
        synchronized (state) {
            long elapsedTicks = Math.max(0L, (now - state[0]) / 50L);
            state[0] = now;
            state[1] = Math.max(0L, state[1] - elapsedTicks) + 20L;
            return state[1] > 200L;
        }
    }

    private static Channel channelOf(Connection connection) {
        if (connection == null) {
            return null;
        }
        try {
            for (Field field : Connection.class.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (Channel) field.get(connection);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ModLog.failure("Locating Netty channel on Connection", e);
        }
        return null;
    }
}
