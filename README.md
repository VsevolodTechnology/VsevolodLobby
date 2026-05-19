# VsevolodLobby

Лобби-сервер для проекта **OVERDYN**, написанный на **Minestom** (Java 25).  
Игроки попадают сюда после авторизации и через NPC / меню / компас переходят на игровые режимы.

---

## Стек

| Слой | Технология |
|---|---|
| Платформа | **Minestom 2026.03.25-1.21.11** (не Paper/Spigot — кастомный MC-сервер) |
| JDK | **Java 25** |
| Сборка | Gradle 9.x + Shadow plugin → fat-jar |
| Хранение | MongoDB (`mongodb-driver-sync 5.2.1`) — leaderboard паркура (опционально) |
| Профилировщик | Spark (`dev.lu15:spark-minestom`) |
| Прокси | **Velocity 3.4.x** + ViaVersion/ViaBackwards (поддержка клиентов 1.13–1.21.x) |

---

## Сборка и деплой

```bash
# Сборка
./gradlew shadowJar
# Артефакт: build/libs/VsevolodLobby-1.0.0-all.jar

# Деплой на сервер лобби (SFTP)
# Host: sunrise.bubble.wtf:2025  User: overdynmc1.eb37efe9  Pass: Seva1112131415
# Target: /server.jar  (это ядро, не плагин)
sshpass -p 'Seva1112131415' sftp -P 2025 overdynmc1.eb37efe9@sunrise.bubble.wtf <<'EOF'
put build/libs/VsevolodLobby-1.0.0-all.jar /server.jar
EOF

# Перезапуск через RCON
# Host: sunrise.bubble.wtf:50011  Pass: Pizda!2026-2056NewYear
```

---

## Архитектура

```
Клиент (1.13–1.21.x)
    ↓
Velocity :25565 (публичный) — ViaVersion/ViaBackwards
    ↓ modern forwarding
Minestom :25566 (локальный) — этот проект
```

- Velocity пробрасывает оригинальную версию клиента через `GameProfile.Property("vsevolod_lobby_protocol")`
- `LobbyPlayerProvider` читает property → тег `IDENTIFIER_CLIENT_PROTOCOL` на Player
- `ClientCompat` отвечает: «поддерживает ли клиент text_display / transfer / …»
- Compression: `-1` (Velocity сжимает сам, не нужно на бэкенде)

---

## Структура пакетов

```
ua.vsevolod.lobby/
├── bootstrap/            — точка входа, ModuleLoader, ServerBootstrap
│   └── module/           — SparkModule, CommandModule, InstanceModule, LobbyModule
├── command/
│   ├── admin/            — /op /deop /stop /gamemode /tpsbar /rambar /version /reload /npc /menu
│   └── lobby/            — /spawn
├── config/               — LobbyConfig (константы), ProxyConfig, ConfigManager
├── feature/
│   ├── admin/            — OpsStore, StatsBarService, CommandsReferenceWriter
│   ├── lobby/
│   │   ├── audio/music/  — LobbyMusicManager, LobbyMusicSelectorMenu
│   │   ├── interaction/npc/ — NpcManager, NpcActionExecutor, LobbyNpcInteractionListener
│   │   ├── player/
│   │   │   ├── join/     — LobbyJoinInitializer, LobbyItemService, JoinItemManager
│   │   │   ├── prefs/    — PlayerPreferences, PlayerPreferencesService (MongoDB)
│   │   │   ├── visibility/ — PlayerHider
│   │   │   └── behavior/ — конфиг поведения игрока
│   │   └── ui/
│   │       ├── hologram/ — HologramManager, TextHologramEntry
│   │       ├── menu/     — LobbyModeSelectorMenu, LobbySettingsMenu, MenuManager
│   │       ├── sidebar/  — LobbySidebar, SidebarToggle, PerViewerSidebar
│   │       └── tab/      — LobbyTabListManager
│   └── parkour/          — ParkourService, ParkourSession, ParkourSettingsMenu, leaderboard
└── integration/          — console, spark, mongodb, mojang skin
```

---

## Конфиги (config/*.yml, hot-reload через /reload)

| Файл | Подсистема |
|---|---|
| `config/tab.yml` | Tab list — header/footer, плейсхолдеры {ping} {time} {mspt} |
| `config/sidebar.yml` | Scoreboard — строки, анимация заголовка, интервалы |
| `config/npcs.yml` | NPC — позиция, скин, действия (open-menu / run-command / transfer-server) |
| `config/join-items.yml` | Предметы при входе — слот, материал, действия, condition |
| `config/menus.yml` | Chest-меню — декор, кнопки, действия, visibility |
| `config/holograms.yml` | Голограммы |
| `config/player-behavior.yml` | Поведение игрока |

Runtime-данные (не в git): `storage/ops.txt`, `storage/parkour/leaderboard.tsv`, `proxy.properties`, `worlds/lobby/`.

---

## Ключевые особенности для разработки

