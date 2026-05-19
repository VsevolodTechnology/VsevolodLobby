package ua.vsevolod.lobby.feature.lobby.player.join.items;

import ua.vsevolod.lobby.feature.admin.config.ConfigSection;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class JoinItemsConfigSection implements ConfigSection<JoinItemsConfig> {

    public static final JoinItemsConfigSection INSTANCE = new JoinItemsConfigSection();

    private static final JoinItemsConfig DEFAULTS = new JoinItemsConfig(List.of(
            new JoinItemDefinition(
                    "mode-selector",
                    4,
                    "compass",
                    "&#F1BB58&lВыбор режима",
                    List.of(
                            "&7Открывает меню выбора",
                            "&7доступных режимов.",
                            "",
                            "&e➥ Нажмите, чтобы открыть"
                    ),
                    false,
                    JoinItemDefinition.Condition.ALWAYS,
                    new NpcAction("open-menu", "mode-selector", false),
                    NpcAction.NONE
            )
    ));

    private static final String TEMPLATE = """
            # ====================================================
            # Предметы, выдаваемые игроку при входе на лобби — join-items.yml
            # ====================================================
            # После изменения файла выполни /reload — применяется без перезапуска.
            #
            # Каждый элемент списка items — это один предмет в хотбаре.
            #
            # Поля предмета:
            #   id         — уникальный идентификатор предмета (латинские буквы и дефис).
            #                Используется внутренне; менять можно, но тогда старый предмет
            #                перестанет реагировать на клики до следующего входа игрока.
            #   slot       — слот в инвентаре (0-8 = хотбар; 0-35 = весь инвентарь).
            #   material   — название материала Minecraft (compass, jukebox, paper и т.д.).
            #                Принимает "compass" или "minecraft:compass" — оба варианта верны.
            #   name       — отображаемое название. Поддерживает &X-коды и &#RRGGBB HEX-цвета.
            #   lore       — список строк описания под названием. Пустой список [] = нет описания.
            #   glint      — true = зачарованное мерцание, false = без мерцания.
            #   condition  — кому выдаётся предмет при входе:
            #                  always       — всем игрокам
            #                  bypass-only  — только операторам (BYPASS_USERS)
            #                  non-bypass   — только обычным игрокам (не операторам)
            #   actions    — действия при кликах:
            #     right    — действие при нажатии ПКМ
            #     left     — действие при нажатии ЛКМ / атаке
            #
            # Типы действий (actions):
            #   none                           — ничего не делать
            #   run-command                    — выполнить команду
            #     target: "команда"            — текст команды (без /)
            #     execute-as-op: true/false    — выполнить с правами оператора
            #   open-menu                      — открыть меню
            #     target: "id-меню"            — идентификатор меню из menus.yml
            #   parkour-start                  — начать паркур

            items:
              - id: mode-selector     # Уникальный ID предмета
                slot: 4               # Слот 4 = центр хотбара (0-8)
                material: compass     # Материал предмета
                name: "&#F1BB58&lВыбор режима"  # Название (HEX-цвет + жирный)
                lore:
                  - "&7Открывает меню выбора"    # Строки описания (&7 = серый)
                  - "&7доступных режимов."
                  - ""                            # Пустая строка = разрыв
                  - "&e➥ Нажмите, чтобы открыть" # Подсказка (&e = жёлтый)
                glint: false          # Без зачарованного мерцания
                condition: always     # Выдаётся всем игрокам
                actions:
                  right: { type: open-menu, target: mode-selector }  # ПКМ — открыть меню mode-selector
                  left:  { type: none }                               # ЛКМ — ничего
            """;

    private final AtomicReference<JoinItemsConfig> current = new AtomicReference<>(DEFAULTS);

    private JoinItemsConfigSection() {}

    public JoinItemsConfig current() {
        return current.get();
    }

    @Override
    public String name() {
        return "join-items";
    }

    @Override
    public String templateYaml() {
        return TEMPLATE;
    }

    @Override
    public JoinItemsConfig parse(Map<String, Object> yaml) {
        Object raw = yaml.get("items");
        if (!(raw instanceof List<?> list)) return new JoinItemsConfig(List.of());
        List<JoinItemDefinition> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            try {
                out.add(parseDefinition(coerceMap(m)));
            } catch (Exception e) {
                System.err.println("[JoinItemsConfig] Skipping malformed item: " + e.getMessage());
            }
        }
        return new JoinItemsConfig(out);
    }

    @Override
    public void apply(JoinItemsConfig snapshot) {
        current.set(snapshot);
    }

    private static JoinItemDefinition parseDefinition(Map<String, Object> m) {
        String id = asString(m.get("id"), null);
        int slot = (int) asLong(m.get("slot"), 0);
        String material = asString(m.get("material"), "stone");
        String name = asNullableString(m.get("name"));
        List<String> lore = asStringList(m.get("lore"), List.of());
        boolean glint = asBool(m.get("glint"), false);
        JoinItemDefinition.Condition condition =
                JoinItemDefinition.Condition.fromString(asNullableString(m.get("condition")));

        Map<String, Object> actions = coerceMap(m.get("actions"));
        NpcAction right = parseAction(coerceMap(actions.get("right")));
        NpcAction left  = parseAction(coerceMap(actions.get("left")));

        return new JoinItemDefinition(id, slot, material, name, lore, glint, condition, right, left);
    }

    private static NpcAction parseAction(Map<String, Object> m) {
        if (m.isEmpty()) return NpcAction.NONE;
        String type = asString(m.get("type"), "none").toLowerCase();
        String target = asString(m.get("target"), "");
        boolean asOp = asBool(m.get("execute-as-op"), false);
        return new NpcAction(type, target, asOp);
    }

    private static Map<String, Object> coerceMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
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
        return s.isBlank() ? null : s;
    }

    private static List<String> asStringList(Object o, List<String> fallback) {
        if (!(o instanceof List<?> raw)) return fallback;
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) out.add(item == null ? "" : String.valueOf(item));
        return List.copyOf(out);
    }
}
