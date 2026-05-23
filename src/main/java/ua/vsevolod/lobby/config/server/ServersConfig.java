package ua.vsevolod.lobby.config.server;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Server registry, backed by {@code config/servers.yml}. ConfigLib-powered, hot-reloadable
 * via {@link ConfigReload}.
 *
 * <p>This is the single source of truth for "what servers exist": each entry in {@link #servers}
 * can be bound to a menu item (see {@code MenuItem.serverId}) or targeted by {@code [connect]}.
 * All status/connect text and the menu-item lore template live here too — every string is
 * MiniMessage and fully configurable.</p>
 */
@Configuration
public final class ServersConfig {

    private static final Path FILE = Paths.get("config", "network", "servers.yml");
    private static volatile ServersConfig instance;
    private static final List<Consumer<ServersConfig>> listeners = new CopyOnWriteArrayList<>();

    // Derived once per (re)load — converting on every UI tick would be wasteful.
    private transient List<ServerInfo> serverInfoCache = List.of();

    @Comment({
            "Серверы лобби. Ключ карты — id сервера: его указывают в [connect] <id>",
            "и в menus.yml (server-id у предмета), чтобы привязать предмет к серверу.",
            "status: ONLINE — статус берётся из живого опроса прокси;",
            "        OFFLINE / SOON — статус принудительно зафиксирован.",
            "itemName / itemLore у сервера — необязательны: оставь пустыми, чтобы взять",
            "общий шаблон ниже; заполни, если этому серверу нужны своё название,",
            "описание и цвета (отличные от остальных)."
    })
    public Map<String, ServerEntry> servers = defaultServers();

    @Comment("Префикс сообщений о подключении (MiniMessage).")
    public String connectPrefix =
            "<dark_gray>[<gradient:#AE3AF3:#985DBC><bold>Сервер</bold></gradient><dark_gray>] ";

    @Comment("Сообщение при подключении. Плейсхолдеры: {server} {world}.")
    public String connectOnline = "<#FFF2E0>Идёт подключение к серверу <#AE3AF3>{world}<#FFF2E0>…";

    @Comment("Сообщение, если сервер выключен.")
    public String connectOffline =
            "<#FFF2E0>Сервер <#AE3AF3>{world}<#FFF2E0> сейчас выключен — загляни позже.";

    @Comment("Сообщение, если сервер ещё запускается.")
    public String connectLoading =
            "<#FFF2E0>Сервер <#AE3AF3>{world}<#FFF2E0> ещё запускается — попробуй чуть позже.";

    @Comment("Сообщение, если сервер скоро откроется.")
    public String connectSoon = "<#FFF2E0>Сервер <#AE3AF3>{world}<#FFF2E0> скоро откроется.";

    @Comment("Короткая подпись статуса — плейсхолдер {status} в шаблонах.")
    public String statusOnline = "<#A8E063>● Активен";
    public String statusOffline = "<#E36666>● Выключен";
    public String statusLoading = "<#C58AF0>● Запускается";
    public String statusSoon = "<#C58AF0>● Скоро";

    @Comment({
            "ОБЩИЙ шаблон названия предмета сервера в меню. Плейсхолдеры: {world} {server}.",
            "Можно переопределить для конкретного сервера полем itemName выше."
    })
    public String itemName = "<gradient:#AE3AF3:#985DBC><bold>{world}</bold></gradient>";

    @Comment({
            "ОБЩИЙ шаблон описания (lore) предмета сервера в меню. Плейсхолдеры:",
            "{world} {server} {version} {status} {online} {max} {bar} {tags}",
            "{website} {telegram} {discord}.",
            "Можно переопределить для конкретного сервера полем itemLore выше."
    })
    public List<String> itemLore = new ArrayList<>(List.of(
            " ",
            "  <#AE3AF3>«Сервер»",
            "   <dark_gray>› <#FFF2E0>Мир: <#C58AF0>{world}",
            "   <dark_gray>› <#FFF2E0>Состояние: {status}",
            "   <dark_gray>› <#FFF2E0>Онлайн: <#C58AF0>{online}<dark_gray>/<#C58AF0>{max} {bar}",
            " ",
            "  <#AE3AF3>«Информация»",
            "   <dark_gray>› <#FFF2E0>Ядро: <#C58AF0>{version}",
            " ",
            "  <#AE3AF3>«Теги»",
            "   {tags}",
            " ",
            "  <#AE3AF3>«Соц.сети»",
            "   <dark_gray>› <#FFF2E0>Сайт: <#C58AF0>{website}",
            "   <dark_gray>› <#FFF2E0>TG: <#C58AF0>{telegram}",
            "   <dark_gray>› <#FFF2E0>DS: <#C58AF0>{discord}",
            " ",
            "  <#C58AF0>➥ <#FFF2E0>Нажми, чтобы подключиться"
    ));

    @Comment("Полоса онлайна — {bar}. segments — число делений.")
    public int barSegments = 5;
    public String barColorLow = "#6E4E8C";
    public String barColorMedium = "#A06BD0";
    public String barColorHigh = "#C58AF0";
    public String barColorEmpty = "#4A4A4A";
    public String barColorBorder = "#3C3C3C";

    @Comment("Цвет тегов и разделителя между ними.")
    public String tagColor = "#C58AF0";
    public String tagSeparatorColor = "#5A5A5A";
    public int tagsMaxPerLine = 3;

    private static Map<String, ServerEntry> defaultServers() {
        Map<String, ServerEntry> out = new LinkedHashMap<>();
        out.put("adventur", new ServerEntry(
                "Выживание", "1.21.8", ServerStatus.ONLINE, 100,
                List.of("Выживание", "Экономика", "Кланы", "Магия", "Ивенты"),
                "grass_block", null, null));
        return out;
    }

    // ── Derived view ──────────────────────────────────────────────────────────

    /** Live {@link ServerInfo} views, derived from {@link #servers} (cached per reload). */
    public List<ServerInfo> serverInfos() {
        return serverInfoCache;
    }

    public Optional<ServerInfo> findById(String id) {
        if (id == null) return Optional.empty();
        return serverInfoCache.stream()
                .filter(s -> s.id().equalsIgnoreCase(id))
                .findFirst();
    }

    private void rebuildCache() {
        List<ServerInfo> infos = new ArrayList<>(servers.size());
        for (Map.Entry<String, ServerEntry> e : servers.entrySet()) {
            infos.add(ServerInfo.from(e.getKey(), e.getValue()));
        }
        serverInfoCache = List.copyOf(infos);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static ServersConfig get() {
        ServersConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized ServersConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, ServersConfig.class, ConfigReload.NULLS);
        } catch (Exception e) {
            System.err.println("[ServersConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new ServersConfig();
        }
        instance.rebuildCache();
        for (Consumer<ServersConfig> l : listeners) {
            try { l.accept(instance); } catch (Exception ex) { ex.printStackTrace(); }
        }
        return instance;
    }

    public static void addListener(Consumer<ServersConfig> listener) {
        listeners.add(listener);
    }

    public static void init() {
        load();
        ConfigReload.register("servers", ServersConfig::load);
    }
}
