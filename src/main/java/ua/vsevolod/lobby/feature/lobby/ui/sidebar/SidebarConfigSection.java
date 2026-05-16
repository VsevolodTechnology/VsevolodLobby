package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import ua.vsevolod.lobby.feature.admin.config.ConfigSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class SidebarConfigSection implements ConfigSection<SidebarConfig> {

    public static final SidebarConfigSection INSTANCE = new SidebarConfigSection();

    private static final SidebarConfig DEFAULTS = new SidebarConfig(
            true,
            800L,
            "&7« {frame}&f &7»",
            List.of(
                    "&#FFB300&lO&#FFBE1A&lV&#FFC933&lE&#FFD44D&lR&#FFDF66&lD&#FFEA80&lY&#FFF599&lN",
                    "&#FFE066&lO&#FFD94D&lV&#FFD233&lE&#FFCB1A&lR&#FFC400&lD&#FFB000&lY&#FF9C00&lN",
                    "&#F7C948&lO&#F5B73D&lV&#F3A533&lE&#F19228&lR&#EF801E&lD&#ED6D13&lY&#EB5B09&lN",
                    "&#FF9700&lO&#FFA31A&lV&#FFAF33&lE&#FFBB4D&lR&#FFC766&lD&#FFD380&lY&#FFDF99&lN"
            ),
            1000L,
            "  &7Добро пожаловать!",
            List.of(
                    " &7- &#FFF2E0Скорее &eвыбирай &#FFF2E0режим",
                    " &7- &#FFF2E0для &bигры &#FFF2E0на сервере",
                    " &7- &#FFF2E0и начинай свой &#EA1B40путь"
            ),
            "&#FF9700↶ &#FFF2E0Режимы онлайн &#FF9700↷",
            "&6➜ &fВаш пинг&7: &a{ping}",
            "  &7• &#FFF2E0{world}&7: {status}",
            "&#EA1B40{count}",
            "&#EA1B40Скоро",
            "&#EA1B40Выключен"
    );

    private static final String TEMPLATE = """
            # ====================================================
            # Sidebar (scoreboard) configuration
            # ====================================================
            # Edit this file then run /reload.
            #
            # Phase-1 scope: TEXT and animation frames hot-reload live.
            # Changing animation interval / refresh interval requires a server restart.
            # Reordering / adding new lines is Phase-5 (not yet supported).

            # Master switch. false → no sidebar is shown to anyone. /reload toggles live.
            enabled: true

            title:
              # Frame template — `{frame}` substitutes one entry from `frames` each tick.
              frame-template: "&7« {frame}&f &7»"
              animation-interval-ms: 800
              frames:
                - "&#FFB300&lO&#FFBE1A&lV&#FFC933&lE&#FFD44D&lR&#FFDF66&lD&#FFEA80&lY&#FFF599&lN"
                - "&#FFE066&lO&#FFD94D&lV&#FFD233&lE&#FFCB1A&lR&#FFC400&lD&#FFB000&lY&#FF9C00&lN"
                - "&#F7C948&lO&#F5B73D&lV&#F3A533&lE&#F19228&lR&#EF801E&lD&#ED6D13&lY&#EB5B09&lN"
                - "&#FF9700&lO&#FFA31A&lV&#FFAF33&lE&#FFBB4D&lR&#FFC766&lD&#FFD380&lY&#FFDF99&lN"

            # How often dynamic data (ping, server online counts) is re-pushed to viewers.
            refresh-interval-ms: 1000

            welcome-text: "  &7Добро пожаловать!"

            description-lines:
              - " &7- &#FFF2E0Скорее &eвыбирай &#FFF2E0режим"
              - " &7- &#FFF2E0для &bигры &#FFF2E0на сервере"
              - " &7- &#FFF2E0и начинай свой &#EA1B40путь"

            modes-header: "&#FF9700↶ &#FFF2E0Режимы онлайн &#FF9700↷"

            # `{ping}` → player.latency()
            ping-template: "&6➜ &fВаш пинг&7: &a{ping}"

            # Per-server line. `{world}`, `{status}` are required; `{status}` is replaced by one
            # of status-online / status-soon / status-offline depending on server state.
            server-line-template: "  &7• &#FFF2E0{world}&7: {status}"
            status-online:  "&#EA1B40{count}"
            status-soon:    "&#EA1B40Скоро"
            status-offline: "&#EA1B40Выключен"
            """;

    private final AtomicReference<SidebarConfig> current = new AtomicReference<>(DEFAULTS);

    private SidebarConfigSection() {}

    public SidebarConfig current() {
        return current.get();
    }

    @Override
    public String name() {
        return "sidebar";
    }

    @Override
    public String templateYaml() {
        return TEMPLATE;
    }

    @Override
    public SidebarConfig parse(Map<String, Object> yaml) {
        boolean enabled = asBool(yaml.get("enabled"), DEFAULTS.enabled());
        Map<String, Object> title = asMap(yaml.get("title"));
        long animMs   = asLong(title.get("animation-interval-ms"), DEFAULTS.titleAnimationIntervalMs());
        String tpl    = asString(title.get("frame-template"), DEFAULTS.titleFrameTemplate());
        List<String> frames = asStringList(title.get("frames"), DEFAULTS.titleFrames());

        long refreshMs = asLong(yaml.get("refresh-interval-ms"), DEFAULTS.refreshIntervalMs());
        String welcome = asString(yaml.get("welcome-text"), DEFAULTS.welcomeText());
        List<String> desc = asStringList(yaml.get("description-lines"), DEFAULTS.descriptionLines());
        String modesHeader = asString(yaml.get("modes-header"), DEFAULTS.modesHeader());
        String pingTpl     = asString(yaml.get("ping-template"), DEFAULTS.pingTemplate());
        String srvTpl      = asString(yaml.get("server-line-template"), DEFAULTS.serverLineTemplate());
        String statusOn    = asString(yaml.get("status-online"), DEFAULTS.statusOnline());
        String statusSoon  = asString(yaml.get("status-soon"), DEFAULTS.statusSoon());
        String statusOff   = asString(yaml.get("status-offline"), DEFAULTS.statusOffline());

        return new SidebarConfig(
                enabled, animMs, tpl, frames, refreshMs, welcome, desc,
                modesHeader, pingTpl, srvTpl, statusOn, statusSoon, statusOff
        );
    }

    private static boolean asBool(Object o, boolean fallback) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s.trim());
        return fallback;
    }

    @Override
    public void apply(SidebarConfig snapshot) {
        current.set(snapshot);
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

    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
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
