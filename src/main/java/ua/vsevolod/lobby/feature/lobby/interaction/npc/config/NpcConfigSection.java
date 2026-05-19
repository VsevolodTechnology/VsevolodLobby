package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

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
 * Config section for NPCs — DeluxeMenus-inspired YAML format.
 *
 * <pre>
 * npcs:
 *   mode-selector:
 *     position:
 *       x: 0.5
 *       y: 77.0
 *       z: -29.5
 *       yaw: 0.0
 *       pitch: 0.0
 *     display_name: '&amp;6&amp;lВыбор режима'
 *     description: '&amp;7Нажмите, чтобы открыть меню'
 *     skin:
 *       type: username       # username | url | texture
 *       value: Notch
 *     glowing: false
 *     glow_color: null
 *     visible: true
 *     right_click_commands:
 *       - '[menu] mode-selector'
 *       - '[message] &aВы открыли меню!'
 *     left_click_commands: []
 *
 *   parkour-npc:
 *     position: { x: 6.5, y: 79.0, z: -4.5, yaw: 52.0, pitch: 0.0 }
 *     display_name: null
 *     description: 'Паркур — проверь реакцию и точность!'
 *     skin:
 *       type: url
 *       value: 'https://textures.minecraft.net/texture/abc123...'
 *     glowing: true
 *     glow_color: gold
 *     visible: true
 *     right_click_commands:
 *       - '[parkour]'
 *       - '[player] server adventure world'
 *     left_click_commands: []
 * </pre>
 *
 * <p>Skin type {@code texture} supports an optional {@code signature} sub-key for pre-signed skins.</p>
 */
public final class NpcConfigSection implements ConfigSection<NpcsConfig> {

    public static final NpcConfigSection INSTANCE = new NpcConfigSection();

    private static final NpcsConfig DEFAULTS = new NpcsConfig(List.of(
            new NpcDefinition(
                    "mode-selector",
                    new NpcPosition(0.5, 77.0, -29.5, 0f, 0f),
                    "&6&lВыбор режима",
                    "&7Нажмите, чтобы открыть меню",
                    null, false, null, true,
                    List.of("[menu] mode-selector"),
                    List.of("[menu] mode-selector")
            ),
            new NpcDefinition(
                    "parkour",
                    new NpcPosition(6.5, 79.0, -4.5, 52f, 0f),
                    null,
                    "Проверь свою реакцию, точность и контроль\nможешь дойти до конца и не упасть?",
                    "Dream", true, "gold", true,
                    List.of("[parkour]"),
                    List.of()
            )
    ));

