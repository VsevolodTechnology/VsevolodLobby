# ROADMAP — конфиги, hot-reload, in-game редактирование

Прогресс отмечать прямо в этом файле: `[ ]` → `[x]`.
Каждая фаза = отдельный коммит (можно несколько).
Между фазами — пауза на тест и review.

---

## Фаза 1 — Фундамент + Tab+Sidebar

Без неё все остальные фазы не имеют смысла: нужен единый ConfigManager, формат YAML, шаблон автогенерации, hot-reload framework.

### 1.1 Инфраструктура

- [x] Добавить зависимость `org.yaml:snakeyaml` (или `org.spongepowered:configurate-yaml`) в `build.gradle.kts`
- [x] Создать `feature/admin/config/ConfigManager.java`:
  - [x] метод `register(name, defaultsSupplier, loader)` для регистрации каждого подконфига
  - [x] метод `loadAll()` — вызывается при старте
  - [x] метод `reloadAll()` — вызывается по команде
  - [x] автоматическое создание файла из template при отсутствии
  - [x] атомарная запись (write to `.tmp` → `Files.move`)
  - [x] валидация: при ошибке парсинга — лог и фоллбэк на дефолты
- [x] Создать команду `/reload` — owner-only, вызывает `ConfigManager.reloadAll()`, отвечает количеством перезагруженных секций / ошибками
- [x] Папка `config/` в working directory (как `storage/`)

### 1.2 Tab list config

- [x] `config/tab.yml` — структура:
  ```yaml
  update-interval-ms: 100
  time-format: "HH:mm"
  header:
    - "&#FF9700&lOVERDYN"
    - ""
    - "&#FFF2E0Вы находитесь в &eЛобби"
    - "&#FFF2E0Пинг: &a{ping}мс &7⇄ &#FFF2E0Время: &a{time}{mspt}"
    - ""
    - "&#FF9700↶ &#FFF2E0Список игроков &#FF9700↷"
  footer:
    - ""
    - "&#FFF2E0Магазин: ..."
    # ...
  mspt-format-bypass: " &#FFF2E0MSPT: &e{mspt}"
  ```
- [x] `TabConfig` record/класс — десериализация YAML → java
- [x] Заменить хардкод в `LobbyTabListManager.update()` — все строки из `TabConfig`
- [x] Поддержать плейсхолдеры `{ping}`, `{time}`, `{mspt}`, `{online}`, `{player}`
- [x] Тест: правка `tab.yml` + `/reload` → таб обновился без рестарта

### 1.3 Sidebar config

- [x] `config/sidebar.yml` — структура:
  ```yaml
  title-animation:
    interval-ms: 800
    frames:
      - "&#FFB300&lO&#FFBE1A&lV..."
      - "..."
  lines:
    - id: "blank_top"
      score: 15
      text: ""
    - id: "modes_header"
      score: 14
      text: "&#FF9700↶ Режимы онлайн ↷"
    # ...
  refresh-interval-ms: 1000
  ```
- [x] `SidebarConfig` класс
- [x] Заменить хардкод в `LobbySidebar` — строки, score, animation frames *(тексты + анимация в YAML; reorder/добавление новых строк — Фаза 5)*
- [x] Поддержать `{ping}`, `{online}` плейсхолдеры в text
- [x] Тест: правка → `/reload` → sidebar пересоберётся для всех онлайн

### 1.4 Коммит

- [ ] commit: "Phase 1: config infrastructure + tab/sidebar"
- [ ] push в ту же ветку `feature/admin-tools`

**Известные ограничения Фазы 1** *(зафиксированы намеренно, ставлю в Фазу 5):*
- Интервалы (animation-interval-ms, refresh-interval-ms, update-interval-ms) применяются только при рестарте — task scheduler не пересчитывается на лету.
- Структура sidebar (порядок строк, новые строки) — фиксированная. В YAML только тексты и анимация. Полный reorder уходит в Фазу 5.

---

## Фаза 2 — NPC система (CRUD + click actions)

### 2.1 Конфиг

- [x] `config/npcs.yml` — структура:
  ```yaml
  npcs:
    - id: "mode-selector"
      position: {x: 0.5, y: 77.0, z: -29.5, yaw: 0, pitch: 0}
      world: "lobby"   # пока всегда lobby
      name: "&6&lВыбор режима"
      description: "&7Нажмите, чтобы открыть меню"
      skin: null         # null = без скина; иначе ник из mojang/sessionserver
      glowing: false
      hologram-enabled: true
      actions:
        right-click:
          type: open-menu      # open-menu | run-command | transfer-server
          target: "mode-selector"  # для open-menu — id меню
          execute-as: player    # player | op
        left-click:
          type: run-command
          target: "/spawn"
          execute-as: op
    - id: "parkour"
      position: {x: 6.5, y: 79.0, z: -4.5, yaw: 52, pitch: 0}
      name: null
      description: "..."
      skin: "Dream"
      glowing: true
      hologram-enabled: false
      actions:
        right-click:
          type: parkour-start
  ```

