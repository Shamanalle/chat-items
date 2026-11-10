package com.kasper.chatitems.server;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
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
 * Version-specific Minecraft calls for 1.19 – 1.19.4 (where {@link CommandSourceStack#sendSuccess}
 * takes a {@link Component} directly rather than a supplier).
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

    public static ClickEvent clickSuggestCommand(String command) {
        return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
    }

    public static ChatFormatting rarityColor(ItemStack stack) {
        ChatFormatting color = stack.getRarity().color;
        return color != null ? color : ChatFormatting.WHITE;
    }

    public static ItemStack separatorPane() {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.setHoverName(Component.literal("").withStyle(style -> style.withItalic(false)));
        return pane;
    }

    public static ItemStack backButton(String label) {
        return backButton(label, null);
    }

    public static ItemStack backButton(String label, String hint) {
        ItemStack arrow = new ItemStack(Items.ARROW);
        arrow.setHoverName(Component.literal(label)
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false)));
        if (hint != null && !hint.isEmpty()) {
            appendNbtLoreLine(arrow, hint, "gray");
        }
        return arrow;
    }

    public static ItemStack withInspectHint(ItemStack stack, String hint) {
        if (stack == null || stack.isEmpty() || hint == null || hint.isEmpty()) {
            return stack;
        }
        ItemStack copy = stack.copy();
        appendNbtLoreLine(copy, hint, "gold");
        return copy;
    }

    private static void appendNbtLoreLine(ItemStack stack, String text, String color) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.getList("Lore", 8);
        if (lore.size() < 256) {
            lore.add(StringTag.valueOf(loreJson(text, color)));
            display.put("Lore", lore);
        }
    }

    private static String loreJson(String text, String color) {
        StringBuilder b = new StringBuilder(text.length() + 40);
        b.append("{\"text\":\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '"') {
                b.append('\\').append(c);
            } else if (c >= 0x20) {
                b.append(c);
            }
        }
        b.append("\",\"color\":\"").append(color).append("\",\"italic\":false}");
        return b.toString();
    }

    public static boolean isSingleplayerHost(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null
                && server.isSingleplayer()
                && server.isSingleplayerOwner(player.getGameProfile());
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
        player.sendSystemMessage(message);
    }

    public static void sendCommandLine(CommandSourceStack source, Component line) {
        source.sendSuccess(line, false);
    }

    public static ServerPlayer player(CommandSourceStack source) {
        return source.getPlayer();
    }
}
