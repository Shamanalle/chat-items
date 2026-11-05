package com.kasper.chatitems.gametest;

import com.kasper.chatitems.SnapshotType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Plays singleplayer with Chat Items: the integrated server runs the mod's chat decorator, snapshot
 * cache, and read-only container menus. Sends chat messages containing {@code [item]}, {@code [inv]},
 * and {@code [ender]}, opens the read-only inventory and ender-chest snapshot menus via
 * {@code /chatitems view <id>}, verifies that items cannot be taken from the snapshots, captures 4K
 * {@code store-*} screenshots, and exercises {@code /chatitems} in English and Russian.
 */
public class CommandsGameTest implements FabricClientGameTest {

    private static final String[] LANGUAGES = {"en_us", "ru_ru"};
    private static final int STORE_WIDTH = 3840;
    private static final int STORE_HEIGHT = 2160;
    private static final int STORE_GUI_SCALE = 5;

    /** Chat lines as the client received them. */
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    private static final String[][] GROUPS = {
            {"status", "chatitems status"},
            {"help", "chatitems help"},
            {"changes", "chatitems cooldown 5", "chatitems max_tokens 4", "chatitems ttl 180", "chatitems item off", "chatitems inv off", "chatitems ender off", "chatitems relaod"},
            {"undo", "chatitems cooldown 3", "chatitems max_tokens 3", "chatitems ttl 300", "chatitems item on", "chatitems inv on", "chatitems ender on", "chatitems reload"},
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                RECEIVED.add(message.getString());
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                RECEIVED.add(message.getString()));

        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(TestScreens::allowCommands).create()) {
            context.waitTicks(40);
            context.runOnClient(client -> TestScreens.command(client, "gamemode survival"));
            context.waitTicks(5);

            // Build the showcase hall and populate the player's inventory, armor, offhand, and ender chest
            context.runOnClient(TestScreens::setupShowcaseScene);
            context.waitTicks(20);
            context.runOnClient(client -> TestScreens.guiScale(client, STORE_GUI_SCALE));
            context.waitTicks(5);

            // Disable cooldown temporarily so two showcase chat messages can be sent back-to-back
            context.runOnClient(client -> TestScreens.command(client, "chatitems cooldown 0"));
            context.waitTicks(10);
            context.runOnClient(TestScreens::clearChat);
            RECEIVED.clear();

            // Send chat messages with [item], [inv], and [ender]
            context.runOnClient(client -> TestScreens.chat(client, "Ready for the End with [item]!"));
            context.waitTicks(15);
            context.runOnClient(client -> TestScreens.chat(client, "Full gear in [inv] and spare loot in [ender]"));
            context.waitTicks(15);

            TestScreens.verifyChatAndSnapshots(RECEIVED);
            storeShot(context, "store-chat");

            // Open the read-only inventory snapshot, verify read-only protection, and capture 4K store-inv
            String invId = TestScreens.latestSnapshotId(SnapshotType.INV);
            context.runOnClient(client -> TestScreens.command(client, "chatitems view " + invId));
            context.waitTicks(15);
            context.runOnClient(TestScreens::verifyInventorySnapshotMenu);
            context.waitTicks(5);
            storeShot(context, "store-inv");
            context.runOnClient(TestScreens::closeContainer);
            context.waitTicks(10);

            // Open the read-only ender chest snapshot, verify read-only protection, and capture 4K store-ender
            String enderId = TestScreens.latestSnapshotId(SnapshotType.ENDER);
            context.runOnClient(client -> TestScreens.command(client, "chatitems view " + enderId));
            context.waitTicks(15);
            context.runOnClient(TestScreens::verifyEnderSnapshotMenu);
            context.waitTicks(5);
            storeShot(context, "store-ender");
            context.runOnClient(TestScreens::closeContainer);
            context.waitTicks(10);

            // Restore default cooldown (3s) and capture 4K store-status
            context.runOnClient(client -> TestScreens.command(client, "chatitems cooldown 3"));
            context.waitTicks(10);
            context.runOnClient(TestScreens::clearChat);
            context.runOnClient(client -> TestScreens.command(client, "chatitems status"));
            context.waitTicks(10);
            storeShot(context, "store-status");

            context.runOnClient(client -> TestScreens.guiScale(client, 0));
            context.waitTicks(5);

            for (String language : LANGUAGES) {
                context.runOnClient(client -> TestScreens.language(client, language));
                context.waitTicks(5);
                context.waitFor(TestScreens::loaded, 1200);
                // The server answers in the player's language: tell it the new one
                context.runOnClient(TestScreens::sendOptions);
                context.waitTicks(40);
                for (String[] group : GROUPS) {
                    context.runOnClient(TestScreens::clearChat);
                    RECEIVED.clear();
                    for (int i = 1; i < group.length; i++) {
                        String command = group[i];
                        context.runOnClient(client -> TestScreens.command(client, command));
                        context.waitTicks(10);
                    }
                    context.takeScreenshot("commands-" + group[0] + "-" + language);
                    check(group, language);
                }
            }
        }
    }

    private static void storeShot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(STORE_WIDTH, STORE_HEIGHT));
    }

    private static void check(String[] group, String language) {
        if (RECEIVED.isEmpty()) {
            throw new AssertionError("No reply to " + String.join(", ", group) + " (" + language + ")");
        }
        for (String line : RECEIVED) {
            if (line.contains("command.chatitems.") || line.contains("chatitems.tag.") || line.contains("chatitems.gui.")) {
                throw new AssertionError("Untranslated text in " + language + ": " + line);
            }
            if (line.contains("Unknown or incomplete command") || line.contains("Неизвестная или неполная команда")) {
                throw new AssertionError("/chatitems is not a command here (" + language + "): " + RECEIVED);
            }
        }
        String all = String.join("\n", RECEIVED);
        if (group[0].equals("status")) {
            String tokensLabel = language.equals("ru_ru") ? "Теги: " : "Tokens: ";
            if (!all.contains("Chat Items ") || !all.contains(tokensLabel)) {
                throw new AssertionError("No status in " + language + ": " + RECEIVED);
            }
            if (all.matches("(?s).*(Active snapshots in cache|Активных снимков в кэше): 0\\b.*")) {
                throw new AssertionError("Active snapshots were not recorded in status: " + RECEIVED);
            }
        }
    }
}
