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
            # Hotbar items given on join
            # ====================================================
            # Edit then /reload to apply live.
            #
            # Per item:
            #   id         — stable identifier; stored on the ItemStack tag so the listener can find
            #                the definition on click.
            #   slot       — hotbar slot (0-8 typical; full inventory 0-35)
            #   material   — minecraft material name (compass, jukebox, paper, …)
            #                Accepts "compass" or "minecraft:compass".
            #   name       — display name (Adventure legacy & codes)
            #   lore       — list of lines below the name
            #   glint      — enchanted shimmer
            #   condition  — always | bypass-only | non-bypass
            #                Controls WHO gets this item on join.
            #   actions:
            #     right    — fires on right-click
            #     left     — fires on left-click (drop / attack)
            #
            # Action types (same set as NPCs):
            #   none, run-command, open-menu, parkour-start
            # `execute-as-op: true` on `run-command` runs as permission level 4 for that call.

            items:
              - id: mode-selector
                slot: 4
                material: compass
                name: "&#F1BB58&lВыбор режима"
                lore:
                  - "&7Открывает меню выбора"
                  - "&7доступных режимов."
                  - ""
                  - "&e➥ Нажмите, чтобы открыть"
                glint: false
                condition: always
                actions:
                  right: { type: open-menu, target: mode-selector }
                  left:  { type: none }
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
