package com.kasper.chatitems.server;

import com.kasper.chatitems.ChatItems;
import com.kasper.chatitems.ModLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's log on the mod loaders, which ship SLF4J with Minecraft (1.17+). */
public final class Slf4jLog implements ModLog.Sink {

    private final Logger log = LoggerFactory.getLogger(ChatItems.MOD_NAME);

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        if (error == null) {
            log.warn(message);
        } else {
            log.warn(message, error);
        }
    }
}
