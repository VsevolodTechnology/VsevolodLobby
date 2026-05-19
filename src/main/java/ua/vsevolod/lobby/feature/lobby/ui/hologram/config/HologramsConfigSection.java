package ua.vsevolod.lobby.feature.lobby.ui.hologram.config;

import ua.vsevolod.lobby.feature.admin.config.ConfigSection;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Config section for static lobby holograms (holograms.yml).
 *
 * <pre>
 * holograms:
 *   parkour:
 *     position:
 *       x: 6.5
 *       y: 81.2
 *       z: -4.5
 *     lines:
 *       - "&#F1BB58&lПАРКУР"
 *       - "&#FFF2E0Проверь свою реакцию, точность и контроль"
 *       - "&#FFF2E0можешь дойти до конца и не упасть?"
 *     billboard: fixed        # center | vertical | horizontal | fixed
 *     scale: 0.8              # uniform; or {x: 1.0, y: 1.0, z: 1.0}
 *     shadow: true
 *     see_through: true
 *     alignment: center       # left | center | right
 *     background: '0x1C1C1E'  # packed ARGB hex or integer, 0 = none
 *     use_default_background: true
 *     line_spacing: 0.25
 * </pre>
 */
public final class HologramsConfigSection implements ConfigSection<HologramsConfig> {

    public static final HologramsConfigSection INSTANCE = new HologramsConfigSection();

    private static final HologramsConfig DEFAULTS = new HologramsConfig(Map.of(
            "parkour", new HologramDefinition(
                    "parkour",
                    6.5, 81.2, -4.5,
                    List.of(
                            "&#F1BB58&lП&#F1B958&lА&#F1B658&lР&#F1B458&lК&#F1B158&lУ&#F1AF58&lР",
                            "&#FFF2E0Проверь свою реакцию, точность и контроль",
                            "&#FFF2E0можешь дойти до конца и не упасть?"
                    ),
                    "center",
                    0.8, 0.8, 0.8,
                    true, true, "center",
                    0x1C1C1E, true, 0.25
            )
    ));

    private static final String TEMPLATE = """
            # ====================================================
            # Статичные голограммы лобби — holograms.yml
            # ====================================================
            # После изменения файла выполни /reload.
            #
            # Каждая голограмма — это элемент карты с уникальным ключом (идентификатором).
            #
            # Поля голограммы:
            #   position      — координаты в мире лобби (x, y, z — дробные числа).
            #   lines         — список строк текста. Поддерживают & коды и &#RRGGBB HEX-цвета.
            #                   Все строки объединяются в одну объект text_display (оптимально).
            #   billboard     — режим вращения голограммы:
            #                     center     — всегда смотрит на камеру игрока (рекомендуется)
            #                     vertical   — вращается только по горизонтали (следит за игроком)
            #                     horizontal — вращается только по вертикали
            #                     fixed      — неподвижна (фиксированное направление)
            #   scale         — масштаб текста. Одно число = одинаковый масштаб по всем осям.
            #                   Можно задать отдельно: {x: 1.0, y: 1.2, z: 1.0}
            #   shadow        — true = тень под текстом, false = без тени.
            #   see_through   — true = текст виден сквозь блоки, false = перекрывается блоками.
            #   alignment     — выравнивание текста: left | center | right
            #   use_default_background — true = стандартный тёмный фон Minecraft (перекрывает background).
            #   background    — цвет фона в формате ARGB: '0xAARRGGBB' или число. 0 = прозрачный.
            #                   Пример: '0x1C1C1E' = тёмно-серый без прозрачности.
            #                   Пример с прозрачностью: '0x801C1C1E' (80 = ~50% прозрачность).
            #   line_spacing  — межстрочный интервал (дробное число, например 0.25).

            holograms:
              # Голограмма над NPC паркура
              parkour:
                position:
                  x: 6.5   # Координата X
                  y: 81.2  # Координата Y (высота над землёй)
                  z: -4.5  # Координата Z
                lines:
                  - "&#F1BB58&lП&#F1B958&lА&#F1B658&lР&#F1B458&lК&#F1B158&lУ&#F1AF58&lР"  # Радужный заголовок
                  - "&#FFF2E0Проверь свою реакцию, точность и контроль"   # Описание строка 1
                  - "&#FFF2E0можешь дойти до конца и не упасть?"          # Описание строка 2
                billboard: center        # Следит за камерой игрока
                scale: 0.8              # 80% от стандартного размера
                shadow: true            # Тень под текстом
                see_through: true       # Виден сквозь блоки
                alignment: center       # Выравнивание по центру
                use_default_background: true  # Стандартный тёмный фон Minecraft
                background: '0x1C1C1E'  # Цвет фона (используется если use_default_background: false)
                line_spacing: 0.25      # Межстрочный интервал
            """;

