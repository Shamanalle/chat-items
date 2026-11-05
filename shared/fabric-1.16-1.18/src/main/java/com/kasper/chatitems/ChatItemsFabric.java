package com.kasper.chatitems;

import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.McCompat;
import com.kasper.chatitems.server.Slf4jLog;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;

/**
 * Fabric / Quilt entrypoint (Minecraft 1.16.5 – 1.18.2, before {@code ServerMessageDecoratorEvent}
 * and command API v2): registers {@code /chatitems} and decorates chat packets containing tokens.
 */
public class ChatItemsFabric implements ModInitializer {

    private static final String HANDLER_NAME = "chatitems_chat";

    @Override
    public void onInitialize() {
        ModLog.use(new Slf4jLog());
        ChatItems.init(
                FabricLoader.getInstance().getConfigDir().resolve(ServerSettings.FILE_NAME),
                "Fabric");
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, dedicated) -> ChatItemsCommand.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Channel channel = channelOf(handler.connection);
            if (channel == null || channel.pipeline().get(HANDLER_NAME) != null
                    || channel.pipeline().get("packet_handler") == null) {
                return;
            }
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg instanceof ServerboundChatPacket) {
                        String raw = ((ServerboundChatPacket) msg).getMessage();
                        if (raw != null && !raw.startsWith("/")) {
                            ServerSettings settings = ChatItems.settings();
                            settings.reloadIfChanged();
                            if (ChatTokens.parse(raw, settings).hasTokens()) {
                                server.execute(() -> {
                                    ServerPlayer player = handler.player;
                                    if (player == null) {
                                        return;
                                    }
                                    Component plain = McCompat.literal(raw);
                                    Component decorated = ChatItemsServer.decorateChat(player, raw, plain);
                                    McCompat.broadcastChat(player, decorated);
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
