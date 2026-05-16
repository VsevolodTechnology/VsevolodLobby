# VsevolodLobby — описание проекта

## Назначение

Лобби-сервер для проекта **OVERDYN** (бывш. HOTWORLD), написанный на **Minestom** (Java).
Игроки попадают сюда после авторизации и через NPC / меню / команду компаса переходят на игровые режимы (parkour, grief и т.п.).
Старые версии клиентов поддерживаются через Velocity-прокси с ViaVersion+ViaBackwards плагинами.

## Технологический стек

| Слой | Технология |
|---|---|
| Платформа | **Minestom 2026.03.25-1.21.11** (custom MC-сервер, не Paper/Spigot) |
| JDK | **Java 25** (toolchain в `build.gradle.kts`) |
| Сборка | Gradle 9.x + Shadow plugin → один fat-jar |
| Хранение | MongoDB (`mongodb-driver-sync 5.2.1`) — leaderboard паркура (опционально) |
| Профилировщик | Spark (`dev.lu15:spark-minestom`) |
| Прокси | **Velocity 3.4.x** (отдельный процесс) + ViaVersion/ViaBackwards плагины |
| Транспорт | Modern forwarding (общий secret) между Velocity и Minestom |

## Архитектура

```
Клиент (1.13 .. 1.21.x)
    ↓
Velocity (порт 25565, публичный) — ViaVersion+ViaBackwards переводят протокол
    ↓ modern forwarding + GameProfile.Property("vsevolod_lobby_protocol", "<orig-proto>")
Minestom (порт 25566, локальный) — этот проект
```

- Минестом всегда видит протокол 1.21.11 от Velocity, но знает ОРИГИНАЛЬНУЮ версию клиента через property
- `LobbyPlayerProvider` читает property и кладёт значение в тег `IDENTIFIER_CLIENT_PROTOCOL` на игроке
- `ClientCompat` отвечает на вопросы «поддерживает ли клиент text_display / transfer / …»

## Структура исходников

```
src/main/java/ua/vsevolod/lobby/
├── bootstrap/                — точка входа, регистрация модулей
│   ├── Main.java
│   ├── server/               — ServerBootstrap, ModuleLoader, HandshakeOverride, ProxyOnlineService
│   └── module/               — Spark, Command, Instance, Lobby (модули-инициализаторы)
├── command/
│   ├── admin/                — /op /deop /stop /gamemode /tpsbar /rambar /version
│   └── lobby/                — /spawn
├── config/
│   ├── LobbyConfig.java      — статические константы (цвета, локации, BYPASS_USERS, тэги)
│   └── ProxyConfig.java      — runtime-настройки прокси из proxy.properties
├── feature/
│   ├── admin/                — OpsStore, StatsBarService, VersionGate
│   ├── lobby/
│   │   ├── audio/            — музыка лобби
│   │   ├── bootstrap/        — LobbyEventRegistrar, LobbyEventRegistration
│   │   ├── interaction/npc/  — NPC класс + LobbyNpcService + NPC-listeners
│   │   ├── player/           — Provider/инициализация/чат/protection/compat
│   │   ├── ui/
│   │   │   ├── hologram/     — TextHologram (text_display + armor_stand fallback)
│   │   │   ├── menu/         — LobbyModeSelectorMenu (мод-выбор)
│   │   │   ├── sidebar/      — LobbySidebar (скорборд)
│   │   │   ├── tab/          — LobbyTabListManager
│   │   │   └── (item/ внутри lobby package) — LobbyItemService, LobbyMenuItemFactory
│   │   └── world/            — protection листенеры
│   └── parkour/              — парк-режим, leaderboard
└── integration/
    ├── console/              — ShutdownHook, ConsoleListener
    ├── spark/                — SparkService
    └── (mongodb, mojang skin)
```

## Runtime файлы (не в git)

| Файл | Кем создаётся | Назначение |
|---|---|---|
| `proxy.properties` | Автогенерится при первом старте | Velocity secret + bind адрес/порт |
| `storage/ops.txt` | `OpsStore.save()` | Список ников, кому выдали права через `/op` |
| `storage/version_gate.txt` | `VersionGate.save()` | Текущие лимиты протокола (на будущее) |
| `storage/parkour/leaderboard.tsv` | `ParkourFileLeaderboardStore` | Топ паркура (если MongoDB не настроена) |
| `via/` | ViaProxy / ViaBackwards | Кеш мэппингов протокола (legacy) |
| `worlds/lobby/` | ручная загрузка | Карта лобби (`.dat` + слайсы) |

