package com.kasper.chatitems;

import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.PlayerLanguage;
import com.kasper.chatitems.server.Slf4jLog;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fabric / Quilt entrypoint (Minecraft 1.19 – 1.20.2, where {@code ChatDecorator.decorate} returns
 * a {@link CompletableFuture}): registers the chat decorator, client locale observer, and {@code /chatitems}.
 */
public class ChatItemsFabric implements ModInitializer {

    private static final String LANG_HANDLER_NAME = "chatitems_lang";

    @Override
    public void onInitialize() {
        ModLog.use(new Slf4jLog());
        ChatItems.init(
                FabricLoader.getInstance().getConfigDir().resolve(ServerSettings.FILE_NAME),
                "Fabric");
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> ChatItemsCommand.register(dispatcher));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                PlayerLanguage.remove(handler.player.getUUID());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player == null || PlayerLanguage.hasNativeMethod(handler.player)) {
                return;
            }
            Channel channel = channelOf(handler);
            if (channel == null || channel.pipeline().get(LANG_HANDLER_NAME) != null
                    || channel.pipeline().get("packet_handler") == null) {
                return;
            }
            UUID playerId = handler.player.getUUID();
            channel.pipeline().addBefore("packet_handler", LANG_HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    PlayerLanguage.capturePacket(playerId, msg);
                    super.channelRead(ctx, msg);
                }
            });
        });
        ServerMessageDecoratorEvent.EVENT.register(
                ServerMessageDecoratorEvent.CONTENT_PHASE,
                (sender, message) -> CompletableFuture.completedFuture(
                        ChatItemsServer.decorateChat(sender, message.getString(), message)));
    }

    private static Channel channelOf(Object packetListener) {
        try {
            Connection connection = null;
            for (Class<?> cls = packetListener.getClass(); cls != null && cls != Object.class && connection == null; cls = cls.getSuperclass()) {
                for (Field field : cls.getDeclaredFields()) {
                    if (Connection.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        connection = (Connection) field.get(packetListener);
                        break;
                    }
                }
            }
            if (connection == null) {
                return null;
            }
            for (Field field : Connection.class.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (Channel) field.get(connection);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ModLog.failure("Locating Netty channel for language tracking", e);
        }
        return null;
    }
}
