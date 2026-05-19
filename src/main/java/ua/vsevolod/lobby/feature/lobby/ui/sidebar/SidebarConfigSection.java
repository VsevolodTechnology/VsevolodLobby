package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import ua.vsevolod.lobby.feature.admin.config.ConfigSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
            # Сайдбар (таблица очков справа на экране) — sidebar.yml
            # ====================================================
            # После изменения файла выполни /reload.
            #
            # Текст и кадры анимации применяются мгновенно через /reload.
            # Изменение animation-interval-ms и refresh-interval-ms требует перезапуска сервера.

            # Главный переключатель. false = сайдбар не отображается ни одному игроку.
            enabled: true

            title:
              # Шаблон заголовка. {frame} заменяется одним из кадров анимации из frames.
              # Цветовые коды: &X (стандартные), &#RRGGBB (HEX). &l — жирный, &o — курсив.
              frame-template: "&7« {frame}&f &7»"

              # Интервал смены кадров анимации в миллисекундах (800 = 0.8 секунды).
              animation-interval-ms: 800

              # Кадры анимации заголовка. Перечисляй любые строки — они меняются по кругу.
              frames:
                - "&#FFB300&lO&#FFBE1A&lV&#FFC933&lE&#FFD44D&lR&#FFDF66&lD&#FFEA80&lY&#FFF599&lN"
                - "&#FFE066&lO&#FFD94D&lV&#FFD233&lE&#FFCB1A&lR&#FFC400&lD&#FFB000&lY&#FF9C00&lN"
                - "&#F7C948&lO&#F5B73D&lV&#F3A533&lE&#F19228&lR&#EF801E&lD&#ED6D13&lY&#EB5B09&lN"
                - "&#FF9700&lO&#FFA31A&lV&#FFAF33&lE&#FFBB4D&lR&#FFC766&lD&#FFD380&lY&#FFDF99&lN"

            # Как часто обновляются данные сайдбара (пинг, онлайн) в миллисекундах.
            # 1000 = раз в секунду. Меньше значение — плавнее, но больше нагрузка.
            refresh-interval-ms: 1000

            # Строка приветствия (первая строка тела сайдбара).
            welcome-text: "  &7Добро пожаловать!"

            # Строки описания под приветствием. Можно добавлять/удалять строки.
            description-lines:
              - " &7- &#FFF2E0Скорее &eвыбирай &#FFF2E0режим"
              - " &7- &#FFF2E0для &bигры &#FFF2E0на сервере"
              - " &7- &#FFF2E0и начинай свой &#EA1B40путь"

            # Заголовок секции серверов (перед списком режимов).
            modes-header: "&#FF9700↶ &#FFF2E0Режимы онлайн &#FF9700↷"

            # Шаблон строки с пингом. {ping} — задержка текущего игрока в миллисекундах.
            ping-template: "&6➜ &fВаш пинг&7: &a{ping}"

            # Шаблон строки для каждого игрового сервера.
            # {world}  — название сервера (из кода, не из этого файла).
            # {status} — подставляется один из трёх шаблонов ниже в зависимости от статуса.
            server-line-template: "  &7• &#FFF2E0{world}&7: {status}"

            # Текст при статусе "онлайн". {count} — количество игроков на сервере.
            status-online:  "&#EA1B40{count}"

            # Текст при статусе "скоро откроется".
            status-soon:    "&#EA1B40Скоро"

            # Текст при статусе "выключен".
            status-offline: "&#EA1B40Выключен"
            """;

    private final AtomicReference<SidebarConfig> current = new AtomicReference<>(DEFAULTS);
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private SidebarConfigSection() {}

    public SidebarConfig current() {
        return current.get();
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
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
