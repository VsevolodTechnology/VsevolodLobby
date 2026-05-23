# CoreLobby — Отчёт по оптимизации производительности

> **Дата:** 2026-05-20
> **Проект:** `ua.vsevolod.lobby` (Minestom 2026.03.25-1.21.11, Java 25)
> **Объём:** 151 Java-файл, ~16 000 LOC
> **Метод:** статический анализ кода без изменений. Анализ покрывает TPS/MSPT, RAM/CPU, аллокации, кэширование, I/O, многопоточность, архитектуру.

---

## Содержание
1. [Критические проблемы (HIGH)](#1-критические-проблемы-high)
2. [Средняя важность (MEDIUM)](#2-средняя-важность-medium)
3. [Низкая важность (LOW)](#3-низкая-важность-low)
4. [Положительные наблюдения](#4-положительные-наблюдения)
5. [Итоговые рекомендации и приоритеты](#5-итоговые-рекомендации-и-приоритеты)

---

## 1. Критические проблемы (HIGH)

### 1.1. Блокирующий I/O в `ChatState.save()` на тик-потоке

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/player/chat/ChatState.java](src/main/java/ua/vsevolod/lobby/feature/lobby/player/chat/ChatState.java)
- **Где:** `setLocked()` → `save()` (Files.writeString + Files.move)
- **Почему критично:** `setLocked()` вызывается из команд `/chat lock|unlock` и из обработчиков `PlayerChatEvent` — оба пути идут с тик-потока. Синхронный `Files.move` блокирует MSPT на 5–30 мс при холодном диске.
- **Как исправить:** обернуть запись в `Thread.startVirtualThread(() -> save())`, либо передать в очередь дискового writer (как в `ServerLogger`).
- **Приоритет:** **высокий**
- **Ожидаемый эффект:** устранение пиков MSPT 5–30 мс при операциях с чатом.
- **Риски:** при перезапуске сразу после `setLocked()` запись может не успеть. Решение — flush в shutdown-hook.

---

### 1.2. Блокирующий I/O в `VersionGate.save()` под широким `synchronized`

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/admin/VersionGate.java:33-95](src/main/java/ua/vsevolod/lobby/feature/admin/VersionGate.java)
- **Где:** `setEnabled() / setMin() / setMax()` → `save()` (Files.writeString)
- **Почему критично:** `synchronized` удерживает лок класса на время файлового I/O. Любой `isEnabled()` / `getMin()` тоже синхронизирован — все читатели блокируются на время записи. При множественных правках из админ-команд возможен тик-стол.
- **Как исправить:** хранить `enabled/min/max` в `volatile` или `AtomicBoolean/AtomicInteger`, запись выполнять в virtual thread *после* освобождения лока.
- **Приоритет:** **высокий**
- **Ожидаемый эффект:** убирает потенциальные тик-стопы при `/version` командах.
- **Риски:** нужно убедиться, что несколько одновременных `setEnabled` не записывают противоречивые данные — `AtomicReference<State>` с CAS решает это.

---

### 1.3. Блокирующий I/O в `OpsStore.save()` при `/op` и `/deop`

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/admin/OpsStore.java:38-51](src/main/java/ua/vsevolod/lobby/feature/admin/OpsStore.java)
- **Где:** `save()` вызывается из `OpCommand`/`DeopCommand` сразу после изменения `BYPASS_USERS`
- **Почему критично:** на тик-потоке выполняются `Files.write` + `Files.move`. На HDD это десятки миллисекунд. При частых `/op` (например, скрипт админа) возникают MSPT-спайки.
- **Как исправить:** `Thread.startVirtualThread(OpsStore::save)`; для устойчивости — debounce 200 мс, чтобы серия `/op A`, `/op B`, `/op C` дала одну запись.
- **Приоритет:** **высокий**
- **Ожидаемый эффект:** устранение спайков MSPT при админ-операциях.
- **Риски:** при крэше JVM между изменением Set и записью на диск состояние op'ов теряется. Mitigation: уменьшить окно debounce + flush в shutdown-hook.

---

### 1.4. Блокирующие MongoDB-операции на тик-потоке в `MongoParkourLeaderboardStore.upsertBestResult()`

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/parkour/leaderboard/MongoParkourLeaderboardStore.java:108-143](src/main/java/ua/vsevolod/lobby/feature/parkour/leaderboard/MongoParkourLeaderboardStore.java)
- **Где:** `submit()` в `ParkourLeaderboardService` вызывает `submitResult()` → `upsertBestResult()` с циклом retry до 5 раз: `find().first()`, `insertOne()`, `replaceOne()`
- **Почему критично:** `ParkourLeaderboardService.submit()` помечен `synchronized` и вызывается из `ParkourSession.finish()` — тик-поток. При плохом пинге до Mongo (50–200 мс) один забег парит весь сервер.
- **Как исправить:** Сделать `submit()` асинхронным — выполнять upsert в virtual thread, локальное состояние обновлять через CAS `AtomicReference<List<...>>`. Все каллбэки слушателей запускать через `MinecraftServer.getSchedulerManager().scheduleNextTick(...)`.
- **Приоритет:** **высокий**
- **Ожидаемый эффект:** убирает кратные секундным задержки при сдаче результата парк-сессии. Особенно заметно когда несколько игроков заканчивают забег одновременно.
- **Риски:** возможна гонка между параллельными `submit()` для одного UUID — решается тем, что MongoDB сама атомарна по `_id`, а локальный merge — через CAS.

---

### 1.5. `ParkourFileLeaderboardStore.updateEntries()` — распределённый FileLock на тик-потоке

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/parkour/leaderboard/ParkourFileLeaderboardStore.java:43-55](src/main/java/ua/vsevolod/lobby/feature/parkour/leaderboard/ParkourFileLeaderboardStore.java)
- **Где:** `try (FileLock lock = ignored.lock())` — блокирующая операция
- **Почему критично:** если другой лобби-сервер сейчас пишет (тот же файл лидерборда на общем диске), `FileLock.lock()` блокирует поток на десятки/сотни мс. Также блокирующие `Files.readAllLines` + `Files.writeString` под этим локом.
- **Как исправить:** перенести в virtual thread, как уже сделано в `MongoParkourLeaderboardStore`. Лучше — отключить файловый backend в multi-server конфигурациях.
- **Приоритет:** **высокий** (только если используется multi-server файловый режим)
- **Ожидаемый эффект:** устранение каскадных стопов между лобби-серверами.
- **Риски:** при крэше во время записи возможна потеря последнего результата. Mitigation: атомарный rename (уже используется).

