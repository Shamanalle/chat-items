package com.kasper.chatitems.server;

import com.kasper.chatitems.ChatItems;
import com.kasper.chatitems.ModLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The mod's log on Minecraft 1.16.5 – 1.17.1, which ship Log4j 2 rather than SLF4J. */
public final class Slf4jLog implements ModLog.Sink {

    private final Logger log = LogManager.getLogger(ChatItems.MOD_NAME);

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
