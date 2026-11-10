package com.kasper.chatitems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsAndCommandsTest {

    @TempDir
    Path dir;

    @Test
    void savesLoadsAndHotReloadsPropertiesFile() throws IOException {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        ServerSettings s = new ServerSettings(file);
        s.load();
        assertTrue(Files.exists(file));
        assertFalse(s.reloadIfChanged());

        String edited = "enabled=true\nallow_item=true\nallow_inv=false\nallow_ender=true\n"
                + "max_tokens=5\ncooldown_seconds=10\nsnapshot_ttl_seconds=120\nlanguage=ru_ru\n";
        Files.writeString(file, edited, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000L));

        assertTrue(s.reloadIfChanged());
        assertFalse(s.allowInv());
        assertEquals(5, s.maxTokens());
        assertEquals(10, s.cooldownSeconds());
        assertEquals(120, s.snapshotTtlSeconds());
        assertEquals("ru_ru", s.language());
    }

    @Test
    void executesAdminCommandsInEnglishAndRussian() {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        ChatItems.init(file, "test");

        AdminCommands.Context en = new AdminCommands.Context() {
            @Override
            public String language() {
                return "en_us";
            }

            @Override
            public int activeSnapshots() {
                return 2;
            }
        };
        AdminCommands.Context ru = new AdminCommands.Context() {
            @Override
            public String language() {
                return "ru_ru";
            }

            @Override
            public int activeSnapshots() {
                return 2;
            }
        };

        Reply status = AdminCommands.execute("status", en);
        assertFalse(status.refused());
        assertTrue(status.toString().contains("Chat Items"));
        assertTrue(status.toString().contains("Active snapshots in cache: 2"));

        Reply ruStatus = AdminCommands.execute("", ru);
        assertFalse(ruStatus.refused());
        assertTrue(ruStatus.toString().contains("Активных снимков в кэше: 2"));

        assertFalse(AdminCommands.execute("inv off", en).refused());
        assertFalse(ChatItems.settings().allowInv());

        assertFalse(AdminCommands.execute("max_tokens 6", en).refused());
        assertEquals(6, ChatItems.settings().maxTokens());

        assertFalse(AdminCommands.execute("cooldown 5", en).refused());
        assertEquals(5, ChatItems.settings().cooldownSeconds());

        assertFalse(AdminCommands.execute("ttl 180", en).refused());
        assertEquals(180, ChatItems.settings().snapshotTtlSeconds());

        assertTrue(AdminCommands.execute("max_tokens 99", en).refused());
        assertTrue(AdminCommands.execute("statu", en).toString().contains("/chatitems status"));

        List<String> rootSuggestions = AdminCommands.suggestions("");
        assertTrue(rootSuggestions.contains("status"));
        assertTrue(rootSuggestions.contains("reload"));
        assertEquals(List.of("on", "off"), AdminCommands.suggestions("item "));
        assertEquals(List.of("1", "3", "5", "10", "16"), AdminCommands.suggestions("max_tokens "));

        assertEquals(List.of("status", "help"), AdminCommands.suggestions("", false));
        assertEquals(List.of("status"), AdminCommands.suggestions("st", false));
        assertEquals(List.of(), AdminCommands.suggestions("item ", false));

        assertEquals("/chatitems status", AdminCommands.suggestedCommand("/chatitems status — show current settings"));
        assertEquals("/chatitems cooldown ", AdminCommands.suggestedCommand("/chatitems cooldown <0-300> — per-player cooldown"));
        assertEquals("/chatitems reload", AdminCommands.suggestedCommand("Did you mean: /chatitems reload?"));
        assertNull(AdminCommands.suggestedCommand("Chat tokens: enabled"));
    }

    @Test
    void preservesCustomCommentsAndReadsLegacyMaxTokensAlias() throws IOException {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        String customContent = "# My custom server header\n"
                + "enabled=true\n"
                + "max_tokens_per_message=7\n"
                + "custom_future_option=keep_me\n";
        Files.writeString(file, customContent, StandardCharsets.UTF_8);

        ServerSettings s = new ServerSettings(file);
        s.load();
        assertEquals(7, s.maxTokens());

        s.setCooldownSeconds(12);
        s.save();

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.contains("# My custom server header"));
        assertTrue(saved.contains("custom_future_option=keep_me"));
        assertTrue(saved.contains("max_tokens=7"));
        assertFalse(saved.contains("max_tokens_per_message="));
        assertTrue(saved.contains("cooldown_seconds=12"));
    }
}
