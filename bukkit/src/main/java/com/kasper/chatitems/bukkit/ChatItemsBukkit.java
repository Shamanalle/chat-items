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
import net.md_5.bungee.api.chat.TranslatableComponent;
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
import org.bukkit.event.inventory.ClickType;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Paper / Folia / Purpur / Spigot plugin for Minecraft 1.16.5 – 26.3.
 * Replaces {@code [item]}, {@code [offhand]}, {@code [armor]}, {@code [slot1-9]}, {@code [inv]}, and
 * {@code [ender]} in chat and serves read-only snapshot windows on {@code /chatitems view <id>}.
 */
public final class ChatItemsBukkit extends JavaPlugin implements Listener {

    private static final int RELOAD_CHECK_TICKS = 40;
    private static final int MAX_CONTAINER_DEPTH = 4;
    private static final int[] EMPTY_PATH = new int[0];

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
                    int depth = args.length - 2;
                    if (depth > MAX_CONTAINER_DEPTH) {
                        return true;
                    }
                    int[] path = new int[depth];
                    try {
                        for (int i = 0; i < depth; i++) {
                            path[i] = Integer.parseInt(args[i + 2]);
                        }
                        openContainerPath(viewer, args[1], path);
                    } catch (NumberFormatException ignored) {
                        // ignore malformed slot argument
                    }
                    return true;
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
            String suggested = sender instanceof Player ? AdminCommands.suggestedCommand(line.text()) : null;
            if (suggested != null) {
                Player player = (Player) sender;
                TextComponent comp = new TextComponent(line.text());
                comp.setColor(bungeeColor(line.kind()));
                comp.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggested));
                player.spigot().sendMessage(comp);
            } else {
                sender.sendMessage(color(line.kind()) + line.text());
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = !(sender instanceof Player) || sender.hasPermission("chatitems.admin");
        return AdminCommands.suggestions(String.join(" ", args), isAdmin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        ServerSettings settings = ChatItems.settings();
        settings.reloadIfChanged();
        Player sender = event.getPlayer();
        String rawMessage = event.getMessage();
        ChatTokens.ParseResult parsed = ChatTokens.parse(
                rawMessage, settings, type -> hasTokenPermission(sender, type));
        if (!parsed.hasTokens()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean bypassCooldown = sender.hasPermission("chatitems.bypass.cooldown");
        if (!bypassCooldown && !ChatItems.cooldowns().tryAcquire(sender.getUniqueId(), now, settings.cooldownSeconds())) {
            int waitSec = ChatItems.cooldowns().checkRemainingSeconds(sender.getUniqueId(), now, settings.cooldownSeconds());
            event.setCancelled(true);
            String lang = localeOf(sender);
            String warning = ChatColor.RED + ServerText.translate(lang, "chatitems.cooldown.wait", String.valueOf(waitSec));
            scheduling.runForPlayer(sender, () -> sender.sendMessage(warning));
            return;
        }

        Set<Player> recipients = new HashSet<>(event.getRecipients());
        String chatFormat = event.getFormat();
        String senderName = sender.getName();
        String senderDisplayName = sender.getDisplayName();
        int ttlSeconds = settings.snapshotTtlSeconds();
        event.setCancelled(true);

        scheduling.runForPlayer(sender, () -> {
            if (!sender.isOnline()) {
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
                    invSnapshot = captureInventory(sender, now, ttlSeconds);
                } else if (seg.tokenType() == TokenType.ENDER && enderSnapshot == null) {
                    enderSnapshot = captureEnder(sender, now, ttlSeconds);
                }
            }

            getLogger().info("<" + senderName + "> " + rawMessage);

            Map<ItemStack, String> shulkerIds = new IdentityHashMap<>();
            InventorySnapshot<ItemStack> finalInv = invSnapshot;
            InventorySnapshot<ItemStack> finalEnder = enderSnapshot;
            for (Player recipient : recipients) {
                if (recipient == null || !recipient.isOnline()) {
                    continue;
                }
                String lang = localeOf(recipient);
                BaseComponent[] line = buildChatLine(
                        sender, senderDisplayName, chatFormat, parsed, heldSnapshot, offhandItem,
                        hotbarItems, armorItems, finalInv, finalEnder, shulkerIds, lang, now, ttlSeconds);
                scheduling.runForPlayer(recipient, () -> {
                    if (recipient.isOnline()) {
                        recipient.spigot().sendMessage(line);
                    }
                });
            }
        });
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
            String senderDisplayName,
            String chatFormat,
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
        String fmt = chatFormat != null ? chatFormat : "<%1$s> %2$s";
        String withSender = fmt.replace("%1$s", senderDisplayName + ChatColor.RESET);
        int msgIdx = withSender.indexOf("%2$s");
        String prefixStr = msgIdx >= 0 ? withSender.substring(0, msgIdx) : "<" + senderDisplayName + ChatColor.RESET + "> ";
        String suffixStr = msgIdx >= 0 ? withSender.substring(msgIdx + 4) : "";
        Collections.addAll(parts, TextComponent.fromLegacyText(prefixStr));

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
        if (!suffixStr.isEmpty()) {
            Collections.addAll(parts, TextComponent.fromLegacyText(suffixStr));
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
        TextComponent tag = new TextComponent("[");
        for (BaseComponent namePart : buildItemNameComponents(stack)) {
            tag.addExtra(namePart);
        }
        tag.addExtra(new TextComponent(ChatItems.countSuffix(stack.getAmount()) + "]"));
        tag.setColor(itemRarityColor(stack));
        HoverEvent hover = itemHover(stack);
        if (hover != null) {
            tag.setHoverEvent(hover);
        }
        String shulkerId = shulkerIds.get(stack);
        if (shulkerId == null && !shulkerIds.containsKey(stack)) {
            List<ItemStack> shulkerContents = extractShulkerItems(stack);
            if (shulkerContents != null) {
                InventorySnapshot<ItemStack> shulkerSnapshot = cache.storeShulker(
                        sender.getUniqueId(), itemDisplayName(stack), shulkerContents, now, ttlSeconds);
                shulkerId = shulkerSnapshot.id();
            }
            shulkerIds.put(stack, shulkerId);
        }
        if (shulkerId != null) {
            tag.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ChatItems.VIEW_COMMAND_PREFIX + shulkerId));
        }
        return tag;
    }

    private static BaseComponent[] buildItemNameComponents(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return TextComponent.fromLegacyText(meta.getDisplayName());
        }
        return new BaseComponent[]{new TranslatableComponent(itemTranslationKey(stack))};
    }

    private static String itemTranslationKey(ItemStack stack) {
        try {
            Method m = stack.getClass().getMethod("translationKey");
            Object v = m.invoke(stack);
            if (v instanceof String && !((String) v).isEmpty()) {
                return (String) v;
            }
        } catch (Throwable ignored) {
            // try Material#getTranslationKey below
        }
        Material type = stack.getType();
        try {
            Method m = Material.class.getMethod("getTranslationKey");
            Object v = m.invoke(type);
            if (v instanceof String && !((String) v).isEmpty()) {
                return (String) v;
            }
        } catch (Throwable ignored) {
            // construct standard minecraft translation key
        }
        return (type.isBlock() ? "block.minecraft." : "item.minecraft.") + type.getKey().getKey();
    }

    private static net.md_5.bungee.api.ChatColor itemRarityColor(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        boolean enchanted = meta != null && meta.hasEnchants();
        String rarityName = resolveRarityName(stack, meta);
        if ("EPIC".equals(rarityName)) {
            return net.md_5.bungee.api.ChatColor.LIGHT_PURPLE;
        }
        if ("RARE".equals(rarityName)) {
            return enchanted ? net.md_5.bungee.api.ChatColor.LIGHT_PURPLE : net.md_5.bungee.api.ChatColor.AQUA;
        }
        if ("UNCOMMON".equals(rarityName)) {
            return enchanted ? net.md_5.bungee.api.ChatColor.AQUA : net.md_5.bungee.api.ChatColor.YELLOW;
        }
        return enchanted ? net.md_5.bungee.api.ChatColor.AQUA : net.md_5.bungee.api.ChatColor.WHITE;
    }

    private static String resolveRarityName(ItemStack stack, ItemMeta meta) {
        if (meta != null) {
            try {
                Method hasRarity = meta.getClass().getMethod("hasRarity");
                if (Boolean.TRUE.equals(hasRarity.invoke(meta))) {
                    Object r = meta.getClass().getMethod("getRarity").invoke(meta);
                    if (r != null) {
                        return r.toString().toUpperCase(Locale.ROOT);
                    }
                }
            } catch (Throwable ignored) {
                // older server API
            }
        }
        Material type = stack.getType();
        try {
            Object r = Material.class.getMethod("getItemRarity").invoke(type);
            if (r != null) {
                return r.toString().toUpperCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
            // fallback table for 1.16.5 - 1.20.4
        }
        String name = type.name();
        if (name.equals("DRAGON_EGG") || name.equals("ENCHANTED_GOLDEN_APPLE") || name.equals("HEAVY_CORE")
                || name.equals("DEBUG_STICK") || name.equals("STRUCTURE_BLOCK") || name.equals("JIGSAW")
                || name.equals("LIGHT") || name.equals("SPAWNER") || name.contains("COMMAND_BLOCK")) {
            return "EPIC";
        }
        if (name.equals("BEACON") || name.equals("CONDUIT") || name.equals("END_CRYSTAL")
                || name.equals("GOLDEN_APPLE") || name.equals("TRIDENT") || name.equals("MACE")
                || name.startsWith("MUSIC_DISC_")) {
            return "RARE";
        }
        if (name.equals("ELYTRA") || name.equals("NETHER_STAR") || name.equals("TOTEM_OF_UNDYING")
                || name.equals("DRAGON_BREATH") || name.equals("EXPERIENCE_BOTTLE") || name.equals("HEART_OF_THE_SEA")
                || name.equals("ENCHANTED_BOOK") || name.equals("ECHO_SHARD") || name.equals("DISC_FRAGMENT_5")
                || name.equals("GOAT_HORN") || name.equals("RECOVERY_COMPASS") || name.equals("SNIFFER_EGG")
                || name.equals("CREEPER_HEAD") || name.equals("DRAGON_HEAD") || name.equals("PLAYER_HEAD")
                || name.equals("ZOMBIE_HEAD") || name.equals("SKELETON_SKULL") || name.equals("WITHER_SKELETON_SKULL")
                || name.equals("PIGLIN_HEAD") || name.endsWith("_BANNER_PATTERN") || name.endsWith("_SMITHING_TEMPLATE")) {
            return "UNCOMMON";
        }
        return "COMMON";
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
            return ChatColor.stripColor(meta.getDisplayName());
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
        if (meta instanceof BlockStateMeta) {
            BlockState state;
            try {
                state = ((BlockStateMeta) meta).getBlockState();
            } catch (Throwable ignored) {
                state = null;
            }
            if (state instanceof ShulkerBox) {
                ItemStack[] contents = ((ShulkerBox) state).getInventory().getContents();
                List<ItemStack> out = new ArrayList<>(InventorySnapshot.SHULKER_SIZE);
                for (int i = 0; i < InventorySnapshot.SHULKER_SIZE; i++) {
                    ItemStack item = i < contents.length ? contents[i] : null;
                    out.add(cloneOrNull(item));
                }
                return out;
            }
        }
        return extractBundleItems(meta);
    }

    private static List<ItemStack> extractBundleItems(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        try {
            Class<?> bundleMetaClass = Class.forName("org.bukkit.inventory.meta.BundleMeta");
            if (!bundleMetaClass.isInstance(meta)) {
                return null;
            }
            Object raw = bundleMetaClass.getMethod("getItems").invoke(meta);
            if (!(raw instanceof List<?>)) {
                return null;
            }
            List<?> items = (List<?>) raw;
            List<ItemStack> out = new ArrayList<>(InventorySnapshot.SHULKER_SIZE);
            for (int i = 0; i < InventorySnapshot.SHULKER_SIZE; i++) {
                Object entry = i < items.size() ? items.get(i) : null;
                out.add(entry instanceof ItemStack ? cloneOrNull((ItemStack) entry) : null);
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void openSnapshot(Player viewer, String snapshotId) {
        openContainerPath(viewer, snapshotId, EMPTY_PATH);
    }

    private void openContainerPath(Player viewer, String snapshotId, int[] slotPath) {
        int[] path = slotPath == null || slotPath.length == 0 ? EMPTY_PATH : slotPath.clone();
        if (path.length > MAX_CONTAINER_DEPTH) {
            return;
        }
        String lang = localeOf(viewer);
        InventorySnapshot<ItemStack> root = cache.get(snapshotId, System.currentTimeMillis());
        if (root == null) {
            String expired = ChatColor.RED + ServerText.translate(lang, "chatitems.snapshot.expired");
            scheduling.runForPlayer(viewer, () -> viewer.sendMessage(expired));
            return;
        }
        ItemStack pane = separatorPane();
        String inspectHint = ServerText.translate(lang, "chatitems.gui.inspect.hint");
        List<ItemStack> rootSlots = root.toChestSlots(null, pane);
        String id = root.id();

        if (path.length == 0) {
            String titleKey;
            if (root.type() == SnapshotType.INV) {
                titleKey = "chatitems.gui.inv.title";
            } else if (root.type() == SnapshotType.ENDER) {
                titleKey = "chatitems.gui.ender.title";
            } else {
                titleKey = "chatitems.gui.shulker.title";
            }
            String title = ServerText.translate(lang, titleKey, root.ownerName());
            int chestSize = root.type().chestSize();
            scheduling.runForPlayer(viewer, () -> {
                Inventory gui = Bukkit.createInventory(new SnapshotHolder(id, EMPTY_PATH), chestSize, title);
                for (int i = 0; i < rootSlots.size() && i < chestSize; i++) {
                    ItemStack item = rootSlots.get(i);
                    if (item != null) {
                        gui.setItem(i, extractShulkerItems(item) != null ? withInspectHint(item, inspectHint) : item.clone());
                    }
                }
                viewer.openInventory(gui);
            });
            return;
        }

        List<ItemStack> currentSlots = rootSlots;
        ItemStack currentContainer = null;
        String parentLabel = root.ownerName();
        for (int depth = 0; depth < path.length; depth++) {
            int slot = path[depth];
            int limit = depth == 0 ? currentSlots.size() : InventorySnapshot.SHULKER_SIZE;
            if (slot < 0 || slot >= limit || slot >= currentSlots.size()) {
                return;
            }
            if (depth > 0 && currentContainer != null) {
                parentLabel = itemDisplayName(currentContainer);
            }
            ItemStack candidate = currentSlots.get(slot);
            List<ItemStack> inner = extractShulkerItems(candidate);
            if (inner == null) {
                return;
            }
            currentContainer = candidate;
            currentSlots = inner;
        }

        String shulkerTitle = ServerText.translate(lang, "chatitems.gui.shulker.title", itemDisplayName(currentContainer));
        ItemStack back = backButton(
                ServerText.translate(lang, "chatitems.gui.shulker.back", parentLabel),
                ServerText.translate(lang, "chatitems.gui.back.hint"));
        List<ItemStack> chestSlots = InventorySnapshot.toShulkerWithBackSlots(currentSlots, null, pane, back);
        boolean canDrillDeeper = path.length < MAX_CONTAINER_DEPTH;

        scheduling.runForPlayer(viewer, () -> {
            Inventory gui = Bukkit.createInventory(
                    new SnapshotHolder(id, path),
                    InventorySnapshot.SHULKER_WITH_BACK_SIZE,
                    shulkerTitle);
            for (int i = 0; i < chestSlots.size() && i < InventorySnapshot.SHULKER_WITH_BACK_SIZE; i++) {
                ItemStack item = chestSlots.get(i);
                if (item != null) {
                    boolean inspectable = canDrillDeeper && i < InventorySnapshot.SHULKER_SIZE && extractShulkerItems(item) != null;
                    gui.setItem(i, inspectable ? withInspectHint(item, inspectHint) : item.clone());
                }
            }
            viewer.openInventory(gui);
        });
    }

    private static ItemStack separatorPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + " ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static ItemStack backButton(String label, String hint) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET.toString() + ChatColor.YELLOW + label);
            if (hint != null && !hint.isEmpty()) {
                meta.setLore(Collections.singletonList(ChatColor.RESET.toString() + ChatColor.GRAY + hint));
            }
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    private static ItemStack withInspectHint(ItemStack stack, String hint) {
        ItemStack copy = stack.clone();
        if (hint == null || hint.isEmpty()) {
            return copy;
        }
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<String>();
            lore.add(ChatColor.RESET.toString() + ChatColor.GOLD + hint);
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
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
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            return;
        }
        Player viewer = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        SnapshotHolder holder = (SnapshotHolder) top.getHolder();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        int[] path = holder.slotPath();
        if (path.length > 0 && rawSlot == InventorySnapshot.SHULKER_BACK_SLOT) {
            openContainerPath(viewer, holder.snapshotId(), Arrays.copyOf(path, path.length - 1));
            return;
        }
        if (path.length >= MAX_CONTAINER_DEPTH) {
            return;
        }
        if (path.length > 0 && rawSlot >= InventorySnapshot.SHULKER_SIZE) {
            return;
        }
        ItemStack clicked = top.getItem(rawSlot);
        if (extractShulkerItems(clicked) != null) {
            int[] nextPath = Arrays.copyOf(path, path.length + 1);
            nextPath[path.length] = rawSlot;
            openContainerPath(viewer, holder.snapshotId(), nextPath);
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

    private static net.md_5.bungee.api.ChatColor bungeeColor(Reply.Kind kind) {
        switch (kind) {
            case TITLE:
                return net.md_5.bungee.api.ChatColor.GOLD;
            case DONE:
                return net.md_5.bungee.api.ChatColor.GREEN;
            case ERROR:
                return net.md_5.bungee.api.ChatColor.RED;
            case HINT:
                return net.md_5.bungee.api.ChatColor.GRAY;
            default:
                return net.md_5.bungee.api.ChatColor.WHITE;
        }
    }
}