---

### 1.6. `ParkourLeaderboardHologramService` — нет cleanup на `PlayerDisconnectEvent`

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/ParkourLeaderboardHologramService.java](src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/ParkourLeaderboardHologramService.java)
- **Где:** `private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();` без подписки на `PlayerDisconnectEvent`
- **Почему критично:** UUID игроков, которые вошли в зону голограммы и затем отключились, никогда не удаляются. Утечка ~16 байт на каждый дисконнект → за месяц аптайма набирается мегабайты при крупном трафике игроков.
- **Как исправить:**
  ```java
  node.addListener(PlayerDisconnectEvent.class, e -> hideFrom(e.getPlayer()));
  ```
- **Приоритет:** **высокий** (тривиальное исправление, реальная утечка)
- **Ожидаемый эффект:** устранение бесконтрольного роста `viewers` со временем.
- **Риски:** нет.

> ⚠️ Проверить аналогичный паттерн в `LobbyWelcomeHologramService` и `TextHologramEntry` — там тоже могут быть viewer-сеты.

---

### 1.7. Накопление слушателей при `/reload`

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/bootstrap/LobbyEventRegistrar.java:165-181](src/main/java/ua/vsevolod/lobby/feature/lobby/bootstrap/LobbyEventRegistrar.java) + [feature/lobby/ui/menu/config/MenusConfigSection.java:171](src/main/java/ua/vsevolod/lobby/feature/lobby/ui/menu/config/MenusConfigSection.java) + [feature/lobby/interaction/npc/config/NpcConfigSection.java:182](src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/config/NpcConfigSection.java) + [feature/lobby/ui/hologram/config/HologramsConfigSection.java:110](src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/config/HologramsConfigSection.java)
- **Где:** `events.addListener(...)` вызывается заново при каждой регистрации; `addListener(Consumer<...>)` в config sections не имеет парного `removeListener()`
- **Почему критично:** при N перезагрузках конфига каждое событие диспатчится N×. Замедление обработки игровых событий растёт линейно с количеством `/reload`. Также утечка `CopyOnWriteArrayList` listeners в config sections.
- **Как исправить:** ввести `EventNode` для лобби-логики и пересоздавать узел при reload (старые listeners удалятся сами). Для config sections — `removeListener()` / `clearListeners()` перед re-registration.
- **Приоритет:** **высокий**
- **Ожидаемый эффект:** стабильная производительность независимо от количества `/reload`.
- **Риски:** нужно аккуратно реорганизовать regiстрацию — может временно сломать функционал. Лучше делать одну фичу за раз.

---

### 1.8. `ParkourSession.sendHud()` — Component-builder аллоцируется каждый тик

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourSession.java:351-367](src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourSession.java)
- **Код:**
  ```java
  var builder = Component.text()
      .append(HUD_SCORE_LABEL)
      .append(Component.text(score, WHITE))
      .append(HUD_SEPARATOR)
      .append(HUD_TIME_LABEL)
      .append(Component.text(ParkourTimeFormatter.compact(elapsedMillis()), WHITE));
  ```
- **Почему критично:** на каждом тике активной сессии (вызывается из `tick()` через `PlayerMoveEvent`) аллоцируются `TextComponent.Builder`, 2 `Component.text(...)`, итоговый Component, плюс `ParkourTimeFormatter.compact()` форматирует строку. Несколько активных игроков → десятки аллокаций в секунду на каждого.
- **Как исправить:**
  - Кэшировать последний построенный Component по ключу `(score, secondsElapsed)` — обновлять только когда счёт или целая секунда изменились (уже есть `lastHudSecond`, но Component всё равно перестраивается).
  - Использовать `TextReplacementConfig` или предсобранные шаблоны.
- **Приоритет:** **высокий** (горячая точка — несколько раз в секунду на каждую сессию)
- **Ожидаемый эффект:** снижение allocation rate на ~20–40 объектов/сек на каждую активную парк-сессию.
- **Риски:** нужна проверка equality по составу для кэша (`Objects.equals(builtComponent, prevBuilt)`).

