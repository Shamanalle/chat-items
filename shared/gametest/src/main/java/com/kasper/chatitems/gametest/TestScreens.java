package com.kasper.chatitems.gametest;

import com.kasper.chatitems.InventorySnapshot;
import com.kasper.chatitems.SnapshotType;
import com.kasper.chatitems.server.ChatItemsServer;
import com.kasper.chatitems.server.ReadOnlyChestMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Client and integrated-server helpers for the in-game test across 1.21.11 and 26.x. */
final class TestScreens {

    private static volatile CompletableFuture<?> reload;

    private TestScreens() {
    }

    /** Switches the game's language; the resources reload after it. */
    static void language(Minecraft client, String code) {
        client.options.languageCode = code;
        client.getLanguageManager().setSelected(code);
        reload = client.reloadResourcePacks();
    }

    static void guiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        for (String method : new String[]{"resizeDisplay", "resizeGui"}) {
            try {
                client.getClass().getMethod(method).invoke(client);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Try the other method name across 1.21.11 vs 26.x
            }
        }
    }

    /** A world where the player may use commands (cheats on), so /chatitems works. */
    static void allowCommands(WorldCreationUiState settings) {
        settings.setAllowCommands(true);
    }

    /** Sends a command as if typed in chat, without the slash. */
    static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
    }

    /** Sends a regular chat message as a player. */
    static void chat(Minecraft client, String message) {
        client.player.connection.sendChat(message);
    }

    static void closeContainer(Minecraft client) {
        if (client.player != null) {
            client.player.closeContainer();
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.closeContainer();
                }
            });
        }
    }

    /** Sends the client's options (its language) to the server. */
    static void sendOptions(Minecraft client) {
        client.options.broadcastOptions();
    }

    /**
     * Empties the chat so each screenshot shows one group of replies. Compatible with both 1.21.x
     * (where ChatComponent lives on Gui) and 26.x.
     */
    static void clearChat(Minecraft client) {
        for (Object owner : new Object[]{client.gui, client}) {
            for (Method getter : owner.getClass().getMethods()) {
                if (getter.getParameterCount() != 0 || !getter.getReturnType().getSimpleName().contains("Chat")) {
                    continue;
                }
                try {
                    Object chat = getter.invoke(owner);
                    chat.getClass().getMethod("clearMessages", boolean.class).invoke(chat, false);
                    return;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Not the chat component; try the next getter
                }
            }
        }
    }

    /** Whether the game has finished loading resources. */
    static boolean loaded(Minecraft client) {
        return reload == null || reload.isDone();
    }

    /**
     * Builds a stone-brick and bookshelf showcase room around (0, 120, 0), places an ender chest,
     * enchanting table, and lanterns in front of the player, and populates the player's inventory,
     * armor, offhand, and ender chest with recognizable items.
     */
    static void setupShowcaseScene(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            int baseY = 120;
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    for (int dy = 1; dy <= 6; dy++) {
                        level.setBlockAndUpdate(new BlockPos(dx, baseY + dy, dz), Blocks.AIR.defaultBlockState());
                    }
                    level.setBlockAndUpdate(new BlockPos(dx, baseY, dz), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            for (int dx = -4; dx <= 4; dx++) {
                level.setBlockAndUpdate(new BlockPos(dx, baseY + 1, 5), Blocks.BOOKSHELF.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(dx, baseY + 2, 5), Blocks.BOOKSHELF.defaultBlockState());
            }
            level.setBlockAndUpdate(new BlockPos(-2, baseY + 1, 3), Blocks.ENCHANTING_TABLE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(0, baseY + 1, 3), Blocks.ENDER_CHEST.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(2, baseY + 1, 3), Blocks.ANVIL.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(-3, baseY + 1, 1), Blocks.LANTERN.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(3, baseY + 1, 1), Blocks.LANTERN.defaultBlockState());

            for (ServerPlayer player : level.players()) {
                player.teleportTo(level, 0.5, baseY + 1.0, -2.5, Collections.emptySet(), 0.0f, 12.0f, false);
                populatePlayerItems(player);
            }
        });
    }

    private static void populatePlayerItems(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();

        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Dragonbane"));
        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
        player.getInventory().setItem(2, new ItemStack(Items.BOW));
        player.getInventory().setItem(3, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 8));
        player.getInventory().setItem(4, new ItemStack(Items.ENDER_PEARL, 16));
        player.getInventory().setItem(5, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.getInventory().setItem(6, new ItemStack(Items.GOLDEN_CARROT, 64));
        player.getInventory().setItem(7, new ItemStack(Items.FIREWORK_ROCKET, 64));
        player.getInventory().setItem(8, new ItemStack(Items.SPYGLASS));

        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 32));
        player.getInventory().setItem(10, new ItemStack(Items.EMERALD, 48));
        player.getInventory().setItem(11, new ItemStack(Items.NETHERITE_INGOT, 4));
        player.getInventory().setItem(12, new ItemStack(Items.NETHER_STAR, 1));
        player.getInventory().setItem(13, new ItemStack(Items.AMETHYST_SHARD, 16));
        player.getInventory().setItem(14, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        player.getInventory().setItem(15, new ItemStack(Items.ARROW, 64));
        player.getInventory().setItem(16, new ItemStack(Items.LAPIS_LAZULI, 64));
        player.getInventory().setItem(17, new ItemStack(Items.GOLD_INGOT, 32));

        // Armor slots (36 = boots, 37 = leggings, 38 = chestplate, 39 = helmet) and offhand (40)
        player.getInventory().setItem(36, new ItemStack(Items.NETHERITE_BOOTS));
        player.getInventory().setItem(37, new ItemStack(Items.NETHERITE_LEGGINGS));
        player.getInventory().setItem(38, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.getInventory().setItem(39, new ItemStack(Items.NETHERITE_HELMET));
        player.getInventory().setItem(40, new ItemStack(Items.SHIELD));

        // Ender chest items
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.ELYTRA));
        player.getEnderChestInventory().setItem(1, new ItemStack(Items.DRAGON_EGG));
        player.getEnderChestInventory().setItem(2, new ItemStack(Items.NETHER_STAR, 3));
        player.getEnderChestInventory().setItem(3, new ItemStack(Items.BEACON));
        player.getEnderChestInventory().setItem(4, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 16));
        player.getEnderChestInventory().setItem(5, new ItemStack(Items.NETHERITE_INGOT, 12));
        player.getEnderChestInventory().setItem(6, new ItemStack(Items.DIAMOND_BLOCK, 8));
        player.getEnderChestInventory().setItem(7, new ItemStack(Items.EMERALD_BLOCK, 16));
        player.getEnderChestInventory().setItem(8, new ItemStack(Items.TRIDENT));
        player.getEnderChestInventory().setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));

        player.containerMenu.broadcastChanges();
    }

    static String latestSnapshotId(SnapshotType type) {
        InventorySnapshot<ItemStack> s = ChatItemsServer.cache().latestOfType(type, System.currentTimeMillis());
        if (s == null) {
            throw new AssertionError("No active snapshot of type " + type);
        }
        return s.id();
    }

    static void verifyChatAndSnapshots(List<String> received) {
        long now = System.currentTimeMillis();
        int active = ChatItemsServer.cache().activeCount(now);
        if (active < 2) {
            throw new AssertionError("Expected at least 2 active snapshots in ChatItemsServer.cache(), got " + active);
        }
        InventorySnapshot<ItemStack> inv = ChatItemsServer.cache().latestOfType(SnapshotType.INV, now);
        InventorySnapshot<ItemStack> ender = ChatItemsServer.cache().latestOfType(SnapshotType.ENDER, now);
        if (inv == null || inv.type() != SnapshotType.INV) {
            throw new AssertionError("Expected INV snapshot in cache, got " + inv);
        }
        if (ender == null || ender.type() != SnapshotType.ENDER) {
            throw new AssertionError("Expected ENDER snapshot in cache, got " + ender);
        }
        String joined = String.join("\n", received);
        if (!joined.contains("[Dragonbane]")) {
            throw new AssertionError("Expected formatted [Dragonbane] item token in chat, got: " + received);
        }
        if (!joined.contains("Inventory]") && !joined.contains("Инвентарь")) {
            throw new AssertionError("Expected formatted [inv] snapshot tag in chat, got: " + received);
        }
        if (!joined.contains("Ender Chest]") && !joined.contains("Эндер-сундук")) {
            throw new AssertionError("Expected formatted [ender] snapshot tag in chat, got: " + received);
        }
    }

    static void verifyInventorySnapshotMenu(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            throw new AssertionError("Singleplayer server is null");
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof ReadOnlyChestMenu)) {
            throw new AssertionError("Expected ReadOnlyChestMenu for /chatitems view 1, got " + menu.getClass().getName());
        }
        // Chest slot 0 holds main inventory slot 9 (32 diamonds); chest slot 27 holds hotbar slot 0 (Dragonbane sword)
        if (!menu.getSlot(0).getItem().is(Items.DIAMOND)) {
            throw new AssertionError("Expected DIAMOND in snapshot chest slot 0, got " + menu.getSlot(0).getItem());
        }
        if (!menu.getSlot(27).getItem().is(Items.NETHERITE_SWORD)) {
            throw new AssertionError("Expected NETHERITE_SWORD in snapshot chest slot 27 (hotbar 0), got " + menu.getSlot(27).getItem());
        }
        // Armor and offhand slots
        if (!menu.getSlot(InventorySnapshot.CHEST_SLOT_HELMET).getItem().is(Items.NETHERITE_HELMET)) {
            throw new AssertionError("Expected NETHERITE_HELMET in snapshot slot " + InventorySnapshot.CHEST_SLOT_HELMET);
        }
        if (!menu.getSlot(InventorySnapshot.CHEST_SLOT_OFFHAND).getItem().is(Items.SHIELD)) {
            throw new AssertionError("Expected SHIELD in snapshot slot " + InventorySnapshot.CHEST_SLOT_OFFHAND);
        }
        // Verify read-only protection: shift-clicking (quickMoveStack) must return EMPTY and leave the slot intact
        ItemStack moved = menu.quickMoveStack(player, 27);
        if (!moved.isEmpty() || !menu.getSlot(27).getItem().is(Items.NETHERITE_SWORD)) {
            throw new AssertionError("ReadOnlyChestMenu allowed quickMoveStack to take an item!");
        }
    }

    static void verifyEnderSnapshotMenu(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            throw new AssertionError("Singleplayer server is null");
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof ReadOnlyChestMenu)) {
            throw new AssertionError("Expected ReadOnlyChestMenu for /chatitems view 2, got " + menu.getClass().getName());
        }
        if (!menu.getSlot(0).getItem().is(Items.ELYTRA)) {
            throw new AssertionError("Expected ELYTRA in ender chest snapshot slot 0, got " + menu.getSlot(0).getItem());
        }
        if (!menu.getSlot(1).getItem().is(Items.DRAGON_EGG)) {
            throw new AssertionError("Expected DRAGON_EGG in ender chest snapshot slot 1, got " + menu.getSlot(1).getItem());
        }
        ItemStack moved = menu.quickMoveStack(player, 0);
        if (!moved.isEmpty() || !menu.getSlot(0).getItem().is(Items.ELYTRA)) {
            throw new AssertionError("ReadOnlyChestMenu allowed quickMoveStack to take an item from ender snapshot!");
        }
    }
}
