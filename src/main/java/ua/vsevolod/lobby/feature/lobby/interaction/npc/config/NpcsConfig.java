package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NPC config, backed by {@code config/npcs.yml}. ConfigLib-powered, hot-reloadable via
 * {@link ConfigReload}; {@code NpcManager} subscribes via {@link #addListener}.
 */
@Configuration
public final class NpcsConfig {

    private static final Path FILE = Paths.get("config", "npcs.yml");
    private static volatile NpcsConfig instance;
    private static final List<Consumer<NpcsConfig>> listeners = new CopyOnWriteArrayList<>();

    @Comment({
            "Список NPC лобби. skin — спецификация скина: ник игрока, 'url:<...>',",
            "или 'value:<base64>;sig:<base64>'. null = без скина.",
            "entityType — ключ моба (minecraft:allay и т.д.): тогда NPC = декоративный моб,",
            "а scale увеличивает его размер. null entityType = обычный NPC-манекен.",
            "Команды кликов: [menu] [player] [console] [op] [message] [connect] [parkour] [broadcast]."
    })
    public List<NpcDefinition> npcs = List.of(
            new NpcDefinition(
                    "parkour",
                    new NpcPosition(6.5, 79.0, -4.5, 52f, 0f),
                    null,
                    "Проверь свою реакцию, точность и контроль\nможешь дойти до конца и не упасть?",
                    "Dream", true, "gold", true,
                    List.of("[parkour]"),
                    List.of(),
                    null, 1.0
            ),
            new NpcDefinition(
                    "mode-selector",
                    new NpcPosition(0.5, 77.0, -29.5, 0f, 0f),
                    null, null, null,
                    true, null, true,
                    List.of("[menu] mode-selector"), List.of("[menu] mode-selector"),
                    "minecraft:warden", 1.15
            )
    );

    public static NpcsConfig get() {
        NpcsConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized NpcsConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, NpcsConfig.class, ConfigReload.NULLS);
        } catch (Exception e) {
            System.err.println("[NpcsConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new NpcsConfig();
        }
        fireListeners();
        return instance;
    }

    /** Persist the given snapshot and make it live. Used by the {@code /npc} command. */
    public static synchronized void save(NpcsConfig snapshot) {
        YamlConfigurations.save(FILE, NpcsConfig.class, snapshot, ConfigReload.NULLS);
        instance = snapshot;
        fireListeners();
    }

    private static void fireListeners() {
        for (Consumer<NpcsConfig> l : listeners) {
            try { l.accept(instance); } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    public static void addListener(Consumer<NpcsConfig> listener) {
        listeners.add(listener);
    }

    public static void init() {
        load();
        ConfigReload.register("npcs", NpcsConfig::load);
    }
}
