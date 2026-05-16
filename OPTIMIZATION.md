# OPTIMIZATION — снижение MSPT на лобби-сервере

## Метод

**Не оптимизируй наугад. Сначала измерь.** В проекте уже встроен Spark
(`feature/lobby/.../SparkService.java`, плагин Spark подключён в SparkModule).
Spark — это профайлер #1 для Java-серверов Minecraft.

```
/spark profiler start --thread *           # начать сбор
# … подождать 30-60 секунд при типичной нагрузке (несколько игроков на сервере)
/spark profiler stop                        # получить ссылку на отчёт
```

В отчёте смотри:
1. **Server thread (главный тик)** — что съедает основное время каждый тик.
2. **MSPT graph** — есть ли периодические пики (GC-паузы, sync I/O).
3. **Memory allocations** — топ-аллокаций.
4. **Async vs sync** — что блокирует тик, что в фоне.

Дополнительно: `/sparkc tps` и `/sparkc health` дают мгновенный срез.

**Только после отчёта** возвращайся в этот документ и применяй фиксы из тех
разделов, которые соответствуют твоим хотспотам. Иначе будешь тратить
часы на «оптимизации», которые ничего не дадут.

---

## Текущие точки нагрузки в проекте

Эти места — `repeat(...)` задачи, которые крутятся постоянно и **итерируют всех
онлайн-игроков**. Каждая итерация = N (online) операций. С ростом онлайна
стоимость растёт линейно.

| Где | Интервал | Цена за тик |
|---|---|---|
| `LobbyTabListManager.updateAll()` | **100 ms** (10 раз/сек) | для каждого игрока: substitute плейсхолдеров + `Text.raw` (без кеша!) + send header/footer packet |
| `LobbySidebar.refreshAll()` | 1000 ms | для каждого игрока + для каждой строки sidebar: `Text.raw` + updateLineContent |
| `LobbySidebar.tickAnimation()` | 800 ms | один кадр — push всем viewers |
| `StatsBarService.tick()` | 500 ms | для каждого, у кого включён /tpsbar или /rambar: `Text.raw` + setName на BossBar |
| `ProxyOnlineService` | 5 сек | ping сторонних серверов через outgoing connection — может **блокировать тик**, если backend не отвечает |
| `ParkourLeaderboardService.refreshFromStore()` | 3 сек | MongoDB / file read на основном потоке |
| `TextHologram` рассылка | по событию join/leave | при join — каждый hologram-entry шлёт SpawnEntityPacket + Metadata |

---

## Quick wins (10-30 минут на каждый, дают БОЛЬШИНСТВО эффекта)

### 1. Tab list — увеличить интервал с 100 ms до 500-1000 ms

Это **САМАЯ дорогая периодическая задача**. Сейчас крутится 10 раз в секунду и
для каждого игрока пересобирает Component'ы (`Text.raw` без кеша) и шлёт
2 пакета (header + footer).

С 10 игроками онлайн это **20 пакетов в секунду + 20 рендеров Component каждые
100 ms = 200/сек только на таб**.

Фикс: в `config/tab.yml` поменять
```yaml
update-interval-ms: 100
```
на:
```yaml
update-interval-ms: 1000   # или 500, если хочется «живой» MSPT в табе
```

⚠️ Сейчас интервал применяется при старте — после правки нужен рестарт сервера
(см. известное ограничение Фазы 1 в ROADMAP). В Фазе 5 будем делать живой
reschedule.

**Ожидаемый эффект:** −50…−70% времени, которое тик тратит на таб.

### 2. Tab list — кеш по содержимому

`Text.raw(...)` каждый раз парсит легаси-строку через `LegacyComponentSerializer`.
При том, что **подавляющее большинство строк не меняется**.

Подход:
- Если ни одно поле не зависит от игрока (только от глобальных `{time}` и `{online}`)
  — рендерить один раз и слать один и тот же Component всем.
- Per-player отрисовка нужна только когда в строке есть `{ping}`, `{player}`,
  `{mspt}` (последний только для bypass).
- Разделить header/footer на «глобальную» часть (одна на всех) и «персональную»
  (только если строка содержит per-player плейсхолдер).

