package com.kasper.chatitems.server;

import com.kasper.chatitems.ChatItems;
import com.kasper.chatitems.ChatTokens;
import com.kasper.chatitems.InventorySnapshot;
import com.kasper.chatitems.Jv;
import com.kasper.chatitems.ModLog;
import com.kasper.chatitems.ServerSettings;
import com.kasper.chatitems.ServerText;
import com.kasper.chatitems.SnapshotCache;
import com.kasper.chatitems.SnapshotType;
import com.kasper.chatitems.TokenType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared server-side chat decorator and read-only snapshot opener for Fabric, Forge, and NeoForge.
 */
public final class ChatItemsServer {

    private static final SnapshotCache<ItemStack> CACHE = new SnapshotCache<>();

    private ChatItemsServer() {
    }

    public static SnapshotCache<ItemStack> cache() {
        return CACHE;
    }

    /**
     * Replaces {@code [item]} (and aliases {@code [i]}, {@code [hand]}), {@code [inv]}, and {@code [ender]}
     * in {@code rawText} with rich hover/click components. Returns {@code original} unchanged if no active
     * tokens are present or if the player is on cooldown.
     */
    public static Component decorateChat(ServerPlayer sender, String rawText, Component original) {
        if (sender == null || rawText == null || rawText.isEmpty()) {
            return original;
        }
        try {
            ServerSettings settings = ChatItems.settings();
            settings.reloadIfChanged();
            ChatTokens.ParseResult parsed = ChatTokens.parse(rawText, settings);
            if (!parsed.hasTokens()) {
                return original;
            }
            long now = System.currentTimeMillis();
            String lang = languageOf(sender);
            if (!ChatItems.cooldowns().tryAcquire(sender.getUUID(), now, settings.cooldownSeconds())) {
                int waitSec = ChatItems.cooldowns().checkRemainingSeconds(
                        sender.getUUID(), now, settings.cooldownSeconds());
                MutableComponent notice = McCompat.literal(
                        ServerText.translate(lang, "chatitems.cooldown.wait", String.valueOf(waitSec)))
                        .withStyle(ChatFormatting.RED);
                McCompat.sendSystemMessage(sender, notice);
                return original;
            }

            InventorySnapshot<ItemStack> invSnapshot = null;
            InventorySnapshot<ItemStack> enderSnapshot = null;
            for (ChatTokens.Segment seg : parsed.segments()) {
                if (seg.tokenType() == TokenType.INV && invSnapshot == null) {
                    invSnapshot = captureInventory(sender, now, settings.snapshotTtlSeconds());
                } else if (seg.tokenType() == TokenType.ENDER && enderSnapshot == null) {
                    enderSnapshot = captureEnder(sender, now, settings.snapshotTtlSeconds());
                }
            }

            MutableComponent out = McCompat.empty();
            for (ChatTokens.Segment seg : parsed.segments()) {
                if (!seg.isToken()) {
                    out.append(McCompat.literal(seg.text()));
                    continue;
                }
                TokenType type = seg.tokenType();
                if (type == TokenType.ITEM) {
                    out.append(formatHeldItem(sender, lang, now, settings.snapshotTtlSeconds()));
                } else if (type == TokenType.OFFHAND) {
                    out.append(formatOffhandItem(sender, lang, now, settings.snapshotTtlSeconds()));
                } else if (type == TokenType.ARMOR) {
                    out.append(formatArmor(sender, lang, now, settings.snapshotTtlSeconds()));
                } else if (type.hotbarIndex() >= 0) {
                    out.append(formatHotbarSlot(sender, type.hotbarIndex(), lang, now, settings.snapshotTtlSeconds()));
                } else if (type == TokenType.INV && invSnapshot != null) {
                    out.append(formatSnapshotTag(invSnapshot, lang));
                } else if (type == TokenType.ENDER && enderSnapshot != null) {
                    out.append(formatSnapshotTag(enderSnapshot, lang));
                }
            }
            return out;
        } catch (RuntimeException e) {
            ModLog.failure("Decorating chat message", e);
            return original;
        }
    }