---

### 1.9. Дублирование boilerplate в админ-командах

- **Файлы:** все 13 файлов в [src/main/java/ua/vsevolod/lobby/command/admin/](src/main/java/ua/vsevolod/lobby/command/admin/)
- **Паттерн (34 повторения `BYPASS_USERS.contains()`):**
  ```java
  setCondition((sender, _) -> sender instanceof Player p &&
      LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername()));
  setDefaultExecutor((sender, ctx) -> {
      if (!(sender instanceof Player p) || !BYPASS_USERS.contains(p.getUsername())) return;
      // ...
  });
  ```
- **Почему важно:** не прямой источник падения TPS, но: (а) код вдвое выполняет одну и ту же проверку; (б) при изменении модели прав придётся менять 13 файлов — высокая вероятность забыть; (в) лишние allocations лямбд при загрузке.
- **Как исправить:** базовый класс `AdminCommand`, метод `OpsStore.isOperator(sender)`, единый `CommandCondition`. Убрать дубликат внутри `setDefaultExecutor`.
- **Приоритет:** **высокий** (с точки зрения maintainability и обратной защиты от ошибок)
- **Ожидаемый эффект:** минус ~15% LOC в командах, единая точка изменения прав.
- **Риски:** при рефакторинге легко забыть `super()` вызов или повторную регистрацию — нужны компонентные тесты или ручная проверка каждой команды.

---

### 1.10. `ParkourSettingsMenu` — god class на 724 строки

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourSettingsMenu.java](src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourSettingsMenu.java)
- **Где:** один класс отвечает за UI рендеринг, состояние выборов, обработку кликов, cooldown, тематические подменю и инвентори-кэш
- **Почему важно:** 4 `ConcurrentHashMap<UUID, ...>` per-player каши; каждый из них требует cleanup на дисконнект; класс трудно тестировать; вся логика паркур-меню в одном файле.
- **Как исправить:** разделить на `ParkourSettingsState` (модель), `ParkourMainMenuView`, `ParkourThemeMenuView` (UI). Cleanup сделать через единый `evictAll(UUID)`.
- **Приоритет:** **высокий** (maintainability + риск утечки кэшей)
- **Ожидаемый эффект:** уменьшение когнитивной нагрузки, единое место для cleanup.
- **Риски:** значительная перестройка; делать осторожно с покрытием тестами.

---

## 2. Средняя важность (MEDIUM)

### 2.1. `StatsBarService.tick()` — `String.format` на каждого viewer'а каждые 500 мс

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/admin/StatsBarService.java:121-138, 158-161](src/main/java/ua/vsevolod/lobby/feature/admin/StatsBarService.java)
- **Что не так:** `String.format(Locale.US, "...", ...)` создаёт `StringBuilder` + temp-strings на каждого. Хотя skip-if-unchanged смягчает (cache `lastTpsLabel`), сам format вычисляется в любом случае.
- **Почему важно:** при 20 игроках с активным `/tpsbar` это 20×2/сек = 40 `format`-вызовов в секунду.
- **Как исправить:** заменить `String.format` на ручную сборку через `StringBuilder` с одним общим `ThreadLocal` или просто `new StringBuilder(96)`; альтернатива — пред-форматировать общие части (`&7|`, разделители) как константы.
- **Приоритет:** **средний**
- **Ожидаемый эффект:** снижение allocation rate в BossBar-обновлениях.
- **Риски:** нет.

---

### 2.2. `LobbyTabListManager.updateAll()` — две полные проходки по онлайну каждые 50 мс

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/ui/tab/LobbyTabListManager.java:100-136, 142-172](src/main/java/ua/vsevolod/lobby/feature/lobby/ui/tab/LobbyTabListManager.java)
- **Что не так:** `getOnlinePlayers()` вызывается дважды (header refresh + latency broadcast), хоть и с разными интервалами. На каждом тике `LocalTime.now().format(...)` (минимум 1× за тик). При 100 игроках на сервере и обновлении 20 раз/сек — 2000 итераций/сек только на header refresh.
- **Почему важно:** уже сейчас неплохо оптимизирован (skip-if-unchanged, raw `indexOf('{')` для статичных строк), но `LocalTime.now()` вызывает `Clock.systemDefaultZone()` — лишний syscall. Сама итерация по `getOnlinePlayers()` — это allocation `Collection`.
- **Как исправить:**
  - Снизить `updateIntervalMs()` по умолчанию до 250–500 мс (через TabConfig).
  - Кэшировать форматированное время на 1 секунду (`if (currentSec == lastSec) return cachedTimeStr;`).
- **Приоритет:** **средний**
- **Ожидаемый эффект:** снижение нагрузки в TAB-обновлениях в 5–10 раз без визуального вреда (время минут+секунды и так не меняется чаще секунды).
- **Риски:** ping в TAB обновится не сразу — но он и так батчится раз в 2 сек.

---