При 10 игроках это уменьшает с 10× парсинга до 1× в большинстве случаев.

**Ожидаемый эффект:** −30…−50% от оставшегося времени на табе.

### 3. Sidebar — не пересчитывать строки, у которых текст не менялся

Сейчас `refreshAll()` каждую секунду:
- Берёт текущий `cfg`
- Рендерит ВСЕ строки заново через `Text.raw`
- Отправляет updateLineContent для **каждой строки каждому игроку**

Достаточно отправлять обновление только когда:
- Sidebar только что был перезагружен через `/reload` (один раз — всем)
- Или изменилось что-то динамическое: ping, server status

Решение:
- Кешировать `lastRenderedText` для каждой строки/каждого игрока.
- В refresh сравнивать new vs cached, отправлять только если разница.

**Ожидаемый эффект:** −60…−80% времени на сайдбаре в установившемся режиме.

### 4. StatsBarService — пропускать тик, если data не поменялась

`StatsBarService.tick()` каждые 500 ms пересоздаёт Component с TPS/MSPT/Ping
для каждого активного зрителя бара. Если MSPT округлённый не поменялся —
push нечего слать.

То же для RAM-бара — используем округление до десятой части МБ, и если значение
то же, не шлём.

**Ожидаемый эффект:** −80% при идле (никто не флапает баром).

### 5. Compression threshold — поднять обратно

В `ServerBootstrap` стоит `MinecraftServer.setCompressionThreshold(0)`. Это значит
**КАЖДЫЙ** outgoing пакет сжимается zlib'ом. Когда писали — это было нужно
потому что встроенный прокси не понимал compressed-фреймы.

Сейчас стоит standalone ViaProxy + Velocity. **Velocity сам разбирается с
сжатием на backend-канале**. На локалхосте (Velocity↔Minestom через loopback)
сжатие не нужно вообще — оно жжёт CPU без пользы.

Фикс: убрать строку `setCompressionThreshold(0)` ИЛИ выставить дефолт `256` /
`-1` (отключить полностью, что для loopback оптимально).

```java
// в ServerBootstrap, заменить:
MinecraftServer.setCompressionThreshold(0);
// на:
MinecraftServer.setCompressionThreshold(-1);
```

**Ожидаемый эффект:** −10…−30% MSPT при активной сетевой нагрузке (мало
зрителей текущего таба, но при большом числе entity-пакетов — заметно).

⚠️ Только проверь что Velocity при modern-forwarding не требует compression
ON со стороны backend (по идее не должен, modern forwarding это login-плагин-канал).

### 6. ProxyOnlineService — вынести ping серверов в async

Этот сервис каждые 5 секунд опрашивает сторонние серверы (proxy-ping). Если хоть
один не отвечает — есть риск что socket-таймаут блокирует тик.

Фикс: запускать ping в `CompletableFuture.runAsync(...)`, результат пушить
обратно в тик через `SchedulerManager.runOnce(...)`.

**Ожидаемый эффект:** убирает редкие но болезненные spike'и MSPT.

### 7. ParkourLeaderboardService — async I/O

Раз в 3 секунды (`LEADERBOARD_SYNC_MILLIS = 3000L`) читает file/MongoDB. Если
mongo не на той же машине — каждое чтение это сетевой round-trip.

Фикс: завернуть в async, кешировать результат.

**Ожидаемый эффект:** убирает spike'и MSPT, плюс снимает риск, что MongoDB-таймаут
повесит тик.

---

## Серьёзные изменения (1-3 часа каждое)

### 8. `Text.raw` → `Text.c` для статических строк

`Text.c` кеширует распарсенные Component'ы. `Text.raw` — нет. В Phase 1 я
много где использовал `raw` для динамических строк после substitute().

Если строка с плейсхолдерами становится **одной из малого числа уникальных
значений** (например, ping округляется до десятков), можно кешировать по
substituted-результату.

**Альтернатива:** строить Component через ComponentBuilder с явными частями —
тогда статический контекст не парсится каждый раз, а к нему append'ятся
динамические значения. Это быстрее legacy-парсинга.

### 9. Group packets — отправлять одинаковые Component'ы пакетом одной группе

