package com.kasper.chatitems.server;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Version-specific Minecraft calls for 1.20.5 – 1.21.4 (where items use {@link DataComponents}, and
 * {@link HoverEvent} / {@link ClickEvent} use action constructors).
 */
public final class McCompat {

    private McCompat() {
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static MutableComponent empty() {
        return Component.empty();
    }

    public static HoverEvent hoverItem(ItemStack stack) {
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack.copy()));
    }

    public static HoverEvent hoverText(Component text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
    }

    public static ClickEvent clickRunCommand(String command) {
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
    }

    public static ChatFormatting rarityColor(ItemStack stack) {
        ChatFormatting color = stack.getRarity().color();
        return color != null ? color : ChatFormatting.WHITE;
    }

    public static ItemStack separatorPane() {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return pane;
    }

    public static ItemStack getInventoryItem(ServerPlayer player, int slot) {
        return player.getInventory().getItem(slot);
    }

    public static void resyncMenu(Player player, AbstractContainerMenu menu) {
        menu.sendAllDataToRemote();
    }

    public static void sendSystemMessage(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }

    public static void sendCommandLine(CommandSourceStack source, Component line) {
        source.sendSuccess(() -> line, false);
    }

    public static ServerPlayer player(CommandSourceStack source) {
        return source.getPlayer();
    }
}
