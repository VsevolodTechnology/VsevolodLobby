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
            # Таблица игроков (шапка и подвал TAB-листа) — tab.yml
            # ====================================================
            # После изменения файла выполни /reload — применяется без перезапуска сервера.
            #
            # Плейсхолдеры, доступные в строках header и footer:
            #   {ping}    — пинг игрока в миллисекундах
            #   {online}  — общее число игроков онлайн
            #   {time}    — текущее время сервера (формат задаётся в time-format)
            #   {mspt}    — только для операторов (BYPASS): MSPT через mspt-bypass-template;
            #               для остальных игроков — пустая строка
            #
            # Плейсхолдер {player} НЕ поддерживается — рендерер группирует игроков
            # с одинаковым текстом в один пакет, чтобы снизить нагрузку на сервер.

            # Как часто обновляется TAB-лист (в миллисекундах). 100 = 10 раз в секунду.
            update-interval-ms: 100

            # Формат времени для плейсхолдера {time}. Примеры: "HH:mm", "HH:mm:ss".
            time-format: "HH:mm"

            # Шапка TAB-листа (строки над списком игроков).
            # Цветовые коды: &X (стандартные), &#RRGGBB (HEX). Пустая строка "" = пустой отступ.
            header:
              - ""
              - "&#FF9700&lOVERDYN"
              - ""
              - "    &#FFF2E0Пинг: &a{ping}мс &7{mspt}    "
              - ""
              - "&#FF9700↶ &#FFF2E0Список игроков &#FF9700↷"
              - ""

            # Подвал TAB-листа (строки под списком игроков).
            footer:
              - ""
              - ""

            # Шаблон MSPT-строки — показывается только операторам (BYPASS_USERS в коде).
            # {mspt} — миллисекунд на тик (чем меньше, тем лучше; норма ≤ 50).
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
