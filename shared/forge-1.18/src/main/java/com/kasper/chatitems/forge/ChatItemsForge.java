package com.kasper.chatitems.forge;

import com.kasper.chatitems.ChatItems;
import com.kasper.chatitems.ModLog;
import com.kasper.chatitems.ServerSettings;
import com.kasper.chatitems.server.ChatItemsCommand;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.McCompat;
import com.kasper.chatitems.server.Slf4jLog;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entrypoint (Minecraft 1.17.1 – 1.18.2, where {@link ServerChatEvent} uses
 * {@code getMessage()} / {@code setComponent(...)}): registers {@code /chatitems} and decorates chat.
 */
@Mod(ChatItems.MOD_ID)
public final class ChatItemsForge {

    public ChatItemsForge() {
        ModLog.use(new Slf4jLog());
        ChatItems.init(
                FMLPaths.CONFIGDIR.get().resolve(ServerSettings.FILE_NAME),
                "Forge");
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ChatItemsCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((ServerChatEvent e) -> {
            String raw = e.getMessage();
            Component plain = McCompat.literal(raw == null ? "" : raw);
            Component decorated = ChatItemsServer.decorateChat(e.getPlayer(), raw, plain);
            if (decorated != plain && e.getPlayer() != null) {
                e.setComponent(new TranslatableComponent("chat.type.text", e.getPlayer().getDisplayName(), decorated));
            }
        });
    }
}