    private static MutableComponent formatHeldItem(ServerPlayer sender, String lang, long now, int ttlSeconds) {
        ItemStack held = sender.getMainHandItem();
        if (held == null || held.isEmpty()) {
            held = sender.getOffhandItem();
        }
        return formatItemStack(
                sender, held, "chatitems.tag.empty_hand", "chatitems.tag.empty_hand.hover", lang, now, ttlSeconds);
    }

    private static MutableComponent formatOffhandItem(ServerPlayer sender, String lang, long now, int ttlSeconds) {
        return formatItemStack(
                sender, sender.getOffhandItem(), "chatitems.tag.empty_slot", "chatitems.tag.empty_slot.hover", lang, now, ttlSeconds);
    }

    private static MutableComponent formatHotbarSlot(ServerPlayer sender, int hotbarIndex, String lang, long now, int ttlSeconds) {
        ItemStack stack = McCompat.getInventoryItem(sender, hotbarIndex);
        return formatItemStack(
                sender, stack, "chatitems.tag.empty_slot", "chatitems.tag.empty_slot.hover", lang, now, ttlSeconds);
    }

    private static MutableComponent formatArmor(ServerPlayer sender, String lang, long now, int ttlSeconds) {
        EquipmentSlot[] armorOrder = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        MutableComponent out = McCompat.empty();
        boolean any = false;
        for (EquipmentSlot slot : armorOrder) {
            ItemStack piece = sender.getItemBySlot(slot);
            if (piece != null && !piece.isEmpty()) {
                if (any) {
                    out.append(McCompat.literal(" "));
                }
                out.append(formatItemStack(
                        sender, piece, "chatitems.tag.empty_slot", "chatitems.tag.empty_slot.hover", lang, now, ttlSeconds));
                any = true;
            }
        }
        if (!any) {
            Component hover = McCompat.literal(ServerText.translate(lang, "chatitems.tag.empty_armor.hover"))
                    .withStyle(ChatFormatting.GRAY);
            return McCompat.literal(ServerText.translate(lang, "chatitems.tag.empty_armor"))
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                            .withHoverEvent(McCompat.hoverText(hover)));
        }
        return out;
    }

    private static MutableComponent formatItemStack(
            ServerPlayer sender,
            ItemStack stack,
            String emptyKey,
            String emptyHoverKey,
            String lang,
            long now,
            int ttlSeconds
    ) {
        if (stack == null || stack.isEmpty()) {
            Component hover = McCompat.literal(ServerText.translate(lang, emptyHoverKey))
                    .withStyle(ChatFormatting.GRAY);
            return McCompat.literal(ServerText.translate(lang, emptyKey))
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                            .withHoverEvent(McCompat.hoverText(hover)));
        }
        ItemStack copy = stack.copy();
        ChatFormatting rarityColor = McCompat.rarityColor(copy);
        MutableComponent tag = McCompat.literal("[")
                .append(copy.getHoverName().copy())
                .append(McCompat.literal(ChatItems.countSuffix(copy.getCount()) + "]"));
        List<ItemStack> shulkerContents = McCompat.shulkerItems(copy);
        if (shulkerContents != null) {
            InventorySnapshot<ItemStack> shulkerSnapshot = CACHE.storeShulker(
                    sender.getUUID(), copy.getHoverName().getString(), shulkerContents, now, ttlSeconds);
            return tag.withStyle(style -> style.withColor(rarityColor)
                    .withHoverEvent(McCompat.hoverItem(copy))
                    .withClickEvent(McCompat.clickRunCommand(ChatItems.VIEW_COMMAND_PREFIX + shulkerSnapshot.id())));
        }
        return tag.withStyle(style -> style.withColor(rarityColor)
                .withHoverEvent(McCompat.hoverItem(copy)));
    }

    private static MutableComponent formatSnapshotTag(InventorySnapshot<ItemStack> snapshot, String lang) {
        boolean isInv = snapshot.type() == SnapshotType.INV;
        String labelKey = isInv ? "chatitems.tag.inv" : "chatitems.tag.ender";
        String hoverKey = isInv ? "chatitems.tag.inv.hover" : "chatitems.tag.ender.hover";
        ChatFormatting color = isInv ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE;
        Component hover = McCompat.literal(ServerText.translate(lang, hoverKey, snapshot.ownerName()))
                .withStyle(ChatFormatting.GRAY);
        return McCompat.literal(ServerText.translate(lang, labelKey, snapshot.ownerName()))
                .withStyle(style -> style.withColor(color)
                        .withClickEvent(McCompat.clickRunCommand(ChatItems.VIEW_COMMAND_PREFIX + snapshot.id()))
                        .withHoverEvent(McCompat.hoverText(hover)));
    }

    private static InventorySnapshot<ItemStack> captureInventory(ServerPlayer player, long now, int ttlSeconds) {
        List<ItemStack> main = new ArrayList<>(InventorySnapshot.INV_MAIN_SIZE);
        for (int i = 0; i < InventorySnapshot.INV_MAIN_SIZE; i++) {
            ItemStack item = McCompat.getInventoryItem(player, i);
            main.add(item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy());
        }
        List<ItemStack> armor = Jv.listOf(
                copySlot(player, EquipmentSlot.HEAD),
                copySlot(player, EquipmentSlot.CHEST),
                copySlot(player, EquipmentSlot.LEGS),
                copySlot(player, EquipmentSlot.FEET)
        );
        ItemStack offhand = copySlot(player, EquipmentSlot.OFFHAND);
        return CACHE.storeInventory(
                player.getUUID(), player.getName().getString(), main, armor, offhand, now, ttlSeconds);
    }

    private static InventorySnapshot<ItemStack> captureEnder(ServerPlayer player, long now, int ttlSeconds) {
        List<ItemStack> ender = new ArrayList<>(InventorySnapshot.ENDER_SIZE);
        for (int i = 0; i < InventorySnapshot.ENDER_SIZE; i++) {
            ItemStack item = player.getEnderChestInventory().getItem(i);
            ender.add(item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy());
        }
        return CACHE.storeEnder(
                player.getUUID(), player.getName().getString(), ender, now, ttlSeconds);
    }

    private static ItemStack copySlot(ServerPlayer player, EquipmentSlot slot) {
        ItemStack item = player.getItemBySlot(slot);
        return item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
    }

    /**
     * Opens the read-only chest screen for {@code snapshotId} to {@code viewer}.
     *
     * @return {@code true} if the snapshot was found and opened, {@code false} if expired/missing
     */
    public static boolean openSnapshot(ServerPlayer viewer, String snapshotId) {
        if (viewer == null) {
            return false;
        }
        String lang = languageOf(viewer);
        InventorySnapshot<ItemStack> snapshot = CACHE.get(snapshotId, System.currentTimeMillis());
        if (snapshot == null) {
            MutableComponent expired = McCompat.literal(ServerText.translate(lang, "chatitems.snapshot.expired"))
                    .withStyle(ChatFormatting.RED);
            McCompat.sendSystemMessage(viewer, expired);
            return false;
        }
        int size = snapshot.type().chestSize();
        int rows = snapshot.type().rows();
        SimpleContainer container = new SimpleContainer(size);
        ItemStack pane = McCompat.separatorPane();
        List<ItemStack> slots = snapshot.toChestSlots(ItemStack.EMPTY, pane);
        for (int i = 0; i < size && i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            container.setItem(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        String titleKey = snapshot.type() == SnapshotType.INV
                ? "chatitems.gui.inv.title"
                : snapshot.type() == SnapshotType.ENDER
                ? "chatitems.gui.ender.title"
                : "chatitems.gui.shulker.title";
        Component title = McCompat.literal(ServerText.translate(lang, titleKey, snapshot.ownerName()));
        String id = snapshot.id();
        viewer.openMenu(new SimpleMenuProvider(
                (syncId, playerInv, p) -> new ReadOnlyChestMenu(syncId, playerInv, container, rows, id, null),
                title));
        return true;
    }

    /**
     * Opens a 4-row read-only shulker box view for the shulker in {@code chestSlot} of {@code parentSnapshotId},
     * with a Back button in slot {@link InventorySnapshot#SHULKER_BACK_SLOT} that returns to the parent snapshot.
     */
    public static boolean openShulkerSlot(ServerPlayer viewer, String parentSnapshotId, int chestSlot) {
        if (viewer == null) {
            return false;
        }
        String lang = languageOf(viewer);
        InventorySnapshot<ItemStack> parent = CACHE.get(parentSnapshotId, System.currentTimeMillis());
        if (parent == null) {
            MutableComponent expired = McCompat.literal(ServerText.translate(lang, "chatitems.snapshot.expired"))
                    .withStyle(ChatFormatting.RED);
            McCompat.sendSystemMessage(viewer, expired);
            return false;
        }
        ItemStack pane = McCompat.separatorPane();
        List<ItemStack> parentSlots = parent.toChestSlots(ItemStack.EMPTY, pane);
        if (chestSlot < 0 || chestSlot >= parentSlots.size()) {
            return false;
        }
        ItemStack shulkerStack = parentSlots.get(chestSlot);
        List<ItemStack> shulkerItems = McCompat.shulkerItems(shulkerStack);
        if (shulkerItems == null) {
            return false;
        }
        ItemStack back = McCompat.backButton(ServerText.translate(lang, "chatitems.gui.shulker.back", parent.ownerName()));
        List<ItemStack> viewSlots = InventorySnapshot.toShulkerWithBackSlots(shulkerItems, ItemStack.EMPTY, pane, back);
        SimpleContainer container = new SimpleContainer(InventorySnapshot.SHULKER_WITH_BACK_SIZE);
        for (int i = 0; i < InventorySnapshot.SHULKER_WITH_BACK_SIZE && i < viewSlots.size(); i++) {
            ItemStack item = viewSlots.get(i);
            container.setItem(i, item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy());
        }
        Component title = McCompat.literal(
                ServerText.translate(lang, "chatitems.gui.shulker.title", shulkerStack.getHoverName().getString()));
        String pid = parent.id();
        viewer.openMenu(new SimpleMenuProvider(
                (syncId, playerInv, p) -> new ReadOnlyChestMenu(
                        syncId, playerInv, container, InventorySnapshot.SHULKER_WITH_BACK_ROWS, pid, pid),
                title));
        return true;
    }

    /**
     * Handles clicks inside a {@link ReadOnlyChestMenu}: clicking a shulker box inside an {@code [inv]} or
     * {@code [ender]} snapshot opens its contents, and clicking the Back arrow returns to the parent snapshot.
     */
    public static void onMenuClick(Player player, String snapshotId, String parentSnapshotId, int slotId) {
        if (!(player instanceof ServerPlayer) || slotId < 0) {
            return;
        }
        ServerPlayer viewer = (ServerPlayer) player;
        if (parentSnapshotId != null) {
            if (slotId == InventorySnapshot.SHULKER_BACK_SLOT) {
                McCompat.runOnServer(viewer, () -> openSnapshot(viewer, parentSnapshotId));
            }
            return;
        }
        if (snapshotId == null) {
            return;
        }
        InventorySnapshot<ItemStack> snapshot = CACHE.get(snapshotId, System.currentTimeMillis());
        if (snapshot == null) {
            return;
        }
        List<ItemStack> slots = snapshot.toChestSlots(ItemStack.EMPTY, ItemStack.EMPTY);
        if (slotId < slots.size() && McCompat.shulkerItems(slots.get(slotId)) != null) {
            McCompat.runOnServer(viewer, () -> openShulkerSlot(viewer, snapshotId, slotId));
        }
    }

    public static String languageOf(ServerPlayer player) {
        if (player != null) {
            String lang = PlayerLanguage.of(player);
            if (!Jv.isBlank(lang)) {
                return lang;
            }
        }
        return ChatItems.settings().language();
    }
}