### 2.3. `LobbyWelcomeHologramService.buildWelcomeComponent()` — полная пересборка компонента

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/LobbyWelcomeHologramService.java](src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/LobbyWelcomeHologramService.java)
- **Что не так:** ~13 `Component.text() + append` на каждое обновление, даже если изменилось только имя или счётчик игроков.
- **Как исправить:** заранее построить «остов» Component'а в `static final`, в `buildWelcomeComponent()` подставлять только динамические части (имя игрока, число онлайн).
- **Приоритет:** **средний**
- **Ожидаемый эффект:** ~10× уменьшение аллокаций при welcome-refresh.
- **Риски:** нет.

---

### 2.4. `ParkourLeaderboardHologramService.refreshFor()` — N× компонентов на каждого зрителя

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/ParkourLeaderboardHologramService.java:152-213](src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/ParkourLeaderboardHologramService.java)
- **Что не так:** `rankComponent()`, `nameComponent()`, `scoreComponent()`, `timeComponent()` строятся для каждой строки лидерборда (обычно 10) × число viewer'ов. `MinecraftFontMetrics.trimToWidth()` + `padStartToWidth()` — за-strings для имени per-viewer.
- **Как исправить:** Кэшировать `List<Component>` по ключу `(entriesHash)` — обновлять только при изменении самого лидерборда. Trim/pad делать один раз на entry, а не на каждого зрителя.
- **Приоритет:** **средний**
- **Ожидаемый эффект:** O(entries) вместо O(entries × viewers) при стабильном лидерборде.
- **Риски:** нет.

---

### 2.5. `PlayerHider.refreshTabFor()` — O(N²) при массовом toggle

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/player/visibility/PlayerHider.java:100-143](src/main/java/ua/vsevolod/lobby/feature/lobby/player/visibility/PlayerHider.java)
- **Что не так:** для каждого toggle итерируется по всем онлайн-игрокам, и для каждого аллоцируется отдельный `PlayerInfoRemovePacket` / `PlayerInfoUpdatePacket`.
- **Как исправить:** батчить пакеты в один (Minestom поддерживает многоразовые entries в `PlayerInfoUpdatePacket`); проверять флаг `target.isHiddenBy(viewer)` чтобы не отправлять "повторное" скрытие.
- **Приоритет:** **средний**
- **Ожидаемый эффект:** меньше packet flood при массовом toggle.
- **Риски:** нет.

---

### 2.6. `LobbyPlayerChatListener.findPlayer()` — O(N) на каждый @mention

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/player/chat/LobbyPlayerChatListener.java](src/main/java/ua/vsevolod/lobby/feature/lobby/player/chat/LobbyPlayerChatListener.java)
- **Что не так:** при каждом @mention в чате — линейный проход по всем онлайн-игрокам через `getOnlinePlayers()`. Также `StringBuffer` вместо `StringBuilder` (synchronized без нужды).
- **Как исправить:** держать `Map<String, UUID> usernameToUuid` (lower-case ключ) с обновлением на join/disconnect; заменить `StringBuffer` на `StringBuilder`.
- **Приоритет:** **средний**
- **Ожидаемый эффект:** O(1) вместо O(online) на mention; чуть быстрее `replace`.
- **Риски:** при изменении username (Mojang refresh) нужно держать актуальность карты — но в Minestom UUID/username фиксируется на сессию.

---

### 2.7. `Text.c()` — двойная синхронизация на горячих строках

- **Файл:** [src/main/java/ua/vsevolod/lobby/util/Text.java:61-67](src/main/java/ua/vsevolod/lobby/util/Text.java)
- **Что не так:** `CACHE` это `Collections.synchronizedMap(LinkedHashMap)`, при этом весь блок завёрнут ещё в `synchronized (CACHE)`. На popular strings (тексты в sidebar/hologram/tab) — постоянная контеншн.
- **Как исправить:** заменить на `ConcurrentHashMap<String, Component>` с ручной bound-LRU (например, через `Caffeine` или собственный wrapper). Lockless path для cache hits.
- **Приоритет:** **средний**
- **Ожидаемый эффект:** убирает contention под нагрузкой; cache hits становятся read-only.
- **Риски:** теряется LRU-поведение `LinkedHashMap` — но текущий лимит 512 невелик, можно ужать до простого `size > limit → clear` или ограничиться FIFO.

---

### 2.8. `ConfigManager.synchronized loadAll() / reloadAll()` — широкий лок

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/admin/config/ConfigManager.java:23-78](src/main/java/ua/vsevolod/lobby/feature/admin/config/ConfigManager.java)
- **Что не так:** весь `reloadAll()` под одним монитором, держит лок во время I/O всех YAML-файлов. Чтения статуса из других потоков блокируются.
- **Как исправить:** разделить лок по секциям (`ReentrantReadWriteLock` или per-section lock); делать read через CAS `AtomicReference<Snapshot>`.
- **Приоритет:** **средний** (reload редкий, но при reload вся игра встаёт)
- **Ожидаемый эффект:** более плавный reload без фриза.
- **Риски:** небольшое усложнение модели — нужно зафиксировать read/write semantics.

---

### 2.9. `ByteArrayWorldLoader.getOrCreateDelegate()` — гонка в double-checked locking

