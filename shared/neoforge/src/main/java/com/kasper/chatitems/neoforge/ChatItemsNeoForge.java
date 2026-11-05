package com.kasper.chatitems.neoforge;

import com.kasper.chatitems.ChatItems;
import com.kasper.chatitems.ModLog;
import com.kasper.chatitems.ServerSettings;
import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.Slf4jLog;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * NeoForge entrypoint: registers {@code /chatitems} and decorates chat messages on {@link ServerChatEvent}.
 */
@Mod(ChatItems.MOD_ID)
public final class ChatItemsNeoForge {

    public ChatItemsNeoForge() {
        ModLog.use(new Slf4jLog());
        ChatItems.init(
                FMLPaths.CONFIGDIR.get().resolve(ServerSettings.FILE_NAME),
                "NeoForge");
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ChatItemsCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerChatEvent e) -> {
            Component current = e.getMessage();
            Component decorated = ChatItemsServer.decorateChat(e.getPlayer(), e.getRawText(), current);
            if (decorated != current) {
                e.setMessage(decorated);
            }
        });
    }
}