    private final AtomicReference<HologramsConfig> current = new AtomicReference<>(DEFAULTS);
    private final List<Consumer<HologramsConfig>> listeners = new CopyOnWriteArrayList<>();

    private HologramsConfigSection() {}

    public HologramsConfig current() { return current.get(); }

    public void addListener(Consumer<HologramsConfig> listener) { listeners.add(listener); }

    @Override
    public String name() { return "holograms"; }

    @Override
    public String templateYaml() { return TEMPLATE; }

    @Override
    public HologramsConfig parse(Map<String, Object> yaml) {
        Object raw = yaml.get("holograms");
        if (!(raw instanceof Map<?, ?> mapRaw)) return new HologramsConfig(Map.of());

        Map<String, Object> holoMap = coerceMap(mapRaw);
        Map<String, HologramDefinition> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : holoMap.entrySet()) {
            try {
                HologramDefinition def = parseDefinition(entry.getKey(), coerceMap(entry.getValue()));
                out.put(def.id(), def);
            } catch (Exception e) {
                System.err.println("[HologramsConfig] Skipping malformed hologram '"
                        + entry.getKey() + "': " + e.getMessage());
            }
        }
        return new HologramsConfig(Collections.unmodifiableMap(out));
    }

    @Override
    public void apply(HologramsConfig snapshot) {
        current.set(snapshot);
        for (Consumer<HologramsConfig> listener : listeners) {
            try { listener.accept(snapshot); }
            catch (Throwable t) {
                System.err.println("[HologramsConfig] Listener failed: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private static HologramDefinition parseDefinition(String id, Map<String, Object> m) {
        Map<String, Object> pos = coerceMap(m.get("position"));
        double x = asDouble(pos.get("x"), 0);
        double y = asDouble(pos.get("y"), 0);
        double z = asDouble(pos.get("z"), 0);

        List<String> lines = asStringList(m.get("lines"));
        String billboard   = asString(m.get("billboard"), "center").toLowerCase().trim();
        boolean shadow     = asBool(m.get("shadow"), false);
        boolean seeThrough = asBool(m.get("see_through"), true);
        String alignment   = asString(m.get("alignment"), "center").toLowerCase().trim();
        boolean useDefault = asBool(m.get("use_default_background"), false);
        int bg             = parseColor(m.get("background"), 0);
        double spacing     = asDouble(m.get("line_spacing"), 0.25);

        // scale: single number OR map {x, y, z}
        double sx, sy, sz;
        Object scaleRaw = m.get("scale");
        if (scaleRaw instanceof Map<?, ?> sm) {
            Map<String, Object> sm2 = coerceMap(sm);
            sx = asDouble(sm2.get("x"), 1.0);
            sy = asDouble(sm2.get("y"), 1.0);
            sz = asDouble(sm2.get("z"), 1.0);
        } else {
            double uniform = asDouble(scaleRaw, 1.0);
            sx = sy = sz = uniform;
        }

        return new HologramDefinition(id, x, y, z, lines, billboard,
                sx, sy, sz, shadow, seeThrough, alignment, bg, useDefault, spacing);
    }

    private static int parseColor(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equals(s) || "0".equals(s)) return 0;
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) return (int) Long.parseLong(s.substring(2), 16);
            if (s.startsWith("#")) return (int) Long.parseLong(s.substring(1), 16);
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {}
        return fallback;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
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
        if (o == null) return fallback;
        String s = String.valueOf(o).trim();
        return (s.isBlank() || "null".equals(s)) ? fallback : s;
    }

    private static List<String> asStringList(Object o) {
        if (!(o instanceof List<?> raw)) return List.of();
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) out.add(item == null ? "" : String.valueOf(item));
        return List.copyOf(out);
    }
}
