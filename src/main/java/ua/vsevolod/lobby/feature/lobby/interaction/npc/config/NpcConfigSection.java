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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class NpcConfigSection implements ConfigSection<NpcsConfig> {

    public static final NpcConfigSection INSTANCE = new NpcConfigSection();

    private static final NpcsConfig DEFAULTS = new NpcsConfig(List.of(
            new NpcDefinition(
                    "mode-selector",
                    new NpcPosition(0.5, 77.0, -29.5, 0f, 0f),
                    "&6&lВыбор режима",
                    "&7Нажмите, чтобы открыть меню",
                    null, false, null, true,
                    new NpcAction("open-menu", "mode-selector", false),
                    NpcAction.NONE
            ),
            new NpcDefinition(
                    "parkour",
                    new NpcPosition(6.5, 79.0, -4.5, 52f, 0f),
                    null,
                    "Проверь свою реакцию, точность и контроль\nможешь дойти до конца и не упасть?",
                    "Dream", true, "gold", true,
                    new NpcAction("parkour-start", "", false),
                    NpcAction.NONE
            )
    ));

    private static final String TEMPLATE = """
            # ====================================================
            # NPC list
            # ====================================================
            # Edit this file then run /reload (or use /npc <subcommand> in-game to edit live).
            #
            # Each NPC has:
            #   id                — stable identifier (don't rename; delete and re-add instead)
            #   position          — x/y/z/yaw/pitch in the lobby world
            #   name              — text floating above (Adventure legacy colour codes, &-codes & hex)
            #                       set to null to hide the name entirely
            #   description       — second line below the name (or null)
            #   skin              — null, or one of:
            #                         * "Notch"               — mojang username lookup
            #                         * "url:https://..."     — texture URL, signed via mineskin.org
            #                         * "https://..."         — same as above (url: prefix optional)
            #                         * "value:<base64>"      — pre-baked texture value
            #                         * "value:<base64>;sig:<base64>" — full signed pair
            #   glowing           — outline glow (true/false)
            #   glow-color        — colour of the glow when glowing=true. One of the Adventure
            #                       NamedTextColor names: black, dark_blue, dark_green, dark_aqua,
            #                       dark_red, dark_purple, gold, gray, dark_gray, blue, green,
            #                       aqua, red, light_purple, yellow, white. null/empty → white.
            #   visible           — false keeps the NPC in the config but does not spawn it
            #   actions.right     — fires on right-click
            #   actions.left      — fires on left-click (attack)
            #
            # Action types:
            #   none              — do nothing
            #   run-command       — execute `target` as a chat command
            #                       (`execute-as-op: true` raises permission level to 4 just for that call)
            #   open-menu         — open menu by `target` id (Phase 4 wires more menus; for now: mode-selector)
            #   parkour-start     — start the parkour run for the player

            npcs:
              - id: mode-selector
                position: { x: 0.5, y: 77.0, z: -29.5, yaw: 0.0, pitch: 0.0 }
                name: "&6&lВыбор режима"
                description: "&7Нажмите, чтобы открыть меню"
                skin: null
                glowing: false
                glow-color: null
                visible: true
                actions:
                  right: { type: open-menu, target: mode-selector, execute-as-op: false }
                  left:  { type: none }

              - id: parkour
                position: { x: 6.5, y: 79.0, z: -4.5, yaw: 52.0, pitch: 0.0 }
                name: null
                description: "Проверь свою реакцию, точность и контроль\\nможешь дойти до конца и не упасть?"
                skin: Dream
                glowing: true
                glow-color: gold
                visible: true
                actions:
                  right: { type: parkour-start }
                  left:  { type: none }
            """;

    private final AtomicReference<NpcsConfig> current = new AtomicReference<>(DEFAULTS);
    private final List<Consumer<NpcsConfig>> listeners = new CopyOnWriteArrayList<>();

    private NpcConfigSection() {}

    public NpcsConfig current() {
        return current.get();
    }

    /**
     * Subscribe to snapshot swaps (both initial {@link #apply} and subsequent reloads).
     * Listeners run synchronously on the thread that called {@code apply()} — keep them fast.
     */
    public void addListener(Consumer<NpcsConfig> listener) {
        listeners.add(listener);
    }

    @Override
    public String name() {
        return "npcs";
    }

    @Override
    public String templateYaml() {
        return TEMPLATE;
    }

    @Override
    public NpcsConfig parse(Map<String, Object> yaml) {
        Object raw = yaml.get("npcs");
        if (!(raw instanceof List<?> list)) {
            return new NpcsConfig(List.of());
        }
        List<NpcDefinition> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            try {
                out.add(parseDefinition(coerceMap(m)));
            } catch (Exception e) {
                System.err.println("[NpcConfigSection] Skipping malformed NPC entry: " + e.getMessage());
            }
        }
        return new NpcsConfig(out);
    }

    @Override
    public void apply(NpcsConfig snapshot) {
        current.set(snapshot);
        for (Consumer<NpcsConfig> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (Throwable t) {
                System.err.println("[NpcConfigSection] Listener failed: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    /**
     * Persists the supplied config to {@code config/npcs.yml} and updates the in-memory snapshot.
     * Called by NPC edit commands; use {@link ConfigManager#reloadAll()} for file-driven edits.
     */
    public synchronized void saveAndApply(NpcsConfig snapshot) throws IOException {
        Path file = ConfigManager.CONFIG_DIR.resolve(name() + ".yml");
        Files.createDirectories(ConfigManager.CONFIG_DIR);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);

        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> npcs = new ArrayList<>();
        for (NpcDefinition def : snapshot.npcs()) {
            npcs.add(dumpDefinition(def));
        }
        root.put("npcs", npcs);

        String yaml = new Yaml(opts).dump(root);
        Files.writeString(tmp, yaml, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        apply(snapshot);
    }

    private static NpcDefinition parseDefinition(Map<String, Object> m) {
        String id = asString(m.get("id"), null);
        Map<String, Object> pos = coerceMap(m.get("position"));
        NpcPosition position = new NpcPosition(
                asDouble(pos.get("x"), 0),
                asDouble(pos.get("y"), 0),
                asDouble(pos.get("z"), 0),
                (float) asDouble(pos.get("yaw"), 0),
                (float) asDouble(pos.get("pitch"), 0)
        );
        String name = asNullableString(m.get("name"));
        String description = asNullableString(m.get("description"));
        String skin = asNullableString(m.get("skin"));
        boolean glowing = asBool(m.get("glowing"), false);
        String glowColor = asNullableString(m.get("glow-color"));
        boolean visible = asBool(m.get("visible"), true);
        Map<String, Object> actions = coerceMap(m.get("actions"));
        NpcAction right = parseAction(coerceMap(actions.get("right")));
        NpcAction left  = parseAction(coerceMap(actions.get("left")));
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, visible, right, left);
    }

    private static NpcAction parseAction(Map<String, Object> m) {
        if (m.isEmpty()) return NpcAction.NONE;
        String type = asString(m.get("type"), "none").toLowerCase();
        String target = asString(m.get("target"), "");
        boolean asOp = asBool(m.get("execute-as-op"), false);
        return new NpcAction(type, target, asOp);
    }

    private static Map<String, Object> dumpDefinition(NpcDefinition def) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", def.id());

        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("x", def.position().x());
        pos.put("y", def.position().y());
        pos.put("z", def.position().z());
        pos.put("yaw", (double) def.position().yaw());
        pos.put("pitch", (double) def.position().pitch());
        map.put("position", pos);

        map.put("name", def.name());
        map.put("description", def.description());
        map.put("skin", def.skin());
        map.put("glowing", def.glowing());
        map.put("glow-color", def.glowColor());
        map.put("visible", def.visible());

        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("right", dumpAction(def.rightAction()));
        actions.put("left",  dumpAction(def.leftAction()));
        map.put("actions", actions);

        return map;
    }

    private static Map<String, Object> dumpAction(NpcAction a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", a.type());
        if (a.target() != null && !a.target().isEmpty()) map.put("target", a.target());
        if (a.executeAsOp()) map.put("execute-as-op", true);
        return map;
    }

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
        return s.isBlank() ? null : s;
    }
}
