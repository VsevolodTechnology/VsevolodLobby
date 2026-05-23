package ua.vsevolod.lobby.feature.lobby.ui.tab;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TAB-list header/footer config, backed by {@code config/tab.yml}. ConfigLib-powered,
 * hot-reloadable via {@link ConfigReload}.
 *
 * <p>Placeholders in header/footer: {@code {ping}}, {@code {online}}, {@code {time}},
 * {@code {state}}. {@code {player}} is not supported (the renderer groups equal lines into
 * one packet).</p>
 */
@Configuration
public final class TabConfig {

    private static final Path FILE = Paths.get("config", "ui", "tab.yml");
    private static volatile TabConfig instance;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Comment("Как часто обновляется TAB-лист (мс). 500 = 2 раза в секунду.")
    public long updateIntervalMs = 500L;

    @Comment("Формат времени для {time}. Примеры: \"HH:mm\", \"HH:mm:ss\".")
    public String timeFormat = "HH:mm";

    @Comment({
            "Часовой пояс по умолчанию для {time}. Примеры: Europe/Moscow, Europe/Kyiv, Asia/Almaty.",
            "Игрок может в настройках включить показ времени по своему IP — тогда этот пояс не его."
    })
    public String timeZone = "Europe/Moscow";

    @Comment("Короткая подпись пояса по умолчанию — выводится в скобках, например 14:30 (МСК).")
    public String timeZoneLabel = "МСК";

    @Comment("Шапка TAB-листа (MiniMessage). Пустая строка = отступ.")
    public List<String> header = List.of(
            "",
            "<gradient:#AE3AF3:#985DBC><bold>ᴏʀᴊᴜꜱ-ꜱᴛᴜᴅɪᴏ</bold></gradient>",
            "<#A698C5>лучший сервер для своей игры",
            ""
    );

    @Comment("Подвал TAB-листа. Плейсхолдеры: {ping} {state} {online} {time}.")
    public List<String> footer = List.of(
            "",
            "<#A698C5>онлайн <#FFF2E0>{online}  <dark_gray>•  <#A698C5>пинг <#FFF2E0>{ping}мс  <dark_gray>•  <#A698C5>время <#FFF2E0>{time}",
            "",
            "<#A698C5>▸ <gradient:#AE3AF3:#985DBC>{website-short}</gradient><#A698C5> ◂",
            ""
    );

    @Comment({
            "Состояние сервера для {state} — определяется по нагрузке (mspt, мс на тик).",
            "mspt ≤ excellent → отличное; ≤ good → хорошее; ≤ normal → стабильное; иначе → нагружено."
    })
    public String stateExcellent = "<#81E366>отличное";
    public String stateGood = "<#A8E063>хорошее";
    public String stateNormal = "<#C58AF0>стабильное";
    public String statePoor = "<#E36666>высокая нагрузка";
    public double stateExcellentMaxMspt = 30.0;
    public double stateGoodMaxMspt = 42.0;
    public double stateNormalMaxMspt = 50.0;

    /** Picks the {@code {state}} label for a measured mspt value. */
    public String stateFor(double mspt) {
        if (mspt <= stateExcellentMaxMspt) return stateExcellent;
        if (mspt <= stateGoodMaxMspt) return stateGood;
        if (mspt <= stateNormalMaxMspt) return stateNormal;
        return statePoor;
    }

    public static TabConfig get() {
        TabConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized TabConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, TabConfig.class);
        } catch (Exception e) {
            System.err.println("[TabConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new TabConfig();
        }
        for (Runnable l : listeners) {
            try { l.run(); } catch (Exception ex) { ex.printStackTrace(); }
        }
        return instance;
    }

    /** Register a callback fired after every (re)load — used to reschedule the refresh task. */
    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void init() {
        load();
        ConfigReload.register("tab", TabConfig::load);
    }
}