Minestom поддерживает `ServerFlag.GROUPED_PACKET` (по умолчанию true). Если
**один и тот же Component** идёт нескольким игрокам, это сериализуется один раз.

Но для этого надо в коде явно использовать `PacketSendingUtils.sendGroupedPacket(...)`
или похожий API, а не `player.sendPacket(...)` в цикле.

Применимо к: tab header/footer (всем шлём то же), sidebar broadcast строк,
hologram metadata broadcasts.

### 10. Tick monitoring как метрика

Сейчас MSPT отображается только в `/tpsbar`. Хорошо бы:
- Логировать MSPT каждые 60 секунд в файл (rolling)
- Иметь `/spark profiler` запущенный фоном с записью в файл
- Чтобы при жалобе на лаги можно было сразу посмотреть «когда был spike, что было top-method'ом в spark».

Реализация: подписаться на `ServerTickMonitorEvent`, накапливать в скользящем
окне, раз в минуту усреднять и писать в `logs/mspt.log`.

### 11. Текстовые голограммы — viewer culling

`TextHologramEntry` сейчас рассылает SpawnEntityPacket всем, кто `show(player)`
вызвал. В лобби это все игроки.

Но клиент рендерит entity только в пределах view-distance. Игроки далеко от
голограммы получают пакеты впустую (метаданные, обновления).

Фикс: при `updateText/updateStyle` проверять `player.getDistance(position) < N`
(N = 64 — типичный entity render distance), пропускать дальних.

Особенно эффективно для дальних голограмм лидерборда паркура — большинство
игроков их не видит, но получает обновления.

### 12. `ServerFlag` тюнинг

В `net.minestom.server.ServerFlag` есть статические настройки, которые
устанавливаются через `-D` system properties **при старте** (нельзя в runtime):

```
-Dminestom.chunk-view-distance=6           # default 8; для lobby хватит 4-6
-Dminestom.entity-view-distance=4          # default 5; для lobby хватит 3
-Dminestom.entity-synchronization-ticks=20 # default 20; можно 40 для idle entities
-Dminestom.dispatcher-threads=4            # match CPU; default = доступные ядра
-Dminestom.min-chunks-per-tick=0.5
-Dminestom.max-chunks-per-tick=24
-Dminestom.player-packet-per-tick=50
```

Эти флаги передаются в JVM-args через переменную `JAVA_TOOL_OPTIONS` или
переменные env на Pterodactyl, если есть такая возможность. Если Pterodactyl
жёстко фиксирует команду запуска — можно добавить файл `.env` с
`JAVA_TOOL_OPTIONS="-Dminestom.chunk-view-distance=6"`.

**Альтернатива** — программно в коде:

```java
// в Main.java ДО MinecraftServer.init()
System.setProperty("minestom.chunk-view-distance", "6");
```

Тогда ничего не надо менять в Pterodactyl.

**Ожидаемый эффект:** уменьшение view-distance в 2 раза снижает число чанков,
которые сервер шлёт каждому игроку — −10…−30% MSPT при заполненном лобби.

---

## Архитектурные изменения (дни работы — берёшься, если quick wins не хватило)

### 13. Async packet pipeline

Сейчас все `player.sendPacket(...)` идут с тика. На больших объёмах это
становится bottleneck.

Подход: вместо немедленной отправки — складывать в per-player очередь,
flush из netty-eventloop асинхронно. Minestom это в основном уже делает,
но в нашем коде есть места где мы шлём packet синхронно в тике.

### 14. Per-player batching

Если за один тик одному игроку шлётся N пакетов (sidebar updateLineContent
для 11 строк = 11 пакетов) — собрать их в один bundled-packet (`BundlePacket`)
если протокол поддерживает. Это сокращает сетевой round-trip и снимает overhead
serialize per packet.

### 15. Перенести генерацию подписей/Component'ов на background

Если у нас есть тяжёлая логика построения текстового UI — выносить её на отдельный
thread (например `ScheduledExecutorService`) с output в shared structure,
тик только читает уже готовое значение.

Тонкое место: thread safety. Все consumer'ы должны читать атомарно (volatile reference
на immutable snapshot — паттерн, который мы уже использовали для конфигов).