### 2.2 Реализация

- [x] `NpcsConfig`, `NpcDefinition`, `NpcAction`, `NpcPosition` records
- [x] `NpcManager` — отвечает за создание/удаление/обновление NPC по списку из конфига
  - [x] `onConfigApplied()` — diff (по id) старого и нового списка: remove исчезнувших, добавить новых, update изменённых (position/skin → respawn)
  - [x] поддержка `execute-as-op` — поднимать permission-level игрока на время выполнения команды и откатывать сразу после (в `NpcActionExecutor.runCommand`)
- [x] Заменить хардкод в `LobbyModule.load()` — теперь NPC из конфига
- [x] Click-handler — `LobbyNpcInteractionListener` различает right/left, делегирует в `NpcActionExecutor`

### 2.3 In-game команды

- [x] `/npc add <id>` — создаёт NPC со «слепком» текущей позиции игрока, добавляет в `npcs.yml`, сохраняет, спавнит
- [x] `/npc remove <id>` — деспавнит и удаляет из конфига
- [x] `/npc move <id>` — перемещает на текущую позицию игрока, обновляет конфиг
- [x] `/npc setname <id> <name>` (или `none`)
- [x] `/npc setskin <id> <username>` (или `none`)
- [x] `/npc setdesc <id> <text>` (или `none`)
- [x] `/npc setglow <id> <true|false>`
- [x] `/npc setglowcolor <id> <color>` *(дополнительно — цвет подсветки через team)*
- [x] `/npc setvisible <id> <true|false>` *(дополнительно — выключить отображение)*
- [x] `/npc setaction <id> <right|left> <type> [target] [as-op]`
- [x] `/npc list` — список всех NPC с координатами
- [x] tab-completion для id (autocomplete из конфига)
- [x] все команды owner-only

### 2.4 Hologram → подцеплено к NPC

- [ ] У каждого NPC может быть hologram (одна или несколько строк) — если `hologram-enabled: true`, рисуется автоматически над NPC
- [ ] Текст hologram — поле `name` + `description` (или отдельный массив `hologram-lines:`)

### 2.5 Коммит

- [x] commit: "Phase 2: NPC config + CRUD commands"
- [x] push

### 2.6 Бонус — оптимизация (внутри Phase 2 коммита)

Применены пункты из OPTIMIZATION.md:
- [x] Tab — skip-if-unchanged per-player, fast-path для строк без плейсхолдеров
- [x] Sidebar — skip-if-unchanged per-line; кеш заголовка-анимации; учёт `enabled: false`
- [x] StatsBar — skip-if-unchanged per-player для TPS и RAM баров
- [x] `setCompressionThreshold(-1)` вместо `0` (раньше сжимался каждый пакет)
- [x] `minestom.chunk-view-distance=6`, `entity-view-distance=4`, `entity-synchronization-ticks=20` через `System.setProperty` в Main
- [x] `sidebar.enabled` мастер-флаг — выключение скорборда через конфиг

### 2.7 Текущая позиция

- [x] **Фаза 2 → реализована**
- [x] **Фаза 3 → реализована** (join items YAML + actions + condition)
- [x] **Фаза 4 → реализована** (menus YAML + CRUD + visibility)
- [x] **Фаза 5 → реализована** (closeAll on reload, URL skins, action reuse)

---

## Фаза 3 — Items на join

### 3.1 Конфиг

- [x] `config/join-items.yml` — структура:
  ```yaml
  items:
    - id: "mode-selector"
      slot: 4
      material: "compass"
      name: "&6&lРежимы"
      lore:
        - "&7Открой меню выбора режима"
      glint: false
      actions:
        right-click:
          type: open-menu
          target: "mode-selector"
        left-click: null
      condition: "always"   # always | bypass-only | non-bypass
    - id: "music-toggle"
      slot: 8
      material: "jukebox"
      ...
  ```

### 3.2 Реализация

- [x] `JoinItemsConfig`, `JoinItemDefinition` (action reuses `NpcAction`)
- [x] `JoinItemManager` + модификация `LobbyItemService` — раздавать предметы по конфигу при join
- [x] `JoinItemUseListener` — диспатч в action (right-click → use, left → drop)
- [x] `condition: bypass-only`/`non-bypass`/`always` — фильтр получателей

