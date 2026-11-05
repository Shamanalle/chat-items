package com.kasper.chatitems.forge;

import com.kasper.chatitems.ChatItems;
import com.kasper.chatitems.ModLog;
import com.kasper.chatitems.ServerSettings;
import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.Slf4jLog;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entrypoint (Minecraft 1.21.6 and later, Forge's second event bus): registers {@code /chatitems}
 * and decorates chat messages on {@link ServerChatEvent}.
 */
@Mod(ChatItems.MOD_ID)
public final class ChatItemsForge {

    public ChatItemsForge(FMLJavaModLoadingContext context) {
        ModLog.use(new Slf4jLog());
        ChatItems.init(
                FMLPaths.CONFIGDIR.get().resolve(ServerSettings.FILE_NAME),
                "Forge");
        RegisterCommandsEvent.BUS.addListener(e -> ChatItemsCommand.register(e.getDispatcher()));
        ServerChatEvent.BUS.addListener(e -> {
            Component current = e.getMessage();
            Component decorated = ChatItemsServer.decorateChat(e.getPlayer(), e.getRawText(), current);
            if (decorated != current) {
                e.setMessage(decorated);
            }
        });
    }
}