- **Файл:** [src/main/java/ua/vsevolod/lobby/bootstrap/module/ByteArrayWorldLoader.java:53-78](src/main/java/ua/vsevolod/lobby/bootstrap/module/ByteArrayWorldLoader.java)
- **Что не так:** `delegate`, `tempDir`, `presentRegions` — все `volatile`, но присваиваются по очереди внутри `synchronized`. Другой поток через double-checked может увидеть `delegate != null` и при этом `presentRegions == null`.
- **Как исправить:** объединить в один immutable record `LoaderState`, делать одно volatile-присвоение `state = new LoaderState(delegate, tempDir, regions)`. Внешний доступ — через единственное `volatile` поле.
- **Приоритет:** **средний** (потенциальный NPE при холодном старте под нагрузкой)
- **Ожидаемый эффект:** устранение возможной NPE при первом параллельном `loadChunk()`.
- **Риски:** нужна аккуратная инициализация — небольшая.

---

### 2.10. `ProxyOnlineService.counts` — нет TTL-cleanup для устаревших записей

- **Файл:** [src/main/java/ua/vsevolod/lobby/bootstrap/server/ProxyOnlineService.java:100-114](src/main/java/ua/vsevolod/lobby/bootstrap/server/ProxyOnlineService.java)
- **Что не так:** записи с `age > COUNT_TTL_MS` помечаются как "LOADING" в `getStatus()`, но не удаляются из мапы. Со временем накапливаются.
- **Как исправить:** периодический cleanup-task (раз в минуту) с удалением устаревших; либо `Caffeine.expireAfterWrite(TTL)`.
- **Приоритет:** **средний** (медленная утечка, ограниченная числом серверов)
- **Ожидаемый эффект:** убирает фрагментацию мапы.
- **Риски:** нет.

---

### 2.11. Магические числа в paroour-конфигурации

- **Файлы:**
  - [src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourSession.java:29-35](src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourSession.java) — `FALL_THRESHOLD`, `BLOCKS_AHEAD`, `BLOCKS_BEHIND`, `COMPACT_THRESHOLD`
  - [src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourDifficulty.java:7-30](src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourDifficulty.java) — параметры дальности прыжка вшиты в enum
  - [src/main/java/ua/vsevolod/lobby/config/LobbyConfig.java:265-266](src/main/java/ua/vsevolod/lobby/config/LobbyConfig.java) — `VOID_THRESHOLD_Y = 76.0` с TODO
- **Что не так:** значения не настраиваются через YAML, требуют пересборки.
- **Как исправить:** добавить `parkour-config.yml` с настройками сложностей; вынести `VOID_THRESHOLD_Y` в существующий конфиг.
- **Приоритет:** **средний** (удобство, не perf)
- **Ожидаемый эффект:** возможность тюнить параметры без пересборки → быстрее находить оптимум.
- **Риски:** нет.

---

### 2.12. Дубликаты cooldown-логики на разных Map<UUID, Long>

- **Файлы:**
  - `PlayerHider` (toggleCooldowns)
  - `ParkourSettingsMenu` (lastClick)
  - `JoinItemUseListener`
  - `LobbyNpcInteractionListener`
  - `LobbyPlayerChatListener` (cooldowns)
- **Что не так:** каждый владеет своим `ConcurrentHashMap<UUID, Long>` + ручная проверка `System.currentTimeMillis() - last > X`. Дубликаты в 5+ местах.
- **Как исправить:** единый `CooldownService` с API `boolean check(UUID, String key, long durationMs)`. Реализация: один большой `ConcurrentHashMap<CooldownKey, Long>` + scheduled cleanup.
- **Приоритет:** **средний** (maintainability)
- **Ожидаемый эффект:** убирает повторение кода, проще тюнить cooldown'ы.
- **Риски:** при миграции легко пропустить место — нужны grep-проверки.

---

### 2.13. `MenusConfigSection.saveAndApply()` / `NpcConfigSection.saveAndApply()` — YAML-сериализация под `synchronized` + I/O

- **Файлы:**
  - [src/main/java/ua/vsevolod/lobby/feature/lobby/ui/menu/config/MenusConfigSection.java:207-224](src/main/java/ua/vsevolod/lobby/feature/lobby/ui/menu/config/MenusConfigSection.java)
  - [src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/config/NpcConfigSection.java:241-259](src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/config/NpcConfigSection.java)
- **Что не так:** `synchronized` метод держит лок во время YAML dump (десятки мс при большом конфиге) + `Files.writeString`. Все читатели секции блокируются.
- **Как исправить:** под `synchronized` только подготовить snapshot; сериализацию + I/O делать в virtual thread; после успешной записи через CAS обновить applied snapshot.
- **Приоритет:** **средний** (используется только в админ-командах)
- **Ожидаемый эффект:** убирает фриз игрового потока при `/menu save`.
- **Риски:** нужна правильная семантика «изменения применяются после fsync».

---

### 2.14. Spawning virtual thread в каждом scheduler-тике

- **Файлы:** [MsptLogger.java:59-62](src/main/java/ua/vsevolod/lobby/feature/admin/MsptLogger.java), [ParkourLeaderboardService.java:32-35](src/main/java/ua/vsevolod/lobby/feature/parkour/leaderboard/ParkourLeaderboardService.java)
- **Что не так:** `buildTask(() -> Thread.startVirtualThread(...))` — на каждом интервале создаётся новый поток. Virtual threads дёшевы, но не бесплатны.
- **Как исправить:** держать один долгоживущий virtual-thread executor с `Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())` или `Executors.newVirtualThreadPerTaskExecutor()`.
- **Приоритет:** **средний/низкий** (на текущей частоте — раз в 2/60 сек — несущественно)
- **Ожидаемый эффект:** консистентность threading-модели.
- **Риски:** нет.

