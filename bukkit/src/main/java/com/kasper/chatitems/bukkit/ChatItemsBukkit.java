package com.kasper.chatitems.bukkit;

import com.kasper.chatitems.AdminCommands;
import com.kasper.chatitems.ChatItems;
import com.kasper.chatitems.ChatTokens;
import com.kasper.chatitems.InventorySnapshot;
import com.kasper.chatitems.Jv;
import com.kasper.chatitems.ModLog;
import com.kasper.chatitems.Reply;
import com.kasper.chatitems.ServerSettings;
import com.kasper.chatitems.ServerText;
import com.kasper.chatitems.SnapshotCache;
import com.kasper.chatitems.SnapshotType;
import com.kasper.chatitems.TokenType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Paper / Folia / Purpur / Spigot plugin for Minecraft 1.16.5 – 26.3.
 * Replaces {@code [item]} (and aliases {@code [i]}, {@code [hand]}), {@code [inv]}, and {@code [ender]}
 * in chat and serves read-only snapshot windows on {@code /chatitems view <id>}.
 */
public final class ChatItemsBukkit extends JavaPlugin implements Listener {

    private static final int RELOAD_CHECK_TICKS = 40;

    private final SnapshotCache<ItemStack> cache = new SnapshotCache<>();
    private Scheduling scheduling;
    private int ticks;

    @Override
    public void onEnable() {
        ModLog.use(new ModLog.Sink() {
            @Override
            public void info(String message) {
                getLogger().info(message);
            }

            @Override
            public void warn(String message, Throwable error) {
                if (error == null) {
                    getLogger().warning(message);
                } else {
                    getLogger().log(Level.WARNING, message, error);
                }
            }
        });
        ChatItems.init(getDataFolder().toPath().resolve(ServerSettings.FILE_NAME), "Bukkit");
        scheduling = Scheduling.create(this);
        getServer().getPluginManager().registerEvents(this, this);
        scheduling.everyTick(this::tick);
    }

    @Override
    public void onDisable() {
        if (scheduling != null) {
            scheduling.cancelAll();
        }
        cache.clear();
    }

    private void tick() {
        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            ChatItems.settings().reloadIfChanged();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("view")) {
            if (sender instanceof Player) {
                Player viewer = (Player) sender;
                if (args.length >= 3) {
                    try {
                        int slot = Integer.parseInt(args[2]);
                        openShulkerSlot(viewer, args[1], slot);
                        return true;
                    } catch (NumberFormatException ignored) {
                        return true;
                    }
                }
                openSnapshot(viewer, args[1]);
            }
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        boolean readOnlySub = sub.equals("status") || sub.equals("help");
        if (!readOnlySub && sender instanceof Player && !sender.hasPermission("chatitems.admin")) {
            String lang = localeOf(sender);
            sender.sendMessage(ChatColor.RED + ServerText.translate(lang, "chatitems.permission.denied"));
            return true;
        }
        Reply reply = AdminCommands.execute(String.join(" ", args), context(sender));
        for (Reply.Line line : reply.lines()) {
            sender.sendMessage(color(line.kind()) + line.text());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("chatitems.admin")) {
            return Collections.emptyList();
        }
        return AdminCommands.suggestions(String.join(" ", args));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        ServerSettings settings = ChatItems.settings();
        settings.reloadIfChanged();
        Player sender = event.getPlayer();
        ChatTokens.ParseResult parsed = ChatTokens.parse(
                event.getMessage(), settings, type -> hasTokenPermission(sender, type));
        if (!parsed.hasTokens()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean bypassCooldown = sender.hasPermission("chatitems.bypass.cooldown");
        if (!bypassCooldown && !ChatItems.cooldowns().tryAcquire(sender.getUniqueId(), now, settings.cooldownSeconds())) {
            int waitSec = ChatItems.cooldowns().checkRemainingSeconds(sender.getUniqueId(), now, settings.cooldownSeconds());
            event.setCancelled(true);
            String lang = localeOf(sender);
            sender.sendMessage(ChatColor.RED + ServerText.translate(lang, "chatitems.cooldown.wait", String.valueOf(waitSec)));
            return;
        }

        ItemStack heldSnapshot = captureHeldItem(sender);
        PlayerInventory senderInv = sender.getInventory();
        ItemStack offhandItem = cloneOrNull(senderInv.getItemInOffHand());
        List<ItemStack> hotbarItems = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            hotbarItems.add(cloneOrNull(senderInv.getItem(i)));
        }
        List<ItemStack> armorItems = Jv.listOf(
                cloneOrNull(senderInv.getHelmet()),
                cloneOrNull(senderInv.getChestplate()),
                cloneOrNull(senderInv.getLeggings()),
                cloneOrNull(senderInv.getBoots())
        );

        InventorySnapshot<ItemStack> invSnapshot = null;
        InventorySnapshot<ItemStack> enderSnapshot = null;
        for (ChatTokens.Segment seg : parsed.segments()) {
            if (seg.tokenType() == TokenType.INV && invSnapshot == null) {
                invSnapshot = captureInventory(sender, now, settings.snapshotTtlSeconds());
            } else if (seg.tokenType() == TokenType.ENDER && enderSnapshot == null) {
                enderSnapshot = captureEnder(sender, now, settings.snapshotTtlSeconds());
            }
        }

        Set<Player> recipients = new java.util.HashSet<>(event.getRecipients());
        event.setCancelled(true);
        getLogger().info("<" + sender.getName() + "> " + event.getMessage());

        Map<ItemStack, String> shulkerIds = new IdentityHashMap<>();
        for (Player recipient : recipients) {
            String lang = localeOf(recipient);
            BaseComponent[] line = buildChatLine(
                    sender, parsed, heldSnapshot, offhandItem, hotbarItems, armorItems,
                    invSnapshot, enderSnapshot, shulkerIds, lang, now, settings.snapshotTtlSeconds());
            recipient.spigot().sendMessage(line);
        }
    }

