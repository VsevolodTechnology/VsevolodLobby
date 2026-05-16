package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import ua.vsevolod.lobby.feature.admin.config.ConfigManager;
import ua.vsevolod.lobby.feature.admin.config.ConfigSection;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class MenusConfigSection implements ConfigSection<MenusConfig> {

    public static final MenusConfigSection INSTANCE = new MenusConfigSection();

    private static final MenusConfig DEFAULTS = new MenusConfig(buildDefaults());

    private static Map<String, MenuDefinition> buildDefaults() {
        Map<String, MenuDefinition> out = new LinkedHashMap<>();

        Map<String, MenuDecor> modeSelectorDecor = new LinkedHashMap<>();
        modeSelectorDecor.put("orange", new MenuDecor("orange_stained_glass_pane",
                List.of(1, 3, 5, 7, 9, 11, 15, 17, 18, 19, 25, 26, 27, 29, 33, 35, 37, 39, 41, 43)));
        modeSelectorDecor.put("black", new MenuDecor("black_stained_glass_pane",
                List.of(0, 2, 4, 6, 8, 10, 12, 13, 14, 16, 20, 24, 28, 30, 31, 32, 34, 36, 38, 40, 42, 44)));
        modeSelectorDecor.put("gray", new MenuDecor("gray_stained_glass_pane",
                List.of(21, 23)));

        out.put("mode-selector", new MenuDefinition(
                "mode-selector",
                "&8Выбор режима",
                5,
                MenuDefinition.Visibility.ALL,
                modeSelectorDecor,
                List.of(
                        new MenuItem(
                                22,
                                "diamond_pickaxe",
                                "&#E39966&lGrief 1.16",
                                List.of("&7Нажмите, чтобы подключиться"),
                                false,
                                new NpcAction("transfer-server", "grief.1.16x", false)
                        )
                )
        ));
        return out;
    }

    private static final String TEMPLATE = """
            # ====================================================
            # Chest menus configuration
            # ====================================================
            # Edit then /reload to apply live.
            #
            # Per menu (key = menu id, used by `open-menu` action and /menu open):
            #   title       — chest title (legacy & codes)
            #   rows        — 1..6
            #   visibility  — all | bypass-only
            #   decor       — named groups of slots filled with the same glass-pane material
            #   items       — clickable slots; each has slot, material, name, lore, glint, action
            #
            # Action types (same as NPCs/items):
            #   none, run-command, open-menu, parkour-start, transfer-server
            # `transfer-server` is a stub for Phase 4 — currently sends a chat message naming the
            # target. Real cross-server transfer ships in a later phase together with Velocity
            # plugin-channel wiring.

            menus:
              mode-selector:
                title: "&8Выбор режима"
                rows: 5
                visibility: all
                decor:
                  orange:
                    material: orange_stained_glass_pane
                    slots: [1, 3, 5, 7, 9, 11, 15, 17, 18, 19, 25, 26, 27, 29, 33, 35, 37, 39, 41, 43]
                  black:
                    material: black_stained_glass_pane
                    slots: [0, 2, 4, 6, 8, 10, 12, 13, 14, 16, 20, 24, 28, 30, 31, 32, 34, 36, 38, 40, 42, 44]
                  gray:
                    material: gray_stained_glass_pane
                    slots: [21, 23]
                items:
                  - slot: 22
                    material: diamond_pickaxe
                    name: "&#E39966&lGrief 1.16"
                    lore:
                      - "&7Нажмите, чтобы подключиться"
                    glint: false
                    action: { type: transfer-server, target: grief.1.16x }
            """;

    private final AtomicReference<MenusConfig> current = new AtomicReference<>(DEFAULTS);
    private final java.util.List<java.util.function.Consumer<MenusConfig>> listeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private MenusConfigSection() {}

    public MenusConfig current() {
        return current.get();
    }

    /** Subscribe to snapshot swaps. Listeners run synchronously after {@link #apply}. */
    public void addListener(java.util.function.Consumer<MenusConfig> listener) {
        listeners.add(listener);
    }

    @Override
    public String name() {
        return "menus";
    }

    @Override
    public String templateYaml() {
        return TEMPLATE;
    }

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
        for (java.util.function.Consumer<MenusConfig> listener : listeners) {
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
        for (MenuDefinition def : snapshot.menus().values()) {
            menus.put(def.id(), dumpMenu(def));
        }
        root.put("menus", menus);

        Files.writeString(tmp, new Yaml(opts).dump(root), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        apply(snapshot);
    }

    private static MenuDefinition parseMenu(String id, Map<String, Object> m) {
        String title = asString(m.get("title"), "&8" + id);
        int rows = (int) asLong(m.get("rows"), 3);
        MenuDefinition.Visibility visibility = MenuDefinition.Visibility.fromString(asString(m.get("visibility"), "all"));

        Map<String, MenuDecor> decor = new LinkedHashMap<>();
        Map<String, Object> decorRaw = coerceMap(m.get("decor"));
        for (Map.Entry<String, Object> e : decorRaw.entrySet()) {
            Map<String, Object> dm = coerceMap(e.getValue());
            String material = asString(dm.get("material"), "white_stained_glass_pane");
            List<Integer> slots = asIntList(dm.get("slots"));
            decor.put(e.getKey(), new MenuDecor(material, slots));
        }

        List<MenuItem> items = new ArrayList<>();
        Object itemsRaw = m.get("items");
        if (itemsRaw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> im)) continue;
                Map<String, Object> imap = coerceMap(im);
                int slot = (int) asLong(imap.get("slot"), 0);
                String material = asString(imap.get("material"), "stone");
                String name = asNullableString(imap.get("name"));
                List<String> lore = asStringList(imap.get("lore"), List.of());
                boolean glint = asBool(imap.get("glint"), false);
                NpcAction action = parseAction(coerceMap(imap.get("action")));
                items.add(new MenuItem(slot, material, name, lore, glint, action));
            }
        }

        return new MenuDefinition(id, title, rows, visibility, decor, items);
    }

    private static NpcAction parseAction(Map<String, Object> m) {
        if (m.isEmpty()) return NpcAction.NONE;
        String type = asString(m.get("type"), "none").toLowerCase();
        String target = asString(m.get("target"), "");
        boolean asOp = asBool(m.get("execute-as-op"), false);
        return new NpcAction(type, target, asOp);
    }

    private static Map<String, Object> dumpMenu(MenuDefinition def) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", def.title());
        map.put("rows", def.rows());
        map.put("visibility", def.visibility().toYaml());

        Map<String, Object> decor = new LinkedHashMap<>();
        for (Map.Entry<String, MenuDecor> e : def.decor().entrySet()) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("material", e.getValue().material());
            dm.put("slots", e.getValue().slots());
            decor.put(e.getKey(), dm);
        }
        map.put("decor", decor);

        List<Map<String, Object>> items = new ArrayList<>();
        for (MenuItem it : def.items()) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("slot", it.slot());
            im.put("material", it.material());
            if (it.name() != null) im.put("name", it.name());
            if (!it.lore().isEmpty()) im.put("lore", it.lore());
            if (it.glint()) im.put("glint", true);

            Map<String, Object> am = new LinkedHashMap<>();
            am.put("type", it.action().type());
            if (!it.action().target().isEmpty()) am.put("target", it.action().target());
            if (it.action().executeAsOp()) am.put("execute-as-op", true);
            im.put("action", am);
            items.add(im);
        }
        map.put("items", items);
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
