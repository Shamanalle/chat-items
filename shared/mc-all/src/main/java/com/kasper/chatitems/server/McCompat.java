package com.kasper.chatitems.server;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/**
 * Version-specific Minecraft calls for 1.21.5 – 26.3 (where {@link HoverEvent} and {@link ClickEvent}
 * are sealed interfaces with record implementations and items use {@link DataComponents}).
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
        return new HoverEvent.ShowItem(stack.copy());
    }

    public static HoverEvent hoverText(Component text) {
        return new HoverEvent.ShowText(text);
    }

    public static ClickEvent clickRunCommand(String command) {
        return new ClickEvent.RunCommand(command);
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

    public static ItemStack backButton(String label) {
        ItemStack arrow = new ItemStack(Items.ARROW);
        arrow.set(DataComponents.CUSTOM_NAME, Component.literal(label).withStyle(ChatFormatting.YELLOW));
        return arrow;
    }

    public static List<ItemStack> shulkerItems(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)
                || !(((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock)) {
            return null;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.copyInto(items);
        }
        return items;
    }

    public static void runOnServer(ServerPlayer player, Runnable task) {
        MinecraftServer server = ((Entity) player).level().getServer();
        if (server != null) {
            server.execute(task);
        }
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