    private static boolean hasTokenPermission(Player player, TokenType type) {
        if (type.isItemLike()) {
            return player.hasPermission("chatitems.item");
        }
        if (type == TokenType.INV) {
            return player.hasPermission("chatitems.inv");
        }
        if (type == TokenType.ENDER) {
            return player.hasPermission("chatitems.ender");
        }
        return true;
    }

    private BaseComponent[] buildChatLine(
            Player sender,
            ChatTokens.ParseResult parsed,
            ItemStack heldItem,
            ItemStack offhandItem,
            List<ItemStack> hotbarItems,
            List<ItemStack> armorItems,
            InventorySnapshot<ItemStack> invSnapshot,
            InventorySnapshot<ItemStack> enderSnapshot,
            Map<ItemStack, String> shulkerIds,
            String lang,
            long now,
            int ttlSeconds
    ) {
        List<BaseComponent> parts = new ArrayList<>();
        TextComponent prefix = new TextComponent("<" + sender.getDisplayName() + ChatColor.RESET + "> ");
        parts.add(prefix);

        for (ChatTokens.Segment seg : parsed.segments()) {
            if (!seg.isToken()) {
                parts.add(new TextComponent(seg.text()));
                continue;
            }
            TokenType type = seg.tokenType();
            if (type == TokenType.ITEM) {
                parts.add(buildItemComponent(
                        sender, heldItem, "chatitems.tag.empty_hand", "chatitems.tag.empty_hand.hover",
                        shulkerIds, lang, now, ttlSeconds));
            } else if (type == TokenType.OFFHAND) {
                parts.add(buildItemComponent(
                        sender, offhandItem, "chatitems.tag.empty_slot", "chatitems.tag.empty_slot.hover",
                        shulkerIds, lang, now, ttlSeconds));
            } else if (type == TokenType.ARMOR) {
                parts.addAll(buildArmorComponents(sender, armorItems, shulkerIds, lang, now, ttlSeconds));
            } else if (type.hotbarIndex() >= 0) {
                int idx = type.hotbarIndex();
                ItemStack slotItem = idx < hotbarItems.size() ? hotbarItems.get(idx) : null;
                parts.add(buildItemComponent(
                        sender, slotItem, "chatitems.tag.empty_slot", "chatitems.tag.empty_slot.hover",
                        shulkerIds, lang, now, ttlSeconds));
            } else if (type == TokenType.INV && invSnapshot != null) {
                parts.add(buildSnapshotComponent(invSnapshot, lang));
            } else if (type == TokenType.ENDER && enderSnapshot != null) {
                parts.add(buildSnapshotComponent(enderSnapshot, lang));
            }
        }
        return parts.toArray(new BaseComponent[0]);
    }