### Minestom ≠ Paper
- Нет Bukkit API, нет плагинов — всё регистрируется через `EventNode`
- `InventoryPreClickEvent` нужно отменять (`setCancelled(true)`) вручную
- `ExecutionType` имеет только `TICK_START` и `TICK_END` (нет ASYNC)
- `BlockHandler.Dummy.get("minecraft:sign")` — правильный способ зарегистрировать dummy-хэндлер
- Звуки: `Sound.Source.RECORD` для музыки (не MUSIC, иначе ванильный ambient перебивает)
- Virtual threads: `Thread.startVirtualThread(...)` для MongoDB и async операций

### Cooldown-паттерн (везде единообразно)
```java
private static final long COOLDOWN_MS = 300;
private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

long now = System.currentTimeMillis();
Long last = cooldowns.put(player.getUuid(), now);
if (last != null && (now - last) < COOLDOWN_MS) return;
```

### Debounce сохранения в MongoDB (PlayerPreferencesService)
Быстрые изменения настроек коалесцируются в один delayed write через `MinecraftServer.getSchedulerManager().buildTask(...).delay(400ms).schedule()`.

### GUI — не пересоздавать Inventory на каждый клик
`ParkourSettingsMenu` держит один `Inventory` per player в `Map<UUID, Inventory>` и обновляет только изменившиеся слоты. Пример правильного подхода — `LobbySettingsMenu.refreshMenu()`.

---

## Важные места в коде

| Что | Где |
|---|---|
| Регистрация всех listener-ов лобби | `LobbyEventRegistrar.register()` |
| Инициализация игрока при входе | `LobbyJoinInitializer.onSpawn()` |
| ExceptionHandler (NoClassDefFoundError fix) | `ServerBootstrap.bootstrap()` — установить до всего |
| Sign block dummy handlers | `ServerBootstrap` — `BlockHandler.Dummy.get(ns)` |
| Музыка — toggle/start/stop | `LobbyMusicManager` |
| Паркур — создание сессии | `ParkourService.startWithDimension()` |
| Паркур — очистка блоков (memory) | `ParkourSession.removeOldTrailingBlocks()` |
| NPC действия | `NpcActionExecutor` |

---

## Права доступа

- **godes2020** — owner (`LobbyConfig.Settings.OPS_OWNER`), единственный, кто может `/op` / `/deop`
- **BYPASS_USERS** (`storage/ops.txt`) — операторы, permission level 4 при входе
- Большинство admin-команд доступны только bypass-пользователям

---

## Что реализовано (все фазы)

- [x] Config infrastructure (ConfigManager, hot-reload `/reload`)
- [x] Tab list из YAML
- [x] Sidebar из YAML (включая анимацию заголовка, `enabled: false` флаг)
- [x] NPC система: YAML + CRUD команды (`/npc add/remove/move/setskin/...`)
- [x] Join items из YAML
- [x] Chest-меню из YAML + `/menu` команды
- [x] Голограммы (text_display + armor_stand fallback)
- [x] Музыка лобби (toggle, selector, случайная очередь, ambient suppressor)
- [x] PlayerHider (скрытие других игроков)
- [x] PlayerPreferences (MongoDB / fallback на defaults)
- [x] Паркур (сессии, генератор, leaderboard, измерения, темы, звуки)
- [x] Spark profiler integration
- [x] Оптимизации производительности (2026-05-19, см. ниже)

---

## Оптимизации 2026-05-19

Результат аудита — устранены критические проблемы с TPS/RAM/спамом кликов:

| # | Проблема | Решение |
|---|---|---|
| C-1 | `ParkourSettingsMenu` пересоздавал Inventory на каждый клик (300+ объектов/сек) | Один `Inventory` per player + обновление только изменившихся слотов |
| C-2 | NPC interaction без cooldown — DB-запросы при спаме клика | Cooldown 400ms |
| C-3 | JoinItem use без cooldown | Cooldown 300ms |
| C-4 | PlayerHider toggle без cooldown | Cooldown 400ms |
| C-5 | Каждое изменение настройки → немедленный MongoDB write | Debounce 400ms |
| M-1 | `ParkourSession.allBlocks` рос без границ (утечка памяти) | Compact при headIndex ≥ 20 |
| M-2 | `ParkourService.startWithDimension` — нет `isOnline()` guard в async | Добавлены guards |
| M-3 | `scheduleInstanceUnregister` — бесконечный retry | Макс. 90 попыток (~90с) |
| M-4 | `changeDimension` — race condition при быстром вызове | `volatile dimensionChangeInProgress` флаг |
| M-5 | `LobbySidebar.lastPingText` — мёртвое хранилище (write-only) | Удалено |
| M-6 | `handleDisconnect` отправлял `stopSound` пакеты отключающемуся игроку | Убрано, только инвалидация токена |
| L-1 | `LobbySettingsMenu` без cooldown | Cooldown 200ms |
| Fix | `NoClassDefFoundError: ExceptionHandler` при тике | Ранний `setExceptionHandler` в `ServerBootstrap` |
| Fix | WARN: sign blocks without handler | Регистрация `BlockHandler.Dummy` для всех вариантов знаков |