## Существующие подсистемы (текущее состояние)

| Подсистема | Где живёт | Состояние |
|---|---|---|
| **Tab list** | `feature/lobby/ui/tab/LobbyTabListManager` | Хардкод: текст + цвета в коде, обновление каждые 100 мс |
| **Sidebar (scoreboard)** | `feature/lobby/ui/sidebar/LobbySidebar` | Хардкод: 15 строк со score 15→0, анимация заголовка из `LobbyConfig.Animation.TITLE` |
| **NPCs** | `feature/lobby/interaction/npc/*` + `LobbyModule` | Хардкод: два NPC (мод-селектор, парк-NPC) создаются в `LobbyModule.load()` |
| **Items на join** | `LobbyItemService` + `LobbyMenuItemFactory` | Хардкод: компас в слоте 4, music-toggle в слоте 8, QR-карта |
| **Mode-selector меню** | `feature/lobby/ui/menu/LobbyModeSelectorMenu` | Хардкод: 5 рядов chest, декор + 3 серверных слота, ссылки на `ServerRegistry` |
| **Голограммы** | `feature/lobby/ui/hologram/TextHologram*` | Динамически создаются в `LobbyModule.load()`, поддерживается fallback на armor_stand |
| **Команды** | `command/admin/*`, `command/lobby/*` | Регистрируются вручную в `CommandModule.load()`, owner-check через `BYPASS_USERS` или `OPS_OWNER` |
| **Музыка** | `feature/lobby/audio/music/LobbyMusicManager` | Toggle через инвентарный предмет |

## Доменные понятия

- **godes2020** — owner проекта (хардкод в `LobbyConfig.Settings.OPS_OWNER`)
- **BYPASS_USERS** — список «операторов» (мутируется через `/op` и `/deop`, сохраняется в `storage/ops.txt`)
- **OPS_OWNER** — единственный, кто может выдавать/снимать op
- Permission level 4 даётся при join игрокам из BYPASS_USERS (`LobbyJoinInitializer.setupProfile`)

## Текущие конфиги в коде

`LobbyConfig.java` содержит:
- `Project`: имя, соц-ссылки, цвета
- `Settings`: world path, host, bypass users, ops owner, gamemode, identifiers
- `Messages`: welcome MSG, shutting-down MSG
- `Commands`: имена + алиасы команд spawn/gamemode
- `Locations`: точки спавна, NPC, void-threshold
- `Parkour`: NPC pos, skin, leaderboard config
- `Lobby`, `Sections`, `Animation`: тексты + анимация

Всё это **константы в Java** — менять без пересборки нельзя. Цель проекта — вынести **большую часть** в YAML с hot-reload через команду.

## Внешние зависимости и порты

- Velocity слушает `0.0.0.0:25565` (внешний)
- Minestom слушает `127.0.0.1:25566` (закрыт от прямых коннектов)
- Spark UI — внутренний
- MongoDB — `mongodb://127.0.0.1:27017` (если включён леaderboard MONGO mode)

## Команды (на момент написания)

Доступ — только `godes2020` или один из `BYPASS_USERS`:

| Команда | Аргументы | Действие |
|---|---|---|
| `/op <ник>` | owner-only | выдать op (попадает в BYPASS_USERS, лвл 4) |
| `/deop <ник>` | owner-only | снять op |
| `/stop` | bypass | остановить сервер |
| `/gamemode (gm)` | bypass | сменить режим себе |
| `/tpsbar` | bypass | bossbar TPS/MSPT/Ping |
| `/rambar` | bypass | bossbar RAM |
| `/version on\|off\|min\|max <N>` | owner | гейт по протоколу |
| `/spawn (hub/lobby/…)` | все | телепорт на спавн |

## Что НЕ реализовано (открытые направления)

- Конфиги для tab/sidebar/NPC/items/menu (см. ROADMAP.md)
- Команды редактирования NPC/menu в игре
- Hot-reload конфигов
- Per-NPC «как player / как op» режим выполнения команд
- Permission-flag на меню (ops_only / public)
- Полноценный sub-server transfer через Velocity plugin messages

См. **ROADMAP.md** для детального плана работ по фазам.