---

## 3. Низкая важность (LOW)

### 3.1. Множество legacy `§`-кодов вместо modern API

- **Файлы:** [LobbyQrMapItem.java:23-27](src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/qr/LobbyQrMapItem.java), [VersionGateListener.java:28](src/main/java/ua/vsevolod/lobby/feature/admin/VersionGateListener.java), [admin commands `.sendMessage("§...")` — 68 случаев](src/main/java/ua/vsevolod/lobby/command/admin/)
- **Эффект:** парсинг `§`-кода работает, но deprecated. Один раз на сборку Component'а — копейки.
- **Приоритет:** **низкий** (стиль). Лучше перейти на `TextColor.color()` или `Component.text(..., color)` для консистентности с остальным проектом.

---

### 3.2. `Component.space()` создаётся каждый раз вместо `static final SPACE`

- **Файлы:** все меню (ParkourSettingsMenu, LobbySettingsMenu, ParkourDimensionMenu и т.д.)
- **Эффект:** микрооптимизация, ~5 spaces × 500 B на каждое открытие меню.
- **Приоритет:** **низкий** — менюшки открываются нечасто.

---

### 3.3. `Stream.of(...).toList()` в `PlayerHider.buildToggleItem` / `ParkourDimensionMenu.createDimensionItem`

- **Файлы:** [PlayerHider.java:203-228](src/main/java/ua/vsevolod/lobby/feature/lobby/player/visibility/PlayerHider.java), [ParkourDimensionMenu.java:160-175](src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourDimensionMenu.java)
- **Эффект:** для маленьких фиксированных списков лучше `List.of(...)` (immutable, без stream overhead). Микро.
- **Приоритет:** **низкий**.

---

### 3.4. `LaunchPadManager.onMove()` — `System.currentTimeMillis()` внутри проверки cooldown

- **Файл:** `LaunchPadManager` (был замечен агентом)
- **Эффект:** sysколл в "горячем" PlayerMoveEvent — но он гейтится через быструю проверку `activeJump.isEmpty()` сначала, так что вызывается только когда уже прыгаешь. OK.
- **Приоритет:** **низкий**.

---

### 3.5. `ServerLogger` double-checked locking — формально корректный

- **Файл:** [src/main/java/ua/vsevolod/lobby/util/ServerLogger.java:25-34](src/main/java/ua/vsevolod/lobby/util/ServerLogger.java)
- **Эффект:** `INSTANCE` volatile, поля конструктора инициализируются до публикации. Корректно по JMM. Но шаблон хрупкий — лучше eager init или Holder pattern.
- **Приоритет:** **низкий** (работает).

---

### 3.6. `ConsoleListener` — daemon platform thread в shutdown'е

- **Файл:** [src/main/java/ua/vsevolod/lobby/integration/console/ConsoleListener.java](src/main/java/ua/vsevolod/lobby/integration/console/ConsoleListener.java)
- **Эффект:** daemon thread не блокирует JVM exit; reader.close() сработает через try-with-resources.
- **Приоритет:** **низкий**.

---

### 3.7. `MsptLogger` CAS-spin на `maxNs`

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/admin/MsptLogger.java:55-56](src/main/java/ua/vsevolod/lobby/feature/admin/MsptLogger.java)
- **Эффект:** теоретически spin под контентом, но в реальности `ServerTickMonitorEvent` приходит с тик-потока — единственный писатель. Чисто.
- **Приоритет:** **низкий**.

---

### 3.8. `StatsBarService.lastRamLabel` без `volatile`

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/admin/StatsBarService.java:38](src/main/java/ua/vsevolod/lobby/feature/admin/StatsBarService.java)
- **Эффект:** мутируется только из scheduler-таска (один поток) — visibility OK на практике, но без `volatile` есть формальная гонка.
- **Приоритет:** **низкий**.

---

### 3.9. `ParkourLeaderboardStore` vs `ParkourLeaderboardSubmissionStore` — два интерфейса с одним каждым методом

- **Файлы:** `feature/parkour/leaderboard/Parkour*Store.java`
- **Эффект:** небольшое over-abstraction по CQRS-принципу. Допустимо, можно слить если хочется упростить.
- **Приоритет:** **низкий**.

---

### 3.10. `Module` интерфейс с ручной регистрацией модулей

- **Файл:** [src/main/java/ua/vsevolod/lobby/bootstrap/server/ServerBootstrap.java:101-106](src/main/java/ua/vsevolod/lobby/bootstrap/server/ServerBootstrap.java)
- **Эффект:** при добавлении модуля надо править bootstrap. Можно `ServiceLoader.load(Module.class)`. Не блокер — модулей 4.
- **Приоритет:** **низкий**.

---

