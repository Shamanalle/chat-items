# Store pages / Страницы на площадках

The Modrinth project page is `docs/modrinth.md`: the whole page, in English, with the Russian translation collapsed in a `<details>` block at the end. It is short and tells players what they get; commands and details belong in the README. The *Modrinth page* workflow uploads the page and the summary below to Modrinth on every change pushed to `main` (it needs the `MODRINTH_TOKEN` secret with the *Write projects* scope). CurseForge cannot be updated this way: paste the same text into the description editor by hand. The gallery pictures are listed in `docs/gallery.json` (file, title, description, featured; the list's order is their order) and the same workflow puts them into the Modrinth gallery, replacing the picture with the same title. The pictures are 4K (3840×2160) screenshots from the in-game client test (Minecraft 26.3). Each picture must stay under 5 MiB. The page shows the first picture through its raw GitHub link, so the text pasted into CurseForge brings it along.

Страница проекта на Modrinth — это `docs/modrinth.md`: вся страница на английском, а русский перевод свёрнут в блок `<details>` в конце. Она короткая и рассказывает игрокам, что они получат; команды и подробности — в README. Workflow *Modrinth page* выкладывает страницу и краткое описание ниже на Modrinth при каждом изменении в `main` (нужен секрет `MODRINTH_TOKEN` с правом *Write projects*). CurseForge так обновить нельзя: вставьте тот же текст в редактор описания вручную. Картинки галереи перечислены в `docs/gallery.json` (файл, заголовок, описание, featured; порядок списка — порядок картинок), тот же workflow кладёт их в галерею Modrinth, заменяя картинку с тем же заголовком. Картинки — 4K (3840×2160) скриншоты из клиентского игрового теста (Minecraft 26.3). Каждая картинка должна быть меньше 5 МиБ. Первую картинку страница показывает по прямой ссылке на GitHub, так что текст, вставленный в CurseForge, приносит её с собой.

---

## English

**Name:** Chat Items: Show Items, Inventory & Ender Chest in Chat

**Summary:** Type [item], [armor], [inv] or [ender] in chat to show items with tooltips, shulker boxes and read-only inventory snapshots. Server-side, vanilla clients welcome. Forge, NeoForge, Fabric, Paper. 1.16.5 - 26.3.

**Categories:** Utility, Social, Server Utility

**Environment:** server-side (Modrinth: *Client: optional, Server: required*).

**Dependencies:** Fabric API (required on Fabric / Quilt).

**Plugin:** the Paper / Purpur / Folia / Spigot plugin is a version of the same Modrinth project (loaders Paper, Folia, Spigot, Bukkit, Purpur). CurseForge keeps Bukkit plugins in a separate project (`curseforge_plugin` in `.github/publish.json`).

**License:** MIT.

**Links:** source `https://github.com/Shamanalle/chat-items`, issues `https://github.com/Shamanalle/chat-items/issues`.

**Icon:** `docs/images/icon.png` (512×512).

## Русский

**Название:** Chat Items: Show Items, Inventory & Ender Chest in Chat

**Краткое описание** (на площадках только на английском, как выше). Перевод: Напишите [item], [armor], [inv] или [ender] в чат, чтобы показать предметы с подсказками, шалкеровые ящики и снимки инвентаря только для просмотра. Серверный мод и плагин, подходит для ванильных клиентов. Forge, NeoForge, Fabric, Paper. 1.16.5 - 26.3.

**Категории:** Utility, Social, Server Utility

**Окружение:** серверное (Modrinth: *Client: optional, Server: required*).

**Зависимости:** Fabric API (обязателен на Fabric / Quilt).

**Плагин:** плагин для Paper / Purpur / Folia / Spigot — версия того же проекта на Modrinth (загрузчики Paper, Folia, Spigot, Bukkit, Purpur). На CurseForge плагин загружается в отдельный проект Bukkit Plugins (`curseforge_plugin` в `.github/publish.json`).

**Лицензия:** MIT.

**Ссылки:** исходники `https://github.com/Shamanalle/chat-items`, issues `https://github.com/Shamanalle/chat-items/issues`.

**Иконка:** `docs/images/icon.png` (512×512).
