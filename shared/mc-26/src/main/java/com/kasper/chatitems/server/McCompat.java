package com.kasper.chatitems.server;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Version-specific Minecraft calls for 26.x (where {@link HoverEvent.ShowItem} wraps an
 * {@link ItemStackTemplate} and {@link DataComponents} are used).
 */
public final class McCompat {

    private static final Method BUNDLE_ITEMS_METHOD = resolveBundleItemsMethod();

    private McCompat() {
    }

    private static Method resolveBundleItemsMethod() {
        for (String name : new String[]{"itemCopies", "itemCopyStream", "itemsCopy"}) {
            try {
                return BundleContents.class.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try next candidate across 26.1 vs 26.2+
            }
        }
        return null;
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static MutableComponent empty() {
        return Component.empty();
    }

    public static HoverEvent hoverItem(ItemStack stack) {
        return new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack.copy()));
    }

    public static HoverEvent hoverText(Component text) {
        return new HoverEvent.ShowText(text);
    }

    public static ClickEvent clickRunCommand(String command) {
        return new ClickEvent.RunCommand(command);
    }

    public static ClickEvent clickSuggestCommand(String command) {
        return new ClickEvent.SuggestCommand(command);
    }

    public static ChatFormatting rarityColor(ItemStack stack) {
        ChatFormatting color = stack.getRarity().color();
        return color != null ? color : ChatFormatting.WHITE;
    }

    public static ItemStack separatorPane() {
        Item paneItem = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("gray_stained_glass_pane"));
        if (paneItem == null || paneItem == Items.AIR) {
            paneItem = Items.GLASS_PANE;
        }
        ItemStack pane = new ItemStack(paneItem);
        pane.set(DataComponents.CUSTOM_NAME, Component.literal("").withStyle(style -> style.withItalic(false)));
        return pane;
    }

    public static ItemStack backButton(String label) {
        return backButton(label, null);
    }

    public static ItemStack backButton(String label, String hint) {
        ItemStack arrow = new ItemStack(Items.ARROW);
        arrow.set(DataComponents.CUSTOM_NAME,
                Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false)));
        if (hint != null && !hint.isEmpty()) {
            arrow.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal(hint).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)))));
        }
        return arrow;
    }

    public static ItemStack withInspectHint(ItemStack stack, String hint) {
        if (stack == null || stack.isEmpty() || hint == null || hint.isEmpty()) {
            return stack;
        }
        ItemStack copy = stack.copy();
        ItemLore lore = copy.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        if (lore.lines().size() < ItemLore.MAX_LINES) {
            Component line = Component.literal(hint)
                    .withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false));
            copy.set(DataComponents.LORE, lore.withLineAdded(line));
        }
        return copy;
    }

    public static boolean isSingleplayerHost(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null
                && server.isSingleplayer()
                && server.isSingleplayerOwner(player.nameAndId());
    }

    public static List<ItemStack> shulkerItems(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof BlockItem
                && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                contents.copyInto(items);
            }
            return items;
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null || stack.getItem() instanceof BundleItem) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            if (bundle != null && BUNDLE_ITEMS_METHOD != null) {
                try {
                    Object raw = BUNDLE_ITEMS_METHOD.invoke(bundle);
                    Iterator<?> it = null;
                    if (raw instanceof Stream<?>) {
                        it = ((Stream<?>) raw).iterator();
                    } else if (raw instanceof Iterable<?>) {
                        it = ((Iterable<?>) raw).iterator();
                    }
                    int i = 0;
                    while (it != null && it.hasNext() && i < 27) {
                        Object entry = it.next();
                        if (entry instanceof ItemStack) {
                            items.set(i++, ((ItemStack) entry).copy());
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // leave empty slots on unexpected bundle signature
                }
            }
            return items;
        }
        return null;
    }

    public static void runOnServer(ServerPlayer player, Runnable task) {
        MinecraftServer server = player.createCommandSourceStack().getServer();
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
