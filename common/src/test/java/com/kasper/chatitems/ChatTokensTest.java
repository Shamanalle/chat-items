package com.kasper.chatitems;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatTokensTest {

    @Test
    void parsesItemInvAndEnderWithAliases() {
        ServerSettings s = new ServerSettings(null);
        s.setMaxTokens(10);
        ChatTokens.ParseResult res = ChatTokens.parse(
                "Look at my [item], [i], [hand], my [inv] ([inventory]) and [ender] ([enderchest], [ec])!", s);
        assertTrue(res.hasTokens());
        assertTrue(res.hasSnapshotTokens());
        assertFalse(res.limitReached());
        assertEquals(8, res.tokenCount());

        List<TokenType> types = res.segments().stream()
                .filter(ChatTokens.Segment::isToken)
                .map(ChatTokens.Segment::tokenType)
                .toList();
        assertEquals(List.of(
                TokenType.ITEM, TokenType.ITEM, TokenType.ITEM,
                TokenType.INV, TokenType.INV,
                TokenType.ENDER, TokenType.ENDER, TokenType.ENDER
        ), types);
    }

    @Test
    void matchesTokensCaseInsensitively() {
        ServerSettings s = new ServerSettings(null);
        ChatTokens.ParseResult res = ChatTokens.parse("Selling [ITEM] from [Inv] or [ENDER]", s);
        assertEquals(3, res.tokenCount());
        assertEquals(TokenType.ITEM, res.segments().get(1).tokenType());
        assertEquals(TokenType.INV, res.segments().get(3).tokenType());
        assertEquals(TokenType.ENDER, res.segments().get(5).tokenType());
    }

    @Test
    void enforcesMaxTokensPerMessage() {
        ServerSettings s = new ServerSettings(null);
        s.setMaxTokens(2);
        ChatTokens.ParseResult res = ChatTokens.parse("[item] one [inv] two [ender] three [i]", s);
        assertEquals(2, res.tokenCount());
        assertTrue(res.limitReached());
        assertEquals(4, res.segments().size());
        assertEquals(TokenType.ITEM, res.segments().get(0).tokenType());
        assertEquals(" one ", res.segments().get(1).text());
        assertEquals(TokenType.INV, res.segments().get(2).tokenType());
        assertEquals(" two [ender] three [i]", res.segments().get(3).text());
    }

    @Test
    void supportsBackslashEscaping() {
        ServerSettings s = new ServerSettings(null);
        ChatTokens.ParseResult res = ChatTokens.parse("Type \\[item] to show [item]", s);
        assertEquals(1, res.tokenCount());
        assertFalse(res.hasSnapshotTokens());
        assertEquals(2, res.segments().size());
        assertFalse(res.segments().get(0).isToken());
        assertEquals("Type [item] to show ", res.segments().get(0).text());
        assertTrue(res.segments().get(1).isToken());
        assertEquals(TokenType.ITEM, res.segments().get(1).tokenType());
    }

    @Test
    void respectsMasterAndPerTokenSwitches() {
        ServerSettings s = new ServerSettings(null);
        s.setEnabled(false);
        ChatTokens.ParseResult disabled = ChatTokens.parse("My [item] and [inv]", s);
        assertFalse(disabled.hasTokens());
        assertEquals(1, disabled.segments().size());
        assertEquals("My [item] and [inv]", disabled.segments().get(0).text());

        s.setEnabled(true);
        s.setAllowInv(false);
        ChatTokens.ParseResult noInv = ChatTokens.parse("My [item] and [inv] and [ender]", s);
        assertEquals(2, noInv.tokenCount());
        assertEquals(TokenType.ITEM, noInv.segments().get(1).tokenType());
        assertEquals(" and [inv] and ", noInv.segments().get(2).text());
        assertEquals(TokenType.ENDER, noInv.segments().get(3).tokenType());
    }

    @Test
    void parsesOffhandArmorAndHotbarSlotTags() {
        ServerSettings s = new ServerSettings(null);
        s.setMaxTokens(20);
        ChatTokens.ParseResult res = ChatTokens.parse(
                "[offhand] [off] [armor] [armour] [slot1] [s2] [slot9] [s9]", s);
        List<TokenType> types = res.segments().stream()
                .filter(ChatTokens.Segment::isToken)
                .map(ChatTokens.Segment::tokenType)
                .toList();
        assertEquals(List.of(
                TokenType.OFFHAND, TokenType.OFFHAND,
                TokenType.ARMOR, TokenType.ARMOR,
                TokenType.SLOT_1, TokenType.SLOT_2,
                TokenType.SLOT_9, TokenType.SLOT_9
        ), types);
        assertEquals(0, TokenType.SLOT_1.hotbarIndex());
        assertEquals(1, TokenType.SLOT_2.hotbarIndex());
        assertEquals(8, TokenType.SLOT_9.hotbarIndex());
        assertEquals(-1, TokenType.ITEM.hotbarIndex());
        assertTrue(TokenType.OFFHAND.isItemLike());
        assertTrue(TokenType.ARMOR.isItemLike());
        assertTrue(TokenType.SLOT_5.isItemLike());
        assertFalse(TokenType.INV.isItemLike());
        assertFalse(TokenType.ENDER.isItemLike());
    }

    @Test
    void respectsPermissionFilterPredicate() {
        ServerSettings s = new ServerSettings(null);
        ChatTokens.ParseResult res = ChatTokens.parse(
                "Check [item], [inv] and [ender]", s, type -> type != TokenType.ENDER);
        assertEquals(2, res.tokenCount());
        List<TokenType> types = res.segments().stream()
                .filter(ChatTokens.Segment::isToken)
                .map(ChatTokens.Segment::tokenType)
                .toList();
        assertEquals(List.of(TokenType.ITEM, TokenType.INV), types);
    }
}
