package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chest-menu config, backed by {@code config/menus.yml}. ConfigLib-powered, hot-reloadable
 * via {@link ConfigReload}; {@link MenuManager} subscribes via {@link #addListener}.
 */
@Configuration
public final class MenusConfig {

    private static final Path FILE = Paths.get("config", "menus.yml");
    private static volatile MenusConfig instance;
    private static final List<Consumer<MenusConfig>> listeners = new CopyOnWriteArrayList<>();

    @Comment({
            "Меню-сундуки (в стиле DeluxeMenus). Ключ — id меню.",
            "size: 9/18/27/36/45/54. visibility: ALL | BYPASS_ONLY.",
            "Команды предметов: [player] [console] [op] [message] [close] [connect] [menu] [parkour] [broadcast].",
            "server-id: привязка предмета к серверу из servers.yml — иконка, название,",
            "описание и статус берутся оттуда и обновляются вживую, клик подключает к серверу."
    })
    public Map<String, MenuDefinition> menus = buildDefaults();

    private static Map<String, MenuDefinition> buildDefaults() {
        Map<String, MenuItem> items = new LinkedHashMap<>();
        // Purple border + dark inner — on-brand frame.
        items.put("bg_border", new MenuItem(
                List.of(0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44),
                "purple_stained_glass_pane", " ", List.of(), false, List.of(), List.of(), null));
        items.put("bg_inner", new MenuItem(
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 21, 23, 25, 28, 29, 30, 31, 32, 33, 34),
                "black_stained_glass_pane", " ", List.of(), false, List.of(), List.of(), null));
        // Header / info item.
        items.put("info", new MenuItem(
                List.of(4),
                "nether_star",
                "<gradient:#AE3AF3:#C58AF0><bold>Игровые режимы</bold></gradient>",
                List.of(
                        " ",
                        "  <#AE3AF3>«Что это»",
                        "   <#9C93B0>›<#FFF2E0> Каждый режим — отдельный мир",
                        "   <#9C93B0>›<#FFF2E0> со своими правилами и экономикой",
                        " ",
                        "  <#AE3AF3>«Как играть»",
                        "   <#9C93B0>›<#FFF2E0> Выбери режим в меню ниже",
                        "   <#9C93B0>›<#FFF2E0> и кликни, чтобы подключиться",
                        " ",
                        "  <#C58AF0>➥ <#FFF2E0>Выбирай режим и погнали!"
                ),
                true, List.of(), List.of(), null));
        // Server item — icon/name/lore/status come from servers.yml, click connects.
        items.put("adventur", new MenuItem(
                List.of(22),
                "grass_block", null, List.of(), false,
                List.of(), List.of(), "adventur"));
        // "Coming soon" teasers flanking the live server.
        MenuItem soon = new MenuItem(
                List.of(20, 24),
                "gray_dye",
                "<#9C93B0><bold>Новый режим</bold>",
                List.of(
                        " ",
                        "  <#AE3AF3>«Скоро»",
                        "   <#9C93B0>›<#FFF2E0> Здесь появится новый режим —",
                        "   <#9C93B0>›<#FFF2E0> он уже в активной разработке",
                        " ",
                        "  <#C58AF0>➥ <#9C93B0>Следи за новостями в Discord"
                ),
                false, List.of(), List.of(), null);
        items.put("soon", soon);

        Map<String, MenuDefinition> out = new LinkedHashMap<>();
        out.put("mode-selector", new MenuDefinition(
                "mode-selector", "<gradient:#AE3AF3:#985DBC><bold>Выбор режима", 45, null,
                MenuDefinition.Visibility.ALL, items));
        return out;
    }

    public static MenusConfig get() {
        MenusConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized MenusConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, MenusConfig.class, ConfigReload.NULLS);
        } catch (Exception e) {
            System.err.println("[MenusConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new MenusConfig();
        }
        fireListeners();
        return instance;
    }

    /** Persist the given snapshot and make it live. Used by {@code /menu setvisibility}. */
    public static synchronized void save(MenusConfig snapshot) {
        YamlConfigurations.save(FILE, MenusConfig.class, snapshot, ConfigReload.NULLS);
        instance = snapshot;
        fireListeners();
    }

    private static void fireListeners() {
        for (Consumer<MenusConfig> l : listeners) {
            try { l.accept(instance); } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    public static void addListener(Consumer<MenusConfig> listener) {
        listeners.add(listener);
    }

    public static void init() {
        load();
        ConfigReload.register("menus", MenusConfig::load);
    }
}
