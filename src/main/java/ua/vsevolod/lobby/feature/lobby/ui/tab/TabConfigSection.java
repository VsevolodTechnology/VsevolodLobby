package ua.vsevolod.lobby.feature.lobby.ui.tab;

import ua.vsevolod.lobby.feature.admin.config.ConfigSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class TabConfigSection implements ConfigSection<TabConfig> {

    public static final TabConfigSection INSTANCE = new TabConfigSection();

    private static final TabConfig DEFAULTS = new TabConfig(
            100L,
            "HH:mm",
            List.of(
                    "",
                    "&#FF9700&lOVERDYN",
                    "",
                    "    &#FFF2E0Пинг: &a{ping}мс &7{mspt}    ",
                    "",
                    "&#FF9700↶ &#FFF2E0Список игроков &#FF9700↷",
                    ""
            ),
            List.of(
                    "",
                    ""
            ),
            " &#FFF2E0MSPT: &e{mspt}"
    );

    private static final String TEMPLATE = """
            # ====================================================
            # Tab list (player list header & footer) configuration
            # ====================================================
            # Edit this file then run /reload to apply without restarting the server.
            #
            # Placeholders inside header/footer lines:
            #   {ping}    — receiving player's ping (ms)
            #   {online}  — total online players
            #   {time}    — server local time formatted by `time-format` below
            #   {mspt}    — for BYPASS players: rendered via `mspt-bypass-template`;
            #               for others: empty string
            #
            # NOTE: {player} is intentionally NOT supported in the tab list — the renderer
            # groups players with identical (bypass, ping-bucket) text into one packet send
            # to keep MSPT low. Player names go into the player-info entries themselves.

            update-interval-ms: 100
            time-format: "HH:mm"

            header:
              - ""
              - "&#FF9700&lOVERDYN"
              - ""
              - "    &#FFF2E0Пинг: &a{ping}мс &7{mspt}    "
              - ""
              - "&#FF9700↶ &#FFF2E0Список игроков &#FF9700↷"
              - ""
            footer:
              - ""
              - ""

            # Rendered only for BYPASS users (ops). {mspt} substituted at runtime.
            mspt-bypass-template: " &#FFF2E0MSPT: &e{mspt}"
            """;

    private final AtomicReference<TabConfig> current = new AtomicReference<>(DEFAULTS);
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private TabConfigSection() {}

    public TabConfig current() {
        return current.get();
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    @Override
    public String name() {
        return "tab";
    }

    @Override
    public String templateYaml() {
        return TEMPLATE;
    }

    @Override
    public TabConfig parse(Map<String, Object> yaml) {
        long interval = asLong(yaml.get("update-interval-ms"), DEFAULTS.updateIntervalMs());
        String time = asString(yaml.get("time-format"), DEFAULTS.timeFormat());
        List<String> header = asStringList(yaml.get("header"), DEFAULTS.header());
        List<String> footer = asStringList(yaml.get("footer"), DEFAULTS.footer());
        String mspt = asString(yaml.get("mspt-bypass-template"), DEFAULTS.msptBypassTemplate());
        return new TabConfig(interval, time, header, footer, mspt);
    }

    @Override
    public void apply(TabConfig snapshot) {
        current.set(snapshot);
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private static long asLong(Object o, long fallback) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static String asString(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o);
    }

    private static List<String> asStringList(Object o, List<String> fallback) {
        if (!(o instanceof List<?> raw)) return fallback;
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            out.add(item == null ? "" : String.valueOf(item));
        }
        return List.copyOf(out);
    }
}