    private List<BaseComponent> buildArmorComponents(
            Player sender,
            List<ItemStack> armorItems,
            Map<ItemStack, String> shulkerIds,
            String lang,
            long now,
            int ttlSeconds
    ) {
        List<BaseComponent> out = new ArrayList<>();
        for (ItemStack piece : armorItems) {
            if (piece != null && piece.getType() != Material.AIR && piece.getAmount() > 0) {
                if (!out.isEmpty()) {
                    out.add(new TextComponent(" "));
                }
                out.add(buildItemComponent(
                        sender, piece, "chatitems.tag.empty_slot", "chatitems.tag.empty_slot.hover",
                        shulkerIds, lang, now, ttlSeconds));
            }
        }
        if (out.isEmpty()) {
            TextComponent empty = new TextComponent(ServerText.translate(lang, "chatitems.tag.empty_armor"));
            empty.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            empty.setHoverEvent(hoverText(ServerText.translate(lang, "chatitems.tag.empty_armor.hover")));
            out.add(empty);
        }
        return out;
    }

    private BaseComponent buildItemComponent(
            Player sender,
            ItemStack stack,
            String emptyKey,
            String emptyHoverKey,
            Map<ItemStack, String> shulkerIds,
            String lang,
            long now,
            int ttlSeconds
    ) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            TextComponent empty = new TextComponent(ServerText.translate(lang, emptyKey));
            empty.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            empty.setHoverEvent(hoverText(ServerText.translate(lang, emptyHoverKey)));
            return empty;
        }
        String name = itemDisplayName(stack);
        TextComponent tag = new TextComponent("[" + name + ChatItems.countSuffix(stack.getAmount()) + "]");
        boolean enchanted = stack.getItemMeta() != null && stack.getItemMeta().hasEnchants();
        tag.setColor(enchanted ? net.md_5.bungee.api.ChatColor.AQUA : net.md_5.bungee.api.ChatColor.WHITE);
        HoverEvent hover = itemHover(stack);
        if (hover != null) {
            tag.setHoverEvent(hover);
        }
        String shulkerId = shulkerIds.get(stack);
        if (shulkerId == null && !shulkerIds.containsKey(stack)) {
            List<ItemStack> shulkerContents = extractShulkerItems(stack);
            if (shulkerContents != null) {
                InventorySnapshot<ItemStack> shulkerSnapshot = cache.storeShulker(
                        sender.getUniqueId(), name, shulkerContents, now, ttlSeconds);
                shulkerId = shulkerSnapshot.id();
            }
            shulkerIds.put(stack, shulkerId);
        }
        if (shulkerId != null) {
            tag.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ChatItems.VIEW_COMMAND_PREFIX + shulkerId));
        }
        return tag;
    }

    private BaseComponent buildSnapshotComponent(InventorySnapshot<ItemStack> snapshot, String lang) {
        boolean isInv = snapshot.type() == SnapshotType.INV;
        String labelKey = isInv ? "chatitems.tag.inv" : "chatitems.tag.ender";
        String hoverKey = isInv ? "chatitems.tag.inv.hover" : "chatitems.tag.ender.hover";
        TextComponent tag = new TextComponent(ServerText.translate(lang, labelKey, snapshot.ownerName()));
        tag.setColor(isInv ? net.md_5.bungee.api.ChatColor.AQUA : net.md_5.bungee.api.ChatColor.LIGHT_PURPLE);
        tag.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ChatItems.VIEW_COMMAND_PREFIX + snapshot.id()));
        tag.setHoverEvent(hoverText(ServerText.translate(lang, hoverKey, snapshot.ownerName())));
        return tag;
    }

    private static HoverEvent hoverText(String text) {
        TextComponent component = new TextComponent(text);
        component.setColor(net.md_5.bungee.api.ChatColor.GRAY);
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new BaseComponent[]{component}));
    }

    private static HoverEvent itemHover(ItemStack stack) {
        try {
            Content content = Bukkit.getItemFactory().hoverContentOf(stack);
            return new HoverEvent(HoverEvent.Action.SHOW_ITEM, content);
        } catch (Throwable ignored) {
            String key = stack.getType().getKey().toString();
            return new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                    new net.md_5.bungee.api.chat.hover.content.Item(key, stack.getAmount(), null));
        }
    }

    private static String itemDisplayName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String raw = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder b = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            b.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return b.toString();
    }

    private static ItemStack captureHeldItem(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        if (main != null && main.getType() != Material.AIR && main.getAmount() > 0) {
            return main.clone();
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() != Material.AIR && off.getAmount() > 0) {
            return off.clone();
        }
        return null;
    }

    private InventorySnapshot<ItemStack> captureInventory(Player player, long now, int ttlSeconds) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        List<ItemStack> main = new ArrayList<>(InventorySnapshot.INV_MAIN_SIZE);
        for (int i = 0; i < InventorySnapshot.INV_MAIN_SIZE; i++) {
            ItemStack item = i < storage.length ? storage[i] : null;
            main.add(cloneOrNull(item));
        }
        List<ItemStack> armor = Jv.listOf(
                cloneOrNull(inv.getHelmet()),
                cloneOrNull(inv.getChestplate()),
                cloneOrNull(inv.getLeggings()),
                cloneOrNull(inv.getBoots())
        );
        ItemStack offhand = cloneOrNull(inv.getItemInOffHand());
        return cache.storeInventory(player.getUniqueId(), player.getName(), main, armor, offhand, now, ttlSeconds);
    }

    private InventorySnapshot<ItemStack> captureEnder(Player player, long now, int ttlSeconds) {
        ItemStack[] contents = player.getEnderChest().getContents();
        List<ItemStack> ender = new ArrayList<>(InventorySnapshot.ENDER_SIZE);
        for (int i = 0; i < InventorySnapshot.ENDER_SIZE; i++) {
            ItemStack item = i < contents.length ? contents[i] : null;
            ender.add(cloneOrNull(item));
        }
        return cache.storeEnder(player.getUniqueId(), player.getName(), ender, now, ttlSeconds);
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return null;
        }
        return stack.clone();
    }

    private static List<ItemStack> extractShulkerItems(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return null;
        }
        BlockState state;
        try {
            state = ((BlockStateMeta) meta).getBlockState();
        } catch (Throwable ignored) {
            return null;
        }
        if (!(state instanceof ShulkerBox)) {
            return null;
        }
        ItemStack[] contents = ((ShulkerBox) state).getInventory().getContents();
        List<ItemStack> out = new ArrayList<>(InventorySnapshot.SHULKER_SIZE);
        for (int i = 0; i < InventorySnapshot.SHULKER_SIZE; i++) {
            ItemStack item = i < contents.length ? contents[i] : null;
            out.add(cloneOrNull(item));
        }
        return out;
    }

    private void openSnapshot(Player viewer, String snapshotId) {
        String lang = localeOf(viewer);
        InventorySnapshot<ItemStack> snapshot = cache.get(snapshotId, System.currentTimeMillis());
        if (snapshot == null) {
            viewer.sendMessage(ChatColor.RED + ServerText.translate(lang, "chatitems.snapshot.expired"));
            return;
        }
        String titleKey;
        if (snapshot.type() == SnapshotType.INV) {
            titleKey = "chatitems.gui.inv.title";
        } else if (snapshot.type() == SnapshotType.ENDER) {
            titleKey = "chatitems.gui.ender.title";
        } else {
            titleKey = "chatitems.gui.shulker.title";
        }
        String title = ServerText.translate(lang, titleKey, snapshot.ownerName());
        ItemStack pane = separatorPane();
        List<ItemStack> slots = snapshot.toChestSlots(null, pane);

        scheduling.runForPlayer(viewer, () -> {
            Inventory gui = Bukkit.createInventory(new SnapshotHolder(snapshot.id()), snapshot.type().chestSize(), title);
            for (int i = 0; i < slots.size(); i++) {
                ItemStack item = slots.get(i);
                if (item != null) {
                    gui.setItem(i, item.clone());
                }
            }
            viewer.openInventory(gui);
        });
    }

    private void openShulkerSlot(Player viewer, String snapshotId, int slot) {
        String lang = localeOf(viewer);
        InventorySnapshot<ItemStack> parent = cache.get(snapshotId, System.currentTimeMillis());
        if (parent == null) {
            viewer.sendMessage(ChatColor.RED + ServerText.translate(lang, "chatitems.snapshot.expired"));
            return;
        }
        ItemStack pane = separatorPane();
        List<ItemStack> parentSlots = parent.toChestSlots(null, pane);
        if (slot < 0 || slot >= parentSlots.size()) {
            return;
        }
        ItemStack clicked = parentSlots.get(slot);
        List<ItemStack> shulkerItems = extractShulkerItems(clicked);
        if (shulkerItems == null) {
            return;
        }
        String shulkerTitle = ServerText.translate(lang, "chatitems.gui.shulker.title", itemDisplayName(clicked));
        ItemStack back = backButton(ServerText.translate(lang, "chatitems.gui.shulker.back"));
        List<ItemStack> chestSlots = InventorySnapshot.toShulkerWithBackSlots(shulkerItems, null, pane, back);

        scheduling.runForPlayer(viewer, () -> {
            Inventory gui = Bukkit.createInventory(
                    new SnapshotHolder(parent.id(), parent.id()),
                    InventorySnapshot.SHULKER_WITH_BACK_SIZE,
                    shulkerTitle);
            for (int i = 0; i < chestSlots.size(); i++) {
                ItemStack item = chestSlots.get(i);
                if (item != null) {
                    gui.setItem(i, item.clone());
                }
            }
            viewer.openInventory(gui);
        });
    }

    private static ItemStack separatorPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static ItemStack backButton(String label) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + label);
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isSnapshotView(event.getView())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player viewer = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        SnapshotHolder holder = (SnapshotHolder) top.getHolder();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (holder.parentSnapshotId() != null) {
            if (rawSlot == InventorySnapshot.SHULKER_BACK_SLOT) {
                openSnapshot(viewer, holder.parentSnapshotId());
            }
            return;
        }
        ItemStack clicked = top.getItem(rawSlot);
        if (extractShulkerItems(clicked) != null) {
            openShulkerSlot(viewer, holder.snapshotId(), rawSlot);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isSnapshotView(event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (isSnapshotView(event.getView())) {
            event.setCancelled(true);
        }
    }

    private static boolean isSnapshotView(InventoryView view) {
        return view != null && view.getTopInventory() != null
                && view.getTopInventory().getHolder() instanceof SnapshotHolder;
    }

    private AdminCommands.Context context(CommandSender sender) {
        String lang = localeOf(sender);
        int active = cache.activeCount(System.currentTimeMillis());
        return new AdminCommands.Context() {
            @Override
            public String language() {
                return lang;
            }

            @Override
            public int activeSnapshots() {
                return active;
            }
        };
    }

    private static String localeOf(CommandSender sender) {
        if (sender instanceof Player) {
            try {
                return ((Player) sender).getLocale();
            } catch (Throwable ignored) {
                // fallback below
            }
        }
        return ChatItems.settings().language();
    }

    private static ChatColor color(Reply.Kind kind) {
        switch (kind) {
            case TITLE:
                return ChatColor.GOLD;
            case DONE:
                return ChatColor.GREEN;
            case ERROR:
                return ChatColor.RED;
            case HINT:
                return ChatColor.GRAY;
            default:
                return ChatColor.WHITE;
        }
    }
}
