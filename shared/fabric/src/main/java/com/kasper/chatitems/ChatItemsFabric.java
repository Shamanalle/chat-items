package com.kasper.chatitems;

import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.Slf4jLog;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric / Quilt entrypoint (Minecraft 1.19.3 and later): registers the chat decorator and {@code /chatitems}.
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
        ServerMessageDecoratorEvent.EVENT.register(
                ServerMessageDecoratorEvent.CONTENT_PHASE,
                (sender, message) -> ChatItemsServer.decorateChat(sender, message.getString(), message));
    }
}
