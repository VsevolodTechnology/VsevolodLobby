package ua.vsevolod.lobby.feature.lobby.player.join.items;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Join-items config, backed by {@code config/join-items.yml}. ConfigLib-powered,
 * hot-reloadable via {@link ConfigReload}.
 *
 * <p>Each entry is one hotbar item; {@link JoinItemDefinition} carries its slot, material,
 * name, lore, condition and click actions.</p>
 */
@Configuration
public final class JoinItemsConfig {

    private static final Path FILE = Paths.get("config", "join-items.yml");
    private static volatile JoinItemsConfig instance;

    @Comment({
            "Предметы, выдаваемые игроку при входе на лобби.",
            "condition: ALWAYS / BYPASS_ONLY / NON_BYPASS — кому выдавать.",
            "actions.type: none / run-command / open-menu / parkour-start."
    })
    public List<JoinItemDefinition> items = List.of(
            new JoinItemDefinition(
                    "mode-selector",
                    4,
                    "compass",
                    "<#AE3AF3><bold>Выбор режима",
                    List.of(
                            " ",
                            "<#65D1FC> «Информация»",
                            "<gray> - <#FFF2E0>Меню выбора игрового режима,",
                            "<gray> - <#FFF2E0>все режимы сервера в одном месте.",
                            " ",
                            "<#C58AF0>➥ ПКМ — открыть меню"
                    ),
                    false,
                    JoinItemDefinition.Condition.ALWAYS,
                    new NpcAction("open-menu", "mode-selector", false),
                    NpcAction.NONE
            )
    );

    @Comment({
            "Предметы-переключатели хотбара (музыка, скорборд, видимость игроков).",
            "У каждого: материал вкл/выкл, название, метка состояния, описание (lore),",
            "статусы и сообщения в чат. {status} в lore — подставляется statusEnabled/Disabled."
    })
    public ToggleItems toggleItems = ToggleItems.defaults();

    public static JoinItemsConfig get() {
        JoinItemsConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized JoinItemsConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, JoinItemsConfig.class);
        } catch (Exception e) {
            System.err.println("[JoinItemsConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new JoinItemsConfig();
        }
        return instance;
    }

    public static void init() {
        load();
        ConfigReload.register("join-items", JoinItemsConfig::load);
    }
}