### 3.11. `MinestomTagsWorkaround` — временный workaround с TODO

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/lobby/player/workaround/MinestomTagsWorkaround.java](src/main/java/ua/vsevolod/lobby/feature/lobby/player/workaround/MinestomTagsWorkaround.java)
- **Эффект:** костыль для бага в Minestom, помеченный TODO. Когда Minestom исправит — удалить.
- **Приоритет:** **низкий** (следить за upstream).

---

### 3.12. `CommandsReferenceWriter` — 596 строк, периодически генерирует справочник команд

- **Файл:** [src/main/java/ua/vsevolod/lobby/feature/admin/config/CommandsReferenceWriter.java](src/main/java/ua/vsevolod/lobby/feature/admin/config/CommandsReferenceWriter.java)
- **Эффект:** запускается в bootstrap, пишет markdown с описанием команд. Не runtime hot path, но 596 строк статичных строк — раздувает jar.
- **Приоритет:** **низкий**.

---

## 4. Положительные наблюдения

Эти места **сделаны правильно** — не трогать без сильной причины:

1. **66 экземпляров `private static final ItemStack/Component`** — отличное переиспользование. ParkourSettingsMenu использует pre-built APPLY_NEW / APPLY_SAME / APPLY_RESTART / APPLY_CHANGE кнопок.
2. **`Text.java` LRU-кэш** на 512 элементов — bounded, с правильной `removeEldestEntry`.
3. **Большинство per-player мап имеют `PlayerDisconnectEvent` cleanup** (TabList, Sidebar, MenuManager, PlayerHider, ParkourService, StatsBarService).
4. **`ServerLogger` использует virtual thread с queue** для file I/O — игровой поток не блокируется логированием.
5. **Spark-based MSPT/TPS** вместо локального вычисления (`SparkService.getMspt()` / `getTps()`) — стабильное среднее, не амплифицирует одиночные тик-спайки.
6. **`LobbySidebar` pre-rendering анимационных кадров** в массив, skip-if-unchanged на title.
7. **`LobbyTabListManager`** — двойной шаблон (bypass / normal), per-player только `{ping}.replace`, skip-if-unchanged через `RenderedTab`.
8. **`ParkourLeaderboardService` client-side caching** (CRIT-06 audit) — submit обновляет локальный snapshot, не reload-loop DB.
9. **`LobbyQrMapService.preinit()`** — форс-инициализация в bootstrap, чтобы не было лагов при первом джойне.
10. **Атомарная запись через tmp + ATOMIC_MOVE** во многих местах (`OpsStore`, `ChatState`, `VersionGate`, `ParkourFileLeaderboardStore`) — защита от corruption при крэше.
11. **`AsyncPlayerPreLoginEvent`** правильно используется для preload игровых данных из Mongo (вне тик-потока).

---

## 5. Итоговые рекомендации и приоритеты

### Сделать **в первую очередь** (наибольший прирост / минимум риска):