    private static final String TEMPLATE = """
            # ====================================================
            # Список NPC (неигровых персонажей) — npcs.yml
            # ====================================================
            # После изменения файла выполни /reload или используй /npc в игре.
            #
            # Каждый NPC — это элемент карты с уникальным ключом (идентификатором).
            #
            # Поля NPC:
            #   display_name      — имя над головой NPC (& коды и &#RRGGBB HEX).
            #                       null = имя не отображается.
            #   description       — вторая строка под именем (\\n = перенос строки).
            #                       null = не отображается.
            #   position          — координаты в мире лобби:
            #                         x, y, z  — позиция (дробные числа)
            #                         yaw      — поворот по горизонтали (0-360, 0 = смотрит на юг)
            #                         pitch    — наклон головы (-90 = вверх, 90 = вниз, 0 = прямо)
            #   skin              — скин NPC. null = нет скина (невидимый манекен).
            #                       Варианты:
            #                         type: username  →  value: НикнеймИгрока
            #                           (скин берётся с Mojang по нику)
            #                         type: url       →  value: 'https://textures.minecraft.net/texture/...'
            #                           (скин по прямой ссылке на текстуру)
            #                         type: texture   →  value: '<base64-строка>'
            #                                            signature: '<base64-строка>'  (опционально)
            #                           (скин из raw base64-данных)
            #   glowing           — true = светящийся контур вокруг NPC, false = без контура.
            #   glow_color        — цвет контура (только если glowing: true).
            #                       Доступные цвета: black, dark_blue, dark_green, dark_aqua,
            #                       dark_red, dark_purple, gold, gray, dark_gray, blue, green,
            #                       aqua, red, light_purple, yellow, white.
            #                       null = белый.
            #   visible           — true = NPC спавнится в мире, false = только в конфиге
            #                       (можно временно скрыть без удаления из файла).
            #   right_click_commands — команды при нажатии ПКМ на NPC (список строк).
            #   left_click_commands  — команды при нажатии ЛКМ (атака) на NPC (список строк).
            #
            # Префиксы команд (все поддерживают многосложные аргументы):
            #   [player] <команда>    — выполнить /команда от имени игрока
            #   [console] <команда>   — выполнить /команда от имени консоли (с правами оператора)
            #   [op] <команда>        — временно дать права оператора и выполнить /команда
            #   [message] <текст>     — отправить игроку сообщение в чат
            #   [close]               — закрыть открытый инвентарь/меню
            #   [connect] <сервер>    — перенести игрока на другой сервер (имя из Velocity/BungeeCord)
            #   [menu] <id>           — открыть меню по идентификатору из menus.yml
            #   [parkour]             — запустить паркур для этого игрока
            #   [broadcast] <текст>   — показать сообщение всем онлайн-игрокам

            npcs:
              # NPC выбора режима игры
              mode-selector:
                position:
                  x: 0.5       # Координата X в мире лобби
                  y: 77.0      # Координата Y (высота)
                  z: -29.5     # Координата Z
                  yaw: 0.0     # Поворот (0 = смотрит на юг, 90 = запад, 180 = север, 270 = восток)
                  pitch: 0.0   # Наклон головы (0 = прямо, отрицательное = вверх)
                display_name: "&6&lВыбор режима"           # Имя над головой (&6 = золотой, &l = жирный)
                description: "&7Нажмите, чтобы открыть меню"  # Подсказка под именем (&7 = серый)
                skin: null     # Скин NPC. null = без скина
                glowing: false # Светящийся контур: false = выключен
                glow_color: null  # Цвет контура (при glowing: true)
                visible: true  # true = NPC видим в мире
                right_click_commands:
                  - "[menu] mode-selector"   # ПКМ — открыть меню mode-selector
                left_click_commands:
                  - "[menu] mode-selector"   # ЛКМ — тоже открыть меню

              # NPC паркура
              parkour:
                position:
                  x: 6.5
                  y: 79.0
                  z: -4.5
                  yaw: 52.0
                  pitch: 0.0
                display_name: null   # Имя скрыто (null)
                description: "Проверь свою реакцию, точность и контроль\\nможешь дойти до конца и не упасть?"
                skin:
                  type: username   # Тип скина: по нику игрока
                  value: Dream     # Ник игрока, чей скин использовать
                glowing: true      # Включить светящийся контур
                glow_color: gold   # Цвет контура: золотой
                visible: true
                right_click_commands:
                  - "[parkour]"    # ПКМ — начать паркур
                left_click_commands: []  # ЛКМ — ничего
            """;

    private final AtomicReference<NpcsConfig> current = new AtomicReference<>(DEFAULTS);
    private final List<Consumer<NpcsConfig>> listeners = new CopyOnWriteArrayList<>();

    private NpcConfigSection() {}

    public NpcsConfig current() { return current.get(); }

    public void addListener(Consumer<NpcsConfig> listener) { listeners.add(listener); }

    @Override
    public String name() { return "npcs"; }

    @Override
    public String templateYaml() { return TEMPLATE; }

