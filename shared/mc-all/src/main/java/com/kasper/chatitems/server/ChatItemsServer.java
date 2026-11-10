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
import java.util.Arrays;
import java.util.List;

/**
 * Shared server-side chat decorator and read-only snapshot opener for Fabric, Forge, and NeoForge.
 */
public final class ChatItemsServer {

    public static final int MAX_CONTAINER_DEPTH = 4;
    private static final int[] EMPTY_PATH = new int[0];

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
        return openContainerPath(viewer, snapshotId, EMPTY_PATH);
    }

    /**
     * Opens a 4-row read-only container view for the shulker box or bundle in {@code chestSlot} of
     * {@code parentSnapshotId}, with a Back button in slot {@link InventorySnapshot#SHULKER_BACK_SLOT}.
     */
    public static boolean openShulkerSlot(ServerPlayer viewer, String parentSnapshotId, int chestSlot) {
        return openContainerPath(viewer, parentSnapshotId, new int[]{chestSlot});
    }

    /**
     * Opens either the root snapshot ({@code slotPath.length == 0}) or a nested shulker/bundle container
     * reached by following {@code slotPath} up to {@link #MAX_CONTAINER_DEPTH}.
     */
    public static boolean openContainerPath(ServerPlayer viewer, String snapshotId, int[] slotPath) {
        if (viewer == null) {
            return false;
        }
        int[] path = slotPath == null || slotPath.length == 0 ? EMPTY_PATH : slotPath.clone();
        if (path.length > MAX_CONTAINER_DEPTH) {
            return false;
        }
        String lang = languageOf(viewer);
        InventorySnapshot<ItemStack> root = CACHE.get(snapshotId, System.currentTimeMillis());
        if (root == null) {
            MutableComponent expired = McCompat.literal(ServerText.translate(lang, "chatitems.snapshot.expired"))
                    .withStyle(ChatFormatting.RED);
            McCompat.sendSystemMessage(viewer, expired);
            return false;
        }
        ItemStack pane = McCompat.separatorPane();
        String inspectHint = ServerText.translate(lang, "chatitems.gui.inspect.hint");
        List<ItemStack> rootSlots = root.toChestSlots(ItemStack.EMPTY, pane);
        String id = root.id();

        if (path.length == 0) {
            int size = root.type().chestSize();
            int rows = root.type().rows();
            SimpleContainer container = new SimpleContainer(size);
            for (int i = 0; i < size && i < rootSlots.size(); i++) {
                ItemStack stack = rootSlots.get(i);
                if (stack == null || stack.isEmpty()) {
                    container.setItem(i, ItemStack.EMPTY);
                } else if (McCompat.shulkerItems(stack) != null) {
                    container.setItem(i, McCompat.withInspectHint(stack, inspectHint));
                } else {
                    container.setItem(i, stack.copy());
                }
            }
            String titleKey = root.type() == SnapshotType.INV
                    ? "chatitems.gui.inv.title"
                    : root.type() == SnapshotType.ENDER
                    ? "chatitems.gui.ender.title"
                    : "chatitems.gui.shulker.title";
            Component title = McCompat.literal(ServerText.translate(lang, titleKey, root.ownerName()));
            viewer.openMenu(new SimpleMenuProvider(
                    (syncId, playerInv, p) -> new ReadOnlyChestMenu(syncId, playerInv, container, rows, id, EMPTY_PATH),
                    title));
            return true;
        }

        List<ItemStack> currentSlots = rootSlots;
        ItemStack currentContainer = ItemStack.EMPTY;
        String parentLabel = root.ownerName();
        for (int depth = 0; depth < path.length; depth++) {
            int slot = path[depth];
            int limit = depth == 0 ? currentSlots.size() : InventorySnapshot.SHULKER_SIZE;
            if (slot < 0 || slot >= limit || slot >= currentSlots.size()) {
                return false;
            }
            if (depth > 0) {
                parentLabel = currentContainer.getHoverName().getString();
            }
            ItemStack candidate = currentSlots.get(slot);
            List<ItemStack> inner = McCompat.shulkerItems(candidate);
            if (inner == null) {
                return false;
            }
            currentContainer = candidate;
            currentSlots = inner;
        }

        ItemStack back = McCompat.backButton(
                ServerText.translate(lang, "chatitems.gui.shulker.back", parentLabel),
                ServerText.translate(lang, "chatitems.gui.back.hint"));
        List<ItemStack> viewSlots = InventorySnapshot.toShulkerWithBackSlots(currentSlots, ItemStack.EMPTY, pane, back);
        SimpleContainer container = new SimpleContainer(InventorySnapshot.SHULKER_WITH_BACK_SIZE);
        boolean canDrillDeeper = path.length < MAX_CONTAINER_DEPTH;
        for (int i = 0; i < InventorySnapshot.SHULKER_WITH_BACK_SIZE && i < viewSlots.size(); i++) {
            ItemStack item = viewSlots.get(i);
            if (item == null || item.isEmpty()) {
                container.setItem(i, ItemStack.EMPTY);
            } else if (canDrillDeeper && i < InventorySnapshot.SHULKER_SIZE && McCompat.shulkerItems(item) != null) {
                container.setItem(i, McCompat.withInspectHint(item, inspectHint));
            } else {
                container.setItem(i, item.copy());
            }
        }
        Component title = McCompat.literal(
                ServerText.translate(lang, "chatitems.gui.shulker.title", currentContainer.getHoverName().getString()));
        viewer.openMenu(new SimpleMenuProvider(
                (syncId, playerInv, p) -> new ReadOnlyChestMenu(
                        syncId, playerInv, container, InventorySnapshot.SHULKER_WITH_BACK_ROWS, id, path),
                title));
        return true;
    }

    /**
     * Handles clicks inside a {@link ReadOnlyChestMenu}: clicking a shulker box or bundle drills into
     * its contents (up to {@link #MAX_CONTAINER_DEPTH}), and clicking the Back arrow returns one level up.
     */
    public static void onMenuClick(Player player, String snapshotId, int[] slotPath, int slotId) {
        if (!(player instanceof ServerPlayer) || snapshotId == null || slotId < 0) {
            return;
        }
        ServerPlayer viewer = (ServerPlayer) player;
        int[] path = slotPath == null ? EMPTY_PATH : slotPath;
        if (path.length > 0 && slotId == InventorySnapshot.SHULKER_BACK_SLOT) {
            int[] parentPath = Arrays.copyOf(path, path.length - 1);
            McCompat.runOnServer(viewer, () -> openContainerPath(viewer, snapshotId, parentPath));
            return;
        }
        if (path.length >= MAX_CONTAINER_DEPTH) {
            return;
        }
        int topSlots = path.length > 0 ? InventorySnapshot.SHULKER_SIZE : viewer.containerMenu.slots.size();
        if (slotId >= topSlots || slotId >= viewer.containerMenu.slots.size()) {
            return;
        }
        if (path.length == 0) {
            InventorySnapshot<ItemStack> snapshot = CACHE.get(snapshotId, System.currentTimeMillis());
            if (snapshot == null || slotId >= snapshot.type().chestSize()) {
                return;
            }
            List<ItemStack> slots = snapshot.toChestSlots(ItemStack.EMPTY, ItemStack.EMPTY);
            if (slotId < slots.size() && McCompat.shulkerItems(slots.get(slotId)) != null) {
                McCompat.runOnServer(viewer, () -> openContainerPath(viewer, snapshotId, new int[]{slotId}));
            }
            return;
        }
        ItemStack clicked = viewer.containerMenu.getSlot(slotId).getItem();
        if (McCompat.shulkerItems(clicked) != null) {
            int[] nextPath = Arrays.copyOf(path, path.length + 1);
            nextPath[path.length] = slotId;
            McCompat.runOnServer(viewer, () -> openContainerPath(viewer, snapshotId, nextPath));
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
