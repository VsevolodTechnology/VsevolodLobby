package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sidebar (scoreboard) config, backed by {@code config/sidebar.yml}. ConfigLib-powered,
 * hot-reloadable via {@link ConfigReload}.
 *
 * <p>Placeholders: {@code {ping}}, {@code {frame}} (into {@link #titleFrameTemplate}),
 * {@code {world}}/{@code {status}}/{@code {count}} (server lines).</p>
 */
@Configuration
public final class SidebarConfig {

    private static final Path FILE = Paths.get("config", "sidebar.yml");
    private static volatile SidebarConfig instance;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Comment("Главный переключатель. false = сайдбар не отображается.")
    public boolean enabled = true;

    @Comment("Интервал смены кадров анимации заголовка (мс).")
    public long titleAnimationIntervalMs = 800L;

    @Comment("Шаблон заголовка. {frame} заменяется кадром из titleFrames.")
    public String titleFrameTemplate = "<#9C93B0>« {frame}<#FFF2E0> <#9C93B0>»";

    @Comment("Кадры анимации заголовка — меняются по кругу.")
    public List<String> titleFrames = List.of(
            "<gradient:#AE3AF3:#A548DD><bold>ᴏʀᴊᴜꜱ</bold></gradient>",
            "<gradient:#AC3EEE:#A34CD8><bold>ᴏʀᴊᴜꜱ</bold></gradient>",
            "<gradient:#AA41E8:#A14FD2><bold>ᴏʀᴊᴜꜱ</bold></gradient>",
            "<gradient:#A745E3:#9F53CD><bold>ᴏʀᴊᴜꜱ</bold></gradient>"
    );

    @Comment("Как часто обновляются данные сайдбара (мс).")
    public long refreshIntervalMs = 1000L;

    @Comment("Строка приветствия (первая строка тела сайдбара).")
    public String welcomeText = "  <#9C93B0>Добро пожаловать!";

    @Comment("Строки описания под приветствием.")
    public List<String> descriptionLines = List.of(
            " <#9C93B0>- <#FFF2E0>Скорее <#C58AF0>выбирай <#FFF2E0>режим",
            " <#9C93B0>- <#FFF2E0>для <#C58AF0>игры <#FFF2E0>на сервере",
            " <#9C93B0>- <#FFF2E0>и начинай свой <#AE3AF3>путь"
    );

    @Comment("Заголовок секции серверов.")
    public String modesHeader = "<#AE3AF3>↶ <#FFF2E0>Режимы онлайн <#AE3AF3>↷";

    @Comment("Шаблон строки пинга. {ping} — задержка игрока.")
    public String pingTemplate = "<#AE3AF3>➜ <#FFF2E0>Ваш пинг<#9C93B0>: <#C58AF0>{ping}";

    @Comment("Шаблон строки сервера. {world} {status} {count}.")
    public String serverLineTemplate = "  <#9C93B0>• <#FFF2E0>{world}<#9C93B0>: {status}";

    @Comment("Статус \"онлайн\". {count} — игроков на сервере.")
    public String statusOnline = "<#81E366>{count}";

    @Comment("Статус \"скоро откроется\".")
    public String statusSoon = "<#C58AF0>Скоро";

    @Comment("Статус \"выключен\".")
    public String statusOffline = "<#E36666>Выключен";

    public static SidebarConfig get() {
        SidebarConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized SidebarConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, SidebarConfig.class);
        } catch (Exception e) {
            System.err.println("[SidebarConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new SidebarConfig();
        }
        for (Runnable l : listeners) {
            try { l.run(); } catch (Exception ex) { ex.printStackTrace(); }
        }
        return instance;
    }

    /** Register a callback fired after every (re)load — used to reschedule sidebar tasks. */
    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void init() {
        load();
        ConfigReload.register("sidebar", SidebarConfig::load);
    }
}