### 3.3 Команды

- [ ] `/joinitem reload` — это сделает `/reload` глобально, отдельная команда не нужна

### 3.4 Коммит

- [x] commit: "Phase 3: configurable join items"
- [x] push

---

## Фаза 4 — Menus (chest-меню)

### 4.1 Конфиг

- [x] `config/menus.yml` — структура:
  ```yaml
  menus:
    mode-selector:
      title: "&6Выбор режима"
      rows: 5
      visibility: "all"   # all | bypass-only
      decor:
        orange:
          slots: [0, 1, 2, ...]
          material: "orange_stained_glass_pane"
          name: " "
        black:
          slots: [9, 17, ...]
          material: "black_stained_glass_pane"
          name: " "
      items:
        - slot: 13
          material: "grass_block"
          name: "&aSurvival"
          lore:
            - "&7Онлайн: {online}/{max}"
          action:
            type: transfer-server
            target: "survival"
            execute-as: player
        - slot: 22
          material: "diamond_pickaxe"
          name: "&bGrief 1.16"
          action:
            type: transfer-server
            target: "grief.1.16x"
  ```

### 4.2 Реализация

- [x] `MenusConfig`, `MenuDefinition`, `MenuItem`, `MenuDecor`
- [x] `MenuManager` — открывает любое меню по id, рисует слоты/декор/items
- [x] Поддержка плейсхолдеров `{online}`, `{player}` в name/lore
- [x] `visibility: bypass-only` — открыть может только op
- [x] Listener на `InventoryPreClickEvent` — диспатч клика по слоту в action
- [x] Listener на изменение конфига — `closeAll()` всех открытых меню после `/reload`

### 4.3 Команды

- [x] `/menu open <id>` — открыть меню себе
- [x] `/menu list` — список всех меню
- [x] `/menu setvisibility <id> <all|bypass-only>` — изменить и сохранить

### 4.4 Коммит

- [x] commit: "Phase 3+4+5"
- [x] push

---

## Фаза 5 — Полировка и оставшееся

### 5.1 Common helpers

- [x] `NpcAction` переиспользована для NPC / items / menu items (де-факто общий ActionType)
- [x] `execute-as-op` — логика в одном месте: `NpcActionExecutor.runCommand` (permission level 4 на время выполнения)
- [x] Action types: `none`, `run-command`, `open-menu`, `parkour-start`, `transfer-server`

### 5.2 Edge cases

- [x] При `/reload` меню — `MenuManager.closeAll()` через listener на `MenusConfigSection`
- [x] При смене скина NPC через `/npc setskin` — async-фетч с Mineskin/Mojang (URL и username)
- [x] URL скины (`url:...`) — async через virtual thread, после загрузки `updateSkin()` на live entity без респавна
- [ ] При respawn NPC сохранять список viewers — Phase 6+ (NPC сейчас auto-viewable=false, viewers добавляются на join → respawn = ловится в LobbyJoinInitializer)

### 5.3 Документация

- [ ] Дописать `PROJECT.md` — раздел «формат конфигов» с примерами *(шаблоны в .yml файлах уже самодокументируются)*
- [ ] Раздел «как добавить новый ActionType» *(паттерн виден в LobbyModule.load — `npcActionExecutor.register(...)`)*
- [x] Commit: "Phase 3+4+5"

---

## Договорённости

- **Каждая фаза = свой коммит** в ветке `feature/admin-tools` (или, если будет много, отдельные feature-ветки)
- **Перед следующей фазой** — рабочий тест предыдущей в Pterodactyl
- **`/reload` всегда сохраняет рабочий state**: если новый YAML сломан, оставляем старые значения и кричим в лог + чат
- **YAML-файлы автогенерятся** на первом старте с дефолтами (как `proxy.properties` сейчас)
- **Конфиги в `config/`**, runtime-данные в `storage/` — раздельно
- **Не пытаемся** делать «менеджер всего» — каждая подсистема имеет свой YAML и свой класс

## Текущая позиция

- [x] Существует ветка `feature/admin-tools` с админ-командами (op/deop/tpsbar/rambar/version)
- [x] Существует `ProxyConfig` + auto-generate `proxy.properties` (паттерн для остальных)
- [x] Существует `ClientCompat` (можно использовать для условий вроде `version < 762 → fallback`)
- [x] **Фаза 1 → реализована, ждёт пользовательской проверки в игре** (тест `/reload` на живом сервере)
- [ ] **Фаза 2 → следующая**
