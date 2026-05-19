package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import ua.vsevolod.lobby.feature.admin.config.ConfigManager;
import ua.vsevolod.lobby.feature.admin.config.ConfigSection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Config section for chest menus — DeluxeMenus-style YAML format.
 *
 * <pre>
 * menus:
 *   mode-selector:
 *     menu_title: '&amp;8Выбор режима'
 *     size: 45              # rows * 9  (1 row=9, 2=18, 3=27, 4=36, 5=45, 6=54)
 *     open_command: modes   # optional — register /modes as shortcut
 *     visibility: all       # all | bypass-only
 *     items:
 *       bg_black:
 *         material: black_stained_glass_pane
 *         display_name: ' '
 *         slots: [0, 2, 4, 6, 8]
 *       grief:
 *         material: diamond_pickaxe
 *         slot: 22
 *         display_name: '&amp;#E39966&amp;lGrief 1.16'
 *         lore:
 *           - '&amp;7Нажмите, чтобы подключиться'
 *         left_click_commands:
 *           - '[connect] grief.1.16x'
 *         right_click_commands:
 *           - '[connect] grief.1.16x'
 * </pre>
 *
 * <p>Supported command prefixes: {@code [player]}, {@code [console]}, {@code [op]},
 * {@code [message]}, {@code [close]}, {@code [connect]}, {@code [menu]},
 * {@code [parkour]}, {@code [broadcast]}.</p>
 */
public final class MenusConfigSection implements ConfigSection<MenusConfig> {

    public static final MenusConfigSection INSTANCE = new MenusConfigSection();

    private static final MenusConfig DEFAULTS = new MenusConfig(buildDefaults());

    private static Map<String, MenuDefinition> buildDefaults() {
        Map<String, MenuDefinition> out = new LinkedHashMap<>();

        Map<String, MenuItem> items = new LinkedHashMap<>();
        items.put("bg_orange", new MenuItem(
                List.of(1, 3, 5, 7, 9, 11, 15, 17, 18, 19, 25, 26, 27, 29, 33, 35, 37, 39, 41, 43),
                "orange_stained_glass_pane", " ", List.of(), false, List.of(), List.of()));
        items.put("bg_black", new MenuItem(
                List.of(0, 2, 4, 6, 8, 10, 12, 13, 14, 16, 20, 24, 28, 30, 31, 32, 34, 36, 38, 40, 42, 44),
                "black_stained_glass_pane", " ", List.of(), false, List.of(), List.of()));
        items.put("bg_gray", new MenuItem(
                List.of(21, 23),
                "gray_stained_glass_pane", " ", List.of(), false, List.of(), List.of()));
        items.put("adventure", new MenuItem(
                List.of(22),
                "diamond_sword",
                "&#55FF55&lAdventure",
                List.of("&7Нажмите, чтобы подключиться"),
                true,
                List.of("[connect] adventur"),
                List.of("[connect] adventur")));

        out.put("mode-selector", new MenuDefinition(
                "mode-selector", "&8Выбор режима", 45, null,
                MenuDefinition.Visibility.ALL, items));
        return out;
    }

