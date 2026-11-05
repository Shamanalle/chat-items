# Changelog

All notable changes to this project are documented in this file. Each version is written in English
first, then in Russian. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/).

Все заметные изменения проекта записываются в этот файл. Каждая версия описана сначала на английском,
затем на русском.

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
