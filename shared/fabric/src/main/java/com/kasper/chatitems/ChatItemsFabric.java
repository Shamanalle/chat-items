package com.kasper.chatitems;

import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.Slf4jLog;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric / Quilt entrypoint (Minecraft 1.20.3 and later): registers the chat decorator and {@code /chatitems}.
 */
public class ChatItemsFabric implements ModInitializer {

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
                ChatItemsServer.clearPendingCompatChat(handler.player.getUUID());
            }
        });
        ServerMessageDecoratorEvent.EVENT.register(
                ServerMessageDecoratorEvent.CONTENT_PHASE,
                (sender, message) -> ChatItemsServer.decorateFabricChat(sender, message.getString(), message));
        var compatPhase = ServerMessageDecoratorEvent.CONTENT_PHASE;
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(compatPhase, Event.DEFAULT_PHASE);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
                compatPhase,
                (message, sender, params) -> !ChatItemsServer.consumeAndDeliverCompatChat(sender));
    }
}