    @Override
    public NpcsConfig parse(Map<String, Object> yaml) {
        Object raw = yaml.get("npcs");

        // Support both new Map format (keyed by id) and old List format
        if (raw instanceof Map<?, ?> mapRaw) {
            Map<String, Object> npcMap = coerceMap(mapRaw);
            List<NpcDefinition> out = new ArrayList<>();
            for (Map.Entry<String, Object> entry : npcMap.entrySet()) {
                try {
                    out.add(parseDefinition(entry.getKey(), coerceMap(entry.getValue())));
                } catch (Exception e) {
                    System.err.println("[NpcConfig] Skipping malformed NPC '" + entry.getKey()
                            + "': " + e.getMessage());
                }
            }
            return new NpcsConfig(out);
        }

        // Legacy List format (backward compat)
        if (raw instanceof List<?> list) {
            List<NpcDefinition> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                try {
                    Map<String, Object> mm = coerceMap(m);
                    String id = asString(mm.get("id"), null);
                    if (id == null) continue;
                    out.add(parseDefinitionLegacy(id, mm));
                } catch (Exception e) {
                    System.err.println("[NpcConfig] Skipping malformed NPC: " + e.getMessage());
                }
            }
            return new NpcsConfig(out);
        }

        return new NpcsConfig(List.of());
    }

    @Override
    public void apply(NpcsConfig snapshot) {
        current.set(snapshot);
        for (Consumer<NpcsConfig> listener : listeners) {
            try { listener.accept(snapshot); }
            catch (Throwable t) {
                System.err.println("[NpcConfig] Listener failed: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    public synchronized void saveAndApply(NpcsConfig snapshot) throws IOException {
        Path file = ConfigManager.CONFIG_DIR.resolve(name() + ".yml");
        Files.createDirectories(ConfigManager.CONFIG_DIR);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> npcs = new LinkedHashMap<>();
        for (NpcDefinition def : snapshot.npcs()) npcs.put(def.id(), dumpDefinition(def));
        root.put("npcs", npcs);

        Files.writeString(tmp, new Yaml(opts).dump(root), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        apply(snapshot);
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private static NpcDefinition parseDefinition(String id, Map<String, Object> m) {
        NpcPosition position = parsePosition(coerceMap(m.get("position")));
        String name        = asNullableString(m.get("display_name"));
        // Legacy key support
        if (name == null) name = asNullableString(m.get("name"));
        String description = asNullableString(m.get("description"));
        String skin        = parseSkin(m.get("skin"));
        boolean glowing    = asBool(m.get("glowing"), false);
        String glowColor   = asNullableString(m.get("glow_color"));
        // Legacy key support
        if (glowColor == null) glowColor = asNullableString(m.get("glow-color"));
        boolean visible    = asBool(m.get("visible"), true);
        List<String> right = asStringList(m.get("right_click_commands"), List.of());
        List<String> left  = asStringList(m.get("left_click_commands"), List.of());
        return new NpcDefinition(id, position, name, description, skin,
                glowing, glowColor, visible, right, left);
    }

    /** Parse legacy list-format NPC (actions.right / actions.left). */
    private static NpcDefinition parseDefinitionLegacy(String id, Map<String, Object> m) {
        NpcPosition position = parsePosition(coerceMap(m.get("position")));
        String name        = asNullableString(m.get("name"));
        String description = asNullableString(m.get("description"));
        String skin        = asNullableString(m.get("skin"));
        boolean glowing    = asBool(m.get("glowing"), false);
        String glowColor   = asNullableString(m.get("glow-color"));
        boolean visible    = asBool(m.get("visible"), true);
        Map<String, Object> actions = coerceMap(m.get("actions"));
        List<String> right = legacyActionToCommands(coerceMap(actions.get("right")));
        List<String> left  = legacyActionToCommands(coerceMap(actions.get("left")));
        return new NpcDefinition(id, position, name, description, skin,
                glowing, glowColor, visible, right, left);
    }

    private static List<String> legacyActionToCommands(Map<String, Object> m) {
        if (m.isEmpty()) return List.of();
        String type   = asString(m.get("type"), "none").toLowerCase();
        String target = asString(m.get("target"), "");
        boolean asOp  = asBool(m.get("execute-as-op"), false);
        return switch (type) {
            case "none"            -> List.of();
            case "open-menu"       -> List.of("[menu] " + target);
            case "parkour-start"   -> List.of("[parkour]");
            case "transfer-server" -> List.of("[connect] " + target);
            case "run-command"     -> asOp
                    ? List.of("[op] " + target)
                    : List.of("[player] " + target);
            default                -> List.of("[player] " + target);
        };
    }

    private static NpcPosition parsePosition(Map<String, Object> pos) {
        return new NpcPosition(
                asDouble(pos.get("x"), 0),
                asDouble(pos.get("y"), 0),
                asDouble(pos.get("z"), 0),
                (float) asDouble(pos.get("yaw"), 0),
                (float) asDouble(pos.get("pitch"), 0)
        );
    }

    /**
     * Parse skin — supports structured map format AND legacy plain string.
     * <pre>
     * # Structured:
     * skin:
     *   type: username | url | texture
     *   value: ...
     *   signature: ...   # only for type=texture
     *
     * # Legacy plain string (still supported):
     * skin: Notch
     * skin: "url:https://..."
     * skin: "value:base64;sig:base64"
     * </pre>
     */
    private static String parseSkin(Object raw) {
        if (raw == null) return null;

        // Structured map format (new)
        if (raw instanceof Map<?, ?>) {
            Map<String, Object> m = coerceMap(raw);
            String type  = asString(m.get("type"), "username").toLowerCase().trim();
            String value = asNullableString(m.get("value"));
            if (value == null) return null;

            return switch (type) {
                case "username" -> value;
                case "url"      -> value.startsWith("http") ? value : "url:" + value;
                case "texture"  -> {
                    String sig = asNullableString(m.get("signature"));
                    yield sig != null ? "value:" + value + ";sig:" + sig : "value:" + value;
                }
                default         -> value;
            };
        }

        // Legacy plain string
        String s = String.valueOf(raw).trim();
        return (s.isBlank() || "null".equals(s)) ? null : s;
    }

    // ── Dumping ──────────────────────────────────────────────────────────────

    private static Map<String, Object> dumpDefinition(NpcDefinition def) {
        Map<String, Object> map = new LinkedHashMap<>();

        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("x", def.position().x());
        pos.put("y", def.position().y());
        pos.put("z", def.position().z());
        pos.put("yaw",   (double) def.position().yaw());
        pos.put("pitch", (double) def.position().pitch());
        map.put("position", pos);

        map.put("display_name", def.name());
        map.put("description",  def.description());
        map.put("skin",         dumpSkin(def.skin()));
        map.put("glowing",      def.glowing());
        map.put("glow_color",   def.glowColor());
        map.put("visible",      def.visible());
        map.put("right_click_commands", new ArrayList<>(def.rightClickCommands()));
        map.put("left_click_commands",  new ArrayList<>(def.leftClickCommands()));

        return map;
    }

    private static Object dumpSkin(String skin) {
        if (skin == null) return null;
        // Write structured map format
        if (skin.startsWith("value:")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "texture");
            int sigIdx = skin.indexOf(";sig:");
            if (sigIdx >= 0) {
                m.put("value",     skin.substring(6, sigIdx));
                m.put("signature", skin.substring(sigIdx + 5));
            } else {
                m.put("value", skin.substring(6));
            }
            return m;
        }
        if (skin.startsWith("url:") || skin.startsWith("http")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type",  "url");
            m.put("value", skin.startsWith("url:") ? skin.substring(4) : skin);
            return m;
        }
        // Plain username
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",  "username");
        m.put("value", skin);
        return m;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> coerceMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static double asDouble(Object o, double fallback) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
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

    private static List<String> asStringList(Object o, List<String> fallback) {
        if (!(o instanceof List<?> raw)) return fallback;
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) out.add(item == null ? "" : String.valueOf(item));
        return List.copyOf(out);
    }
}