    private static final String TEMPLATE = """
            # ====================================================
            # Меню-сундуки (в стиле DeluxeMenus) — menus.yml
            # ====================================================
            # После изменения файла выполни /reload.
            # Для проверки в игре: /menu open <id-меню>
            #
            # Структура одного меню:
            #   menu_title    — заголовок сундука (& коды и &#RRGGBB HEX).
            #   size          — количество слотов: 9 | 18 | 27 | 36 | 45 | 54 (строки × 9).
            #   open_command  — команда-ярлык для открытия (например "modes" → /modes).
            #                   null = нет команды-ярлыка.
            #   visibility    — кому доступно меню:
            #                     all         — всем игрокам
            #                     bypass-only — только операторам (BYPASS_USERS)
            #
            # Поля предмета (items):
            #   slot          — индекс слота (0 = левый верхний, считается слева направо сверху вниз).
            #   slots         — список слотов (вместо slot, если предмет занимает несколько ячеек).
            #   material      — материал предмета (diamond_sword, compass, stone и т.д.).
            #   display_name  — название предмета (&-коды, &#HEX). null = без названия.
            #   lore          — список строк описания. [] = без описания.
            #   glint         — true = зачарованное мерцание, false = без мерцания.
            #   left_click_commands  — команды при нажатии ЛКМ на предмет.
            #   right_click_commands — команды при нажатии ПКМ на предмет.
            #
            # Префиксы команд:
            #   [player] <команда>    — выполнить /команда от имени игрока
            #   [console] <команда>   — выполнить /команда от консоли (с правами оператора)
            #   [op] <команда>        — временно дать права оператора и выполнить /команда
            #   [message] <текст>     — отправить игроку сообщение в чат
            #   [close]               — закрыть текущее меню
            #   [connect] <сервер>    — перенести игрока на другой сервер (имя сервера в Velocity)
            #   [menu] <id>           — открыть другое меню по идентификатору
            #   [parkour]             — запустить паркур для игрока
            #   [broadcast] <текст>   — показать сообщение всем онлайн-игрокам
            #
            # Плейсхолдеры в display_name и lore:
            #   {player}  — ник игрока, которому открыто меню
            #   {online}  — текущее число игроков онлайн

            menus:
              # Меню выбора режима игры
              mode-selector:
                menu_title: "&8Выбор режима"  # Заголовок сундука (&8 = тёмно-серый)
                size: 45                       # 45 слотов = 5 строк × 9
                open_command: null             # Нет команды-ярлыка (null)
                visibility: all                # Доступно всем игрокам
                items:
                  # Оранжевые стёкла — фоновое украшение
                  bg_orange:
                    material: orange_stained_glass_pane
                    display_name: ' '          # Пробел = нет видимого названия
                    slots: [1, 3, 5, 7, 9, 11, 15, 17, 18, 19, 25, 26, 27, 29, 33, 35, 37, 39, 41, 43]

                  # Чёрные стёкла — фоновое украшение
                  bg_black:
                    material: black_stained_glass_pane
                    display_name: ' '
                    slots: [0, 2, 4, 6, 8, 10, 12, 13, 14, 16, 20, 24, 28, 30, 31, 32, 34, 36, 38, 40, 42, 44]

                  # Серые стёкла — фоновое украшение
                  bg_gray:
                    material: gray_stained_glass_pane
                    display_name: ' '
                    slots: [21, 23]

                  # Кнопка перехода на режим Adventure
                  adventure:
                    material: diamond_sword    # Материал кнопки
                    slot: 22                   # Слот 22 = центр 5-строчного меню
                    display_name: "&#55FF55&lAdventure"  # Зелёный жирный текст
                    lore:
                      - "&7Нажмите, чтобы подключиться"  # Описание (&7 = серый)
                    glint: true               # Зачарованное мерцание для красоты
                    left_click_commands:
                      - "[connect] adventur"   # ЛКМ — перейти на сервер "adventur"
                    right_click_commands:
                      - "[connect] adventur"   # ПКМ — перейти на сервер "adventur"
            """;

    private final AtomicReference<MenusConfig> current = new AtomicReference<>(DEFAULTS);
    private final List<Consumer<MenusConfig>> listeners = new CopyOnWriteArrayList<>();

    private MenusConfigSection() {}

    public MenusConfig current() { return current.get(); }

    public void addListener(Consumer<MenusConfig> listener) { listeners.add(listener); }

    @Override
    public String name() { return "menus"; }

    @Override
    public String templateYaml() { return TEMPLATE; }