### 16. Объединить периодические задачи

Сейчас у нас:
- Tab — каждые 100 ms
- Sidebar refresh — 1000 ms
- Sidebar animation — 800 ms
- StatsBar — 500 ms

Каждая — отдельный scheduler-task. Можно объединить в один **«UI tick»** на
100/200 ms, который сам решает что обновлять по тику-счётчику.

Это уменьшает overhead scheduler и context-switch'ей.

---

## Что НЕ трогать (заданы хостингом)

JVM-флаги из задания на Pterodactyl **не меняются**:
```
-Xms128M -XX:MaxRAMPercentage=80.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=130 ...
```

Эти флаги в целом разумные (G1 с агрессивным новым size, large pages, NUMA,
ParallelRefProc, отключённый explicit GC, IHO=10, sweep activity=1).
Не пытайся подсунуть туда другие через `--add-opens` или ещё как-то.

Если хочешь добавить **только Minestom-системные свойства** (`-D...`),
проще всего:
1. Через `System.setProperty(...)` в `Main.main` ДО `MinecraftServer.init()`.
2. Через файл `JAVA_TOOL_OPTIONS` env variable если Pterodactyl это разрешает.

---

## План действий по приоритету

| # | Задача | Время | Эффект |
|---|---|---|---|
| 1 | Поднять `update-interval-ms` таба до 1000 в `config/tab.yml` | 1 мин | **Большой** |
| 2 | Убрать `setCompressionThreshold(0)`, поставить `-1` | 5 мин | Средний |
| 3 | Профилирование Spark под нагрузкой | 30 мин | **Информация** для остального |
| 4 | Кеш по содержимому для tab (per-player только если нужны per-player плейсхолдеры) | 1-2 часа | **Большой** |
| 5 | Sidebar diff'ить значения перед отправкой | 1-2 часа | Большой |
| 6 | StatsBar — round и skip-if-unchanged | 30 мин | Средний (для bypass-users) |
| 7 | ProxyOnlineService → async | 1 час | Спайки → плоско |
| 8 | ParkourLeaderboardService → async + кеш | 1 час | Спайки → плоско |
| 9 | `System.setProperty("minestom.chunk-view-distance","6")` в Main | 1 мин | Средний при заполненном лобби |
| 10 | Hologram viewer-culling по расстоянию | 2-3 часа | Средний |

**Главное правило:** делать по одному пункту, мерить MSPT до и после, фиксировать
дельту. Иначе невозможно понять, что реально помогло.

---

## Чего вообще не должно быть в hot path лобби

- **Mongo-запросы на тике** — даже один синхронный запрос = пара десятков ms потери.
- **`new ComponentBuilder().append(legacy-парс).build()` в цикле игроков** — каждый
  парс десятки микросекунд × N игроков × 10 тиков/сек = миллисекунды.
- **`String.format(...)` в горячих местах** — медленнее string-concat'ов в горячем коде.
- **GSON/JSON парсинг** — никогда в тике; парсить при загрузке.
- **`Files.read*` на тике** — никогда; читать в фоне или при `/reload`.
- **Регулярки** (`String.replace(regex, ...)` — там внутри regex) — выносить compile в static.

---

## Бенчмарк-фреймворк (опционально)

Если хочешь по-серьёзному — добавить **JMH** в проект (отдельный gradle-subproject),
писать микро-бенчи на критические функции (рендер таба, sidebar refresh).
Запускать локально перед каждым изменением.

Это перебор для большинства случаев — Spark + измерение MSPT обычно достаточно.

---

## Где почитать

- Minestom wiki по производительности: <https://wiki.minestom.org/>
- Spark — <https://spark.lucko.me/docs/>
- Aikar-flags обоснование: <https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/>
  (учитывай: статья 2018 года, многие флаги устарели для современной JVM; берёт
  как ориентир, не как догму)

---

## Итог

**Если хочешь быстрого результата** — пункты 1-3 в плане выше дадут процентов 50
снижения MSPT за час работы. Остальное — диминишинг-ретёрнс.

**Если MSPT всё ещё высокий после quick wins** — Spark покажет реального
виновника. Не оптимизируй то, что не подтверждено профайлером.
