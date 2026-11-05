# Development Plan / План разработки

## Stage 1 — Skeleton, Token Parser, and 3-JAR Build
- `CLAUDE.md` with repository rules.
- `common/` & `common-j8/` (Java 8 bytecode via Jabel):
  - Token parser (`[item]`, `[i]`, `[hand]`, `[inv]`, `[ender]`) with per-message token limit (`max_tokens_per_message`).
  - Per-player cooldown tracker (`ChatCooldowns`).
  - Immutable inventory & Ender chest snapshot cache with TTL (`InventorySnapshot`, `SnapshotCache`).
  - Hot-reloading `.properties` configuration (`SettingsFile`, `ServerSettings`).
  - `/chatitems` command handler (`status`, `reload`, `view <id>`, setting toggles/limits, `help`).
  - 13-language localization (`en_us`, `ru_ru`, `uk_ua`, `de_de`, `fr_fr`, `es_es`, `pt_br`, `pl_pl`, `it_it`, `tr_tr`, `zh_cn`, `ja_jp`, `ko_kr`) verified by `LangConsistencyTest`.
- `universal/` & `packaging/` (`MergeJars`) producing 3 release JARs (`chat-items-fabric`, `chat-items-forge-neoforge`, `chat-items-bukkit`).

## Stage 2 — MVP 0.1.0: Server-Side Chat Items & Read-Only Snapshots + CI
- Native `HoverEvent` (`show_item`) substitution for `[item]` with stack size, rarity color, custom name, enchantments, durability, lore, and components/NBT.
- Immutable `[inv]` (6-row chest with armor, off-hand, main inventory, hotbar) and `[ender]` (3-row chest) snapshots opened via `ClickEvent` (`run_command` -> `/chatitems view <id>`) in a strictly read-only container (`ReadOnlyChestMenu` on Fabric/Forge/NeoForge, `InventoryHolder` + event cancellation on Bukkit/Paper/Folia).
- Full CI suite (`build.yml`, `compat.yml`, `compat-neoforge.yml`, `ingame.yml` with server smoke tests and client GameTest with EN/RU + 4K store screenshots, `release.yml`, `publish.yml`, `modrinth-page.yml`, `sync-release-notes.yml`, `repo-about.yml`).

## Stage 3 — Full 1.16.5 – 26.3 Version Range & Store Pages
- Expand Fabric/Quilt, Forge, and NeoForge bands across the entire 1.16.5 – 26.3 range (handling NBT vs DataComponents in 1.20.5+, HoverEvent/ClickEvent record API in 1.21.5+, ItemStackTemplate & ContainerInput in 26.x, and permission sets in 1.21.11+).
- Pixel-art icon generator (`docs/make-icon.py`), bilingual documentation (`README.md`, `README.ru.md`, `CONTRIBUTING.md`, `CHANGELOG.md`), and Modrinth/CurseForge store pages (`docs/modrinth.md`, `docs/store-description.md`, `docs/gallery.json`).
