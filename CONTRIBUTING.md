# Contributing / Участие в разработке

**[English](#english)** · **[Русский](#русский)**

---

## English

### Setup

- **JDK 25**: runs Gradle and builds the modern modules (older bands are compiled with `--release 8` / `16` / `17` / `21`).
- **JDK 21** (`LEGACY_JAVA_HOME` or `JAVA_HOME_21_X64`): runs Gradle 8 for the legacy Forge 1.20.4 and NeoForge 1.20.2 / 1.20.4 sub-builds.
- **JDK 17** (`JAVA_HOME_17_X64`): runs the Jabel javac plugin for Java 8 bytecode (`common-j8`, `fabric-1.16`, `fabric-1.17`, `forge-1.17`, `versions/forge/1.16.5`).
- Git.

```bash
git clone https://github.com/Shamanalle/chat-items.git
cd chat-items
./gradlew :common:test                 # fast: token parser, cooldowns, snapshot cache, settings, commands, translations
./gradlew build                        # every target; the three release jars land in build/libs/
./gradlew :fabric-1.21.11:runClient    # or :fabric-26, :neoforge-1.21.1, ...; runServer for a server
./gradlew :bukkit:build                # the Paper / Purpur / Folia / Spigot plugin only
```

### Where code lives

| Directory | Compiled into | Contains |
|---|---|---|
| `common/` | every jar (no Minecraft classes; Java 17 sources, built a second time as Java 8 bytecode by `common-j8`) | token parser (`ChatTokens`, `TokenType`), snapshot model & TTL cache (`InventorySnapshot`, `SnapshotCache`, `SnapshotType`), anti-spam cooldowns (`ChatCooldowns`), settings file (`ServerSettings`), `/chatitems` command handler (`AdminCommands`), server texts (`ServerText`), **13-language translations and the icon** |
| `shared/mc-all/` | every Fabric, Forge and NeoForge band | the server side on top of Minecraft: chat decoration and read-only snapshot menus (`ChatItemsServer`, `ReadOnlyChestMenu`), `/chatitems` registration (`ChatItemsCommand`), `McCompat`, `AdminPermission`, `PlayerLanguage`, `Slf4jLog` |
| `shared/mc-1.16/` … `shared/mc-26/` | the bands of those versions | the few classes that differ across Minecraft versions (`McCompat`, `ReadOnlyChestMenu`, `AdminPermission`, `PlayerLanguage`, `Slf4jLog`) |
| `shared/fabric*/`, `shared/neoforge/`, `shared/forge*/` | the bands of that loader | the loader's entrypoint and hooks; `shared/fabric*` also has the `fabric.mod.json` template |
| `shared/gametest/` | `fabric-1.21.11`, `fabric-26` (client gametest) | opens a world in a real client, tests `[item]`, `[inv]`, `[ender]`, read-only snapshot protection, and `/chatitems` in English and Russian, and captures 4K screenshots |
| `versions/<loader>/<band>/` | one band of the universal jar each; the Gradle project is `:<loader>-<band>` | the band's `build.gradle` and metadata (`mods.toml` / `neoforge.mods.toml`) |
| `universal/`, `packaging/` | the two universal mod jars | the dispatchers that pick the band of the running Minecraft at startup (Java 8, loader API only), and `MergeJars`, which builds one jar out of the band jars |
| `bukkit/` | own jar (Java 8 bytecode, 1.16.5 API + Folia Java 17 source set) | the Paper / Purpur / Folia / Spigot / Bukkit plugin (`ChatItemsBukkit`, `SnapshotHolder`, `Scheduling`, `BukkitScheduling`, `FoliaScheduling`) |

### Translations

Language files are in `common/src/main/resources/assets/chatitems/lang/`. Add every new key to **all 13** files. `LangConsistencyTest` fails if a key used in code is missing, if the languages have different keys, or if their placeholders differ. The server answers each player in their game language from the same files (`ServerText`).

### Documentation

The README is two files: `README.md` in English and `README.ru.md` in Russian. They have the same sections and are changed together. The rest of the documentation (`CHANGELOG.md`, this file, issue and pull request templates) is written in English first, followed by a complete Russian translation.

GitHub release notes are generated from `CHANGELOG.md` by `.github/scripts/release-notes.sh`. Keep `CHANGELOG.md` strictly player-facing: only what players and server owners notice, without internal build/CI/test details.

---

## Русский

### Подготовка

- **JDK 25**: запускает Gradle и собирает современные модули (старые диапазоны компилируются с `--release 8` / `16` / `17` / `21`).
- **JDK 21** (`LEGACY_JAVA_HOME` или `JAVA_HOME_21_X64`): запускает Gradle 8 для отдельных сборок Forge 1.20.4 и NeoForge 1.20.2 / 1.20.4.
- **JDK 17** (`JAVA_HOME_17_X64`): запускает плагин Jabel для компиляции в байткод Java 8 (`common-j8`, `fabric-1.16`, `fabric-1.17`, `forge-1.17`, `versions/forge/1.16.5`).
- Git.

```bash
git clone https://github.com/Shamanalle/chat-items.git
cd chat-items
./gradlew :common:test                 # быстро: парсер токенов, кулдауны, кэш снимков, настройки, команды, переводы
./gradlew build                        # все версии; три релизных JAR в build/libs/
./gradlew :fabric-1.21.11:runClient    # или :fabric-26, :neoforge-1.21.1, ...; runServer для сервера
./gradlew :bukkit:build                # только плагин для Paper / Purpur / Folia / Spigot
```

### Где какой код

| Папка | Куда попадает | Что внутри |
|---|---|---|
| `common/` | во все JAR (без классов Minecraft; исходники на Java 17, `common-j8` собирает их ещё раз в байткод Java 8) | парсер токенов (`ChatTokens`, `TokenType`), модель и кэш снимков с TTL (`InventorySnapshot`, `SnapshotCache`, `SnapshotType`), антиспам-кулдауны (`ChatCooldowns`), файл настроек (`ServerSettings`), обработчик `/chatitems` (`AdminCommands`), тексты сервера (`ServerText`), **переводы на 13 языков и иконка** |
| `shared/mc-all/` | во все диапазоны Fabric, Forge и NeoForge | серверная часть поверх Minecraft: подстановка тегов в чат и read-only меню снимков (`ChatItemsServer`, `ReadOnlyChestMenu`), регистрация `/chatitems` (`ChatItemsCommand`), `McCompat`, `AdminPermission`, `PlayerLanguage`, `Slf4jLog` |
| `shared/mc-1.16/` … `shared/mc-26/` | в диапазоны этих версий | классы, отличающиеся между версиями Minecraft (`McCompat`, `ReadOnlyChestMenu`, `AdminPermission`, `PlayerLanguage`, `Slf4jLog`) |
| `shared/fabric*/`, `shared/neoforge/`, `shared/forge*/` | в диапазоны этого загрузчика | точка входа и хуки загрузчика; в `shared/fabric*` также шаблон `fabric.mod.json` |
| `shared/gametest/` | `fabric-1.21.11`, `fabric-26` (client gametest) | открывает мир в настоящем клиенте, проверяет `[item]`, `[inv]`, `[ender]`, защиту read-only окон и `/chatitems` на английском и русском и делает 4K-скриншоты |
| `versions/<загрузчик>/<диапазон>/` | по одному диапазону универсального JAR; проект Gradle — `:<загрузчик>-<диапазон>` | `build.gradle` диапазона и метаданные (`mods.toml` / `neoforge.mods.toml`) |
| `universal/`, `packaging/` | в два универсальных JAR мода | диспетчеры, выбирающие диапазон при запуске (Java 8), и `MergeJars`, собирающий единый JAR |
| `bukkit/` | свой JAR (байткод Java 8, API 1.16.5 + набор исходников Folia на Java 17) | плагин для Paper / Purpur / Folia / Spigot / Bukkit (`ChatItemsBukkit`, `SnapshotHolder`, `Scheduling`, `BukkitScheduling`, `FoliaScheduling`) |

### Переводы

Файлы языков лежат в `common/src/main/resources/assets/chatitems/lang/`. Каждый новый ключ добавляйте во **все 13** файлов. `LangConsistencyTest` проверяет наличие всех используемых в коде ключей и совпадение плейсхолдеров.

### Документация

README состоит из двух файлов: `README.md` на английском и `README.ru.md` на русском. Остальная документация (`CHANGELOG.md`, этот файл, шаблоны issue и pull request) пишется сначала на английском, затем полностью переводится на русский. В `CHANGELOG.md` описываются только заметные игрокам и администраторам серверов изменения.