    @Override
    public MenusConfig parse(Map<String, Object> yaml) {
        Map<String, Object> raw = coerceMap(yaml.get("menus"));
        Map<String, MenuDefinition> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            try {
                MenuDefinition def = parseMenu(entry.getKey(), coerceMap(entry.getValue()));
                out.put(def.id(), def);
            } catch (Exception e) {
                System.err.println("[MenusConfig] Skipping malformed menu '" + entry.getKey()
                        + "': " + e.getMessage());
            }
        }
        return new MenusConfig(out);
    }

    @Override
    public void apply(MenusConfig snapshot) {
        current.set(snapshot);
        for (Consumer<MenusConfig> listener : listeners) {
            try { listener.accept(snapshot); }
            catch (Throwable t) {
                System.err.println("[MenusConfig] Listener failed: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    public synchronized void saveAndApply(MenusConfig snapshot) throws IOException {
        Path file = ConfigManager.CONFIG_DIR.resolve(name() + ".yml");
        Files.createDirectories(ConfigManager.CONFIG_DIR);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> menus = new LinkedHashMap<>();
        for (MenuDefinition def : snapshot.menus().values()) menus.put(def.id(), dumpMenu(def));
        root.put("menus", menus);

        Files.writeString(tmp, new Yaml(opts).dump(root), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        apply(snapshot);
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private static MenuDefinition parseMenu(String id, Map<String, Object> m) {
        String menuTitle = asString(m.get("menu_title"), "&8" + id);
        // Support both 'size' (total slots) and legacy 'rows'
        int size;
        if (m.containsKey("size")) {
            size = (int) asLong(m.get("size"), 27);
        } else {
            int rows = (int) asLong(m.get("rows"), 3);
            size = rows * 9;
        }
        // Round to valid inventory size
        size = Math.max(9, Math.min(54, (size / 9) * 9));
        if (size == 0) size = 27;

        String openCommand = asNullableString(m.get("open_command"));
        MenuDefinition.Visibility visibility =
                MenuDefinition.Visibility.fromString(asString(m.get("visibility"), "all"));

        Map<String, MenuItem> items = new LinkedHashMap<>();
        Map<String, Object> itemsRaw = coerceMap(m.get("items"));
        for (Map.Entry<String, Object> e : itemsRaw.entrySet()) {
            try {
                MenuItem item = parseItem(coerceMap(e.getValue()));
                items.put(e.getKey(), item);
            } catch (Exception ex) {
                System.err.println("[MenusConfig] Skipping malformed item '" + e.getKey()
                        + "' in menu '" + id + "': " + ex.getMessage());
            }
        }

        return new MenuDefinition(id, menuTitle, size, openCommand, visibility, items);
    }

    private static MenuItem parseItem(Map<String, Object> m) {
        String material = asString(m.get("material"), "stone");
        String displayName = asNullableString(m.get("display_name"));
        // Legacy 'name' key support
        if (displayName == null) displayName = asNullableString(m.get("name"));
        List<String> lore = asStringList(m.get("lore"), List.of());
        boolean glint = asBool(m.get("glint"), false);
        List<String> leftCmds  = asStringList(m.get("left_click_commands"), List.of());
        List<String> rightCmds = asStringList(m.get("right_click_commands"), List.of());

        // Slots: prefer 'slots' list, fall back to single 'slot'
        List<Integer> slots;
        if (m.containsKey("slots")) {
            slots = asIntList(m.get("slots"));
        } else {
            int s = (int) asLong(m.get("slot"), 0);
            slots = List.of(s);
        }
        if (slots.isEmpty()) slots = List.of(0);

        return new MenuItem(slots, material, displayName, lore, glint, leftCmds, rightCmds);
    }

    // ── Dumping ──────────────────────────────────────────────────────────────

    private static Map<String, Object> dumpMenu(MenuDefinition def) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("menu_title", def.menuTitle());
        map.put("size", def.size());
        if (def.openCommand() != null) map.put("open_command", def.openCommand());
        map.put("visibility", def.visibility().toYaml());

        Map<String, Object> items = new LinkedHashMap<>();
        for (Map.Entry<String, MenuItem> e : def.items().entrySet()) {
            items.put(e.getKey(), dumpItem(e.getValue()));
        }
        map.put("items", items);
        return map;
    }

    private static Map<String, Object> dumpItem(MenuItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", item.material());
        if (item.slots().size() == 1) {
            m.put("slot", item.primarySlot());
        } else {
            m.put("slots", new ArrayList<>(item.slots()));
        }
        if (item.displayName() != null) m.put("display_name", item.displayName());
        if (!item.lore().isEmpty()) m.put("lore", new ArrayList<>(item.lore()));
        if (item.glint()) m.put("glint", true);
        if (!item.leftClickCommands().isEmpty())  m.put("left_click_commands",  new ArrayList<>(item.leftClickCommands()));
        if (!item.rightClickCommands().isEmpty()) m.put("right_click_commands", new ArrayList<>(item.rightClickCommands()));
        return m;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> coerceMap(Object o) {
        if (o instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static List<Integer> asIntList(Object o) {
        if (!(o instanceof List<?> raw)) return List.of();
        List<Integer> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Number n) out.add(n.intValue());
            else if (item instanceof String s) {
                try { out.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return out;
    }

    private static List<String> asStringList(Object o, List<String> fallback) {
        if (!(o instanceof List<?> raw)) return fallback;
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) out.add(item == null ? "" : String.valueOf(item));
        return List.copyOf(out);
    }

    private static long asLong(Object o, long fallback) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static boolean asBool(Object o, boolean fallback) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s.trim());
        return fallback;
    }

    private static String asString(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o);
    }

    private static String asNullableString(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return (s.isBlank() || "null".equals(s)) ? null : s;
    }
}