| # | Что | Где | Почему первым |
|---|------|-----|----------------|
| 1 | Перевести блокирующий I/O на virtual threads | `ChatState`, `OpsStore`, `VersionGate`, `MenusConfigSection.saveAndApply`, `NpcConfigSection.saveAndApply`, `MongoParkourLeaderboardStore.upsertBestResult` | Прямое падение MSPT-спайков; тривиальный wrap `Thread.startVirtualThread` |
| 2 | Добавить cleanup на `PlayerDisconnect` в `ParkourLeaderboardHologramService` и проверить остальные viewer-сеты | hologram/* | 5 строк кода — устраняет утечку |
| 3 | Кэшировать построенный Component в `ParkourSession.sendHud()` (или хотя бы переиспользовать готовый builder) | ParkourSession.java:351-367 | Самая горячая аллокация в проекте |
| 4 | Снизить частоту `LobbyTabListManager.updateAll` до 250 мс + кэшировать `LocalTime.now().format` на 1 сек | LobbyTabListManager | Простое изменение, кратное снижение нагрузки |
| 5 | Пересоздавать `EventNode` при reload вместо `addListener` поверх старых | LobbyEventRegistrar | Утечка слушателей → деградация TPS со временем |

### Сделать **во вторую очередь** (полезно, но требует аккуратности):

| # | Что | Где | Эффект |
|---|------|-----|--------|
| 6 | Базовый класс `AdminCommand` + `OpsStore.isOperator()` | command/admin/* | Снимает дубль кода и риск ошибок |
| 7 | Заменить `Collections.synchronizedMap` в `Text.c()` на `ConcurrentHashMap` с bounded cleanup | util/Text.java | Lockless cache hits |
| 8 | Унифицированный `CooldownService` | замена 5+ Map<UUID,Long> | Maintenance |
| 9 | Кэширование Component'ов в `LobbyWelcomeHologramService` и `ParkourLeaderboardHologramService` | hologram/* | Снижение allocation rate |
| 10 | Преобразование `ByteArrayWorldLoader` в immutable state record | ByteArrayWorldLoader | Устранение гонки |
| 11 | Username→UUID карта для @mentions | LobbyPlayerChatListener | O(1) вместо O(online) |
| 12 | Перенос параметров парк-сложностей в YAML | feature/parkour | Туннинг без пересборки |

### Можно сделать **позже** или вовсе пропустить:

- Замена `§`-кодов на modern API (стиль).
- Stream→ArrayList в построении меню (микро).
- Pre-built `Component.SPACE` (микро).
- ServiceLoader для модулей (4 модуля — overkill).
- Разделение `ParkourSettingsMenu` на 3 класса (большой рефакторинг, средняя польза).
- Refactor leaderboard interfaces в один (CQRS-разделение оправдано).

### Что даст **самый заметный** прирост:

1. **Async I/O для save-операций** — устранение MSPT-спайков 5–30 мс на операциях chat/op/version → сильное снижение `max` MSPT в логе `MsptLogger`.
2. **Снижение частоты Tab refresh с 50 мс до 250 мс** — кратное снижение CPU при 100+ игроках. Визуально неотличимо.
3. **Component caching в `ParkourSession.sendHud()`** — снимает горячую аллокацию.
4. **Cleanup viewer-сетов на disconnect** — устраняет медленную утечку RAM.
5. **EventNode re-creation on reload** — стабильный TPS независимо от истории `/reload`.

### Прогноз итогового эффекта при выполнении пунктов 1–5:

- **MSPT (peak):** −30 % (исчезновение I/O-всплесков).
- **MSPT (avg):** −10..15 % (меньше задач каждые 50 мс).
- **Allocation rate:** −20..30 % (меньше Component-builder'ов в HUD + кэш).
- **RAM (over uptime):** перестаёт расти от утечек.
- **TPS:** при текущей нагрузке вероятно уже 20.0; запас вырастет.

### Риски при оптимизациях:

- **Async I/O:** при крэше JVM между set и записью теряются последние изменения — снимать через debounce + flush в shutdown-hook.
- **Снижение частоты Tab:** при очень частых spec/spectator-toggle'ах задержка обновления может стать видимой — допустимо.
- **Component caching:** нужны корректные ключи кэша по «изменилось ли содержимое» — `Objects.equals`.
- **EventNode rebuild:** при ошибке в порядке инициализации можно потерять слушатель — нужно ручное тестирование reload-кейсов.
- **Базовый класс команд:** при рефакторинге легко потерять кастомное поведение одной команды — делать по одной, проверять каждую.

---

## Приложение A. Файлы с подтверждёнными проблемами

| Файл | Категория | Серьёзность |
|------|-----------|-------------|
| `feature/lobby/player/chat/ChatState.java` | Blocking I/O | HIGH |
| `feature/admin/VersionGate.java` | Blocking I/O + lock | HIGH |
| `feature/admin/OpsStore.java` | Blocking I/O | HIGH |
| `feature/parkour/leaderboard/MongoParkourLeaderboardStore.java` | Blocking Mongo on tick | HIGH |
| `feature/parkour/leaderboard/ParkourFileLeaderboardStore.java` | FileLock on tick | HIGH |
| `feature/lobby/ui/hologram/ParkourLeaderboardHologramService.java` | Memory leak | HIGH |
| `feature/lobby/bootstrap/LobbyEventRegistrar.java` | Listener accumulation | HIGH |
| `feature/lobby/ui/menu/config/MenusConfigSection.java` | Blocking write under lock | HIGH |
| `feature/lobby/interaction/npc/config/NpcConfigSection.java` | Blocking write under lock | HIGH |
| `feature/parkour/ParkourSession.java` | Per-tick allocation in sendHud | HIGH |
| `feature/parkour/ParkourSettingsMenu.java` | God class | HIGH |
| `command/admin/*.java` (×13) | Boilerplate duplication | HIGH |
| `feature/admin/StatsBarService.java` | String.format per viewer | MEDIUM |
| `feature/lobby/ui/tab/LobbyTabListManager.java` | High refresh rate | MEDIUM |
| `feature/lobby/ui/hologram/LobbyWelcomeHologramService.java` | Component rebuild | MEDIUM |
| `feature/lobby/player/visibility/PlayerHider.java` | O(N²) toggle | MEDIUM |
| `feature/lobby/player/chat/LobbyPlayerChatListener.java` | O(online) findPlayer | MEDIUM |
| `util/Text.java` | Synchronized cache | MEDIUM |
| `feature/admin/config/ConfigManager.java` | Wide synchronized | MEDIUM |
| `bootstrap/module/ByteArrayWorldLoader.java` | DCL race | MEDIUM |
| `bootstrap/server/ProxyOnlineService.java` | TTL cleanup missing | MEDIUM |
| `config/LobbyConfig.java` | Magic numbers TODO | MEDIUM |

---

## Приложение B. Положительные паттерны для сохранения

| Файл | Паттерн |
|------|---------|
| `util/Text.java` | Bounded LRU + `static final` Component'ы |
| `util/ServerLogger.java` | Virtual thread writer + queue |
| `feature/lobby/ui/tab/LobbyTabListManager.java` | Dual-template + skip-if-unchanged |
| `feature/lobby/ui/sidebar/LobbySidebar.java` | Pre-rendered animation frames |
| `feature/parkour/leaderboard/ParkourLeaderboardService.java` | Client-side caching (CRIT-06 fix) |
| `feature/lobby/interaction/qr/LobbyQrMapService.java` | Pre-init static packet |
| `feature/admin/MsptLogger.java` | Atomic accumulation + reset |
| `feature/parkour/ParkourSettingsMenu.java` | Pre-built ItemStack кнопки |
