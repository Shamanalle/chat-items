package com.kasper.chatitems.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/**
 * Version-specific Minecraft calls for 1.17.1 – 1.18.2 (before {@code Component.literal},
 * {@code sendSystemMessage}, and {@code CommandSourceStack.getPlayer}).
 */
public final class McCompat {

    private McCompat() {
    }

    public static MutableComponent literal(String text) {
        return new TextComponent(text);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
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
        ChatFormatting color = stack.getRarity().color;
        return color != null ? color : ChatFormatting.WHITE;
    }

    public static ItemStack separatorPane() {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.setHoverName(new TextComponent(""));
        return pane;
    }

    public static ItemStack backButton(String label) {
        ItemStack arrow = new ItemStack(Items.ARROW);
        arrow.setHoverName(new TextComponent(label).withStyle(ChatFormatting.YELLOW));
        return arrow;
    }

    public static List<ItemStack> shulkerItems(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof BlockItem
                && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            CompoundTag tag = stack.getTagElement("BlockEntityTag");
            if (tag != null && tag.contains("Items", 9)) {
                ContainerHelper.loadAllItems(tag, items);
            }
            return items;
        }
        if (stack.getItem() instanceof BundleItem) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("Items", 9)) {
                ListTag list = tag.getList("Items", 10);
                for (int i = 0; i < list.size() && i < 27; i++) {
                    items.set(i, ItemStack.of(list.getCompound(i)));
                }
            }
            return items;
        }
        return null;
    }

    public static void runOnServer(ServerPlayer player, Runnable task) {
        MinecraftServer server = player.getServer();
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
        player.sendMessage(message, Util.NIL_UUID);
    }

    public static void sendCommandLine(CommandSourceStack source, Component line) {
        source.sendSuccess(line, false);
    }

    public static ServerPlayer player(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    public static void broadcastChat(ServerPlayer sender, Component decoratedBody) {
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        Component chatLine = new TranslatableComponent("chat.type.text", sender.getDisplayName(), decoratedBody);
        server.getPlayerList().broadcastMessage(chatLine, ChatType.CHAT, sender.getUUID());
    }
}
