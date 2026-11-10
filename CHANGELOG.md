# Changelog

All notable changes to this project are documented in this file. Each version is written in English
first, then in Russian. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/).

Все заметные изменения проекта записываются в этот файл. Каждая версия описана сначала на английском,
затем на русском.

## [1.1.0] - 2026-04-19

### English

#### Added
- Shulker Box & Bundle inspection: clicking a Shulker Box or Bundle (`1.17+`) inside a shared `[inv]` or `[ender]` window opens its contents in a read-only view with a `← Back` button to return to the inventory or Ender chest. Sharing a Shulker Box or Bundle in chat with `[item]`, `[offhand]`, or `[slot1]`–`[slot9]` also makes the chat tag clickable so anyone can open and inspect the items inside.
- Quick slot and equipment chat tags:
  - `[offhand]` (or `[off]`) shows the item in your off-hand.
  - `[armor]` (or `[armour]`) shows all worn armor pieces inline in chat, each with its own rarity color and full hover tooltip.
  - `[slot1]` through `[slot9]` (or `[s1]` – `[s9]`) shows the item in hotbar slot 1–9 without switching your held item.
- Granular permissions for Paper, Purpur, Folia, and Spigot servers (compatible with LuckPerms): `chatitems.item`, `chatitems.inv`, `chatitems.ender` (enabled for everyone by default) and `chatitems.bypass.cooldown` (for VIPs, moderators, and admins).

### Русский

#### Добавлено
- Просмотр шалкеровых ящиков и мешочков (Bundles `1.17+`): клик по шалкеру или мешочку внутри открытого окна `[inv]` или `[ender]` открывает его содержимое в режиме только для просмотра с кнопкой `← Назад` для возврата к инвентарю или эндер-сундуку. Если показать шалкер или мешочек в чате через `[item]`, `[offhand]` или `[slot1]`–`[slot9]`, тег в чате также становится кликабельным и позволяет любому игроку открыть и посмотреть предметы внутри.
- Быстрые теги слотов и снаряжения в чате:
  - `[offhand]` (или `[off]`) показывает предмет во второй руке.
  - `[armor]` (или `[armour]`) выводит в строку все надетые элементы брони, каждый со своим цветом редкости и полной всплывающей подсказкой.
  - `[slot1]` – `[slot9]` (или `[s1]` – `[s9]`) показывает предмет из нужного слота хотбара (1–9), не переключая предмет в руке.
- Детальные права (permissions) для серверов Paper, Purpur, Folia и Spigot (совместимы с LuckPerms): `chatitems.item`, `chatitems.inv`, `chatitems.ender` (по умолчанию доступны всем) и `chatitems.bypass.cooldown` (обход кулдауна для VIP, модераторов и администраторов).

## [0.1.0] - 2026-04-19

### English

#### Added
- First release for Fabric and Quilt (Minecraft 1.16.5 – 26.3), Forge (1.16.5 – 26.3), NeoForge (1.20.1 – 26.3), and Paper, Purpur, Folia, and Spigot servers (1.16.5 – 26.3). Works entirely on the server; players with vanilla clients see everything without installing the mod.
- Type `[item]` (or `[i]`, `[hand]`) in chat to show the item in your hand with its custom name, stack size, rarity color, and full tooltip (enchantments, durability, lore, and components) when hovered.
- Type `[inv]` or `[ender]` in chat to share a clickable link that opens a read-only snapshot of your inventory (including armor and off-hand) or Ender chest for anyone who clicks it. Items inside the snapshot cannot be taken, moved, or duplicated.
- Per-message token limit and anti-spam cooldown so chat cannot be flooded with links.
- `/chatitems` shows whether each token is enabled, the limits, and how many snapshots are currently open; `/chatitems reload` reloads the settings file, and operators can toggle tokens or change limits in game.
- Translations in 13 languages (`en_us`, `ru_ru`, `uk_ua`, `de_de`, `fr_fr`, `es_es`, `pt_br`, `pl_pl`, `it_it`, `tr_tr`, `zh_cn`, `ja_jp`, `ko_kr`), with replies sent in each player's game language.

### Русский

#### Добавлено
- Первый релиз для Fabric и Quilt (Minecraft 1.16.5 – 26.3), Forge (1.16.5 – 26.3), NeoForge (1.20.1 – 26.3) и серверов Paper, Purpur, Folia и Spigot (1.16.5 – 26.3). Работает полностью на стороне сервера: игроки с обычным клиентом без мода видят всё.
- Напишите `[item]` (или `[i]`, `[hand]`) в чат, чтобы показать предмет в руке с его названием, количеством, цветом редкости и полной подсказкой (зачарования, прочность, описание и свойства) при наведении курсора.
- Напишите `[inv]` или `[ender]` в чат, чтобы вставить кликабельную ссылку, открывающую любому нажавшему игроку снимок вашего инвентаря (вместе с бронёй и второй рукой) или эндер-сундука только для просмотра. Забрать, переложить или дюпнуть предметы из окна снимка нельзя.
- Ограничение числа тегов в одном сообщении и задержка между отправками для защиты чата от спама.
- `/chatitems` показывает, какие теги включены, текущие ограничения и сколько снимков сейчас хранится; `/chatitems reload` перечитывает файл настроек, а операторы могут переключать теги и менять лимиты прямо из игры.
- Перевод на 13 языков (`en_us`, `ru_ru`, `uk_ua`, `de_de`, `fr_fr`, `es_es`, `pt_br`, `pl_pl`, `it_it`, `tr_tr`, `zh_cn`, `ja_jp`, `ko_kr`); ответы приходят каждому игроку на языке его игры.
