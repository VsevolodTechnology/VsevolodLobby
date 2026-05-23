package ua.vsevolod.lobby.feature.lobby.ui.hologram.config;

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
 * Static-hologram config, backed by {@code config/holograms.yml}. ConfigLib-powered,
 * hot-reloadable via {@link ConfigReload}; the {@link HologramManager} subscribes via
 * {@link #addListener} so {@code /reload} re-renders live.
 */
@Configuration
public final class HologramsConfig {

    private static final Path FILE = Paths.get("config", "ui", "holograms.yml");
    private static volatile HologramsConfig instance;
    private static final List<Consumer<HologramsConfig>> listeners = new CopyOnWriteArrayList<>();

    @Comment({
            "Голограммы лобби. Ключ — уникальный id голограммы.",
            "billboard: center | vertical | horizontal | fixed.",
            "alignment: left | center | right. background — упакованный ARGB, 0 = нет.",
            "В строках работает плейсхолдер {online} — общий онлайн (обновляется вживую)."
    })
    public Map<String, HologramDefinition> holograms = defaultHolograms();

    private static Map<String, HologramDefinition> defaultHolograms() {
        Map<String, HologramDefinition> m = new LinkedHashMap<>();
        m.put("welcome", new HologramDefinition(
                "welcome",
                0.5, 80.0, -2.5,
                List.of(
                        "<gradient:#AE3AF3:#985DBC><bold>ᴏʀᴊᴜꜱ-ꜱᴛᴜᴅɪᴏ",
                        "",
                        "<#FFF2E0>Рады видеть тебя на нашем сервере",
                        "",
                        "<#FFF2E0>Пробеги <#AE3AF3>вперёд ↑ <#FFF2E0>к центру и нажми на <#AE3AF3>NPC",
                        "",
                        "<#FFF2E0>или используй предметы, чтобы выбрать режим.",
                        "",
                        "<#FFF2E0>Сейчас онлайн <#C58AF0>{online} <bold>человек!",
                        "",
                        "<#FFF2E0>Telegram → <#C58AF0>{telegram-short}",
                        "<#FFF2E0>Сайт → <#C58AF0>{website-short}"
                ),
                "fixed",
                1.2, 1.2, 1.2,
                true, true, "center",
                0x1C1C1E, true, 0.28
        ));
        m.put("mode-selector", new HologramDefinition(
                "mode-selector",
                0.5, 81.8, -29.5,
                List.of(
                        "<gradient:#AE3AF3:#C58AF0><bold>ВЫБОР РЕЖИМА</bold></gradient>",
                        "",
                        "<#FFF2E0>Каждый режим — <#C58AF0>отдельный мир<#FFF2E0>,",
                        "<#FFF2E0>со своей <#C58AF0>атмосферой<#FFF2E0>, правилами и историей.",
                        "",
                        "<#9A8E7A>Зайди в один из режимов и проведи время с пользой —",
                        "<#9A8E7A>каждый из них уникален по-своему.",
                        "",
                        "<#AE3AF3>▸ <#C58AF0>нажми ПКМ по NPC<#FFF2E0>, чтобы открыть меню режимов"
                ),
                "fixed",
                0.95, 0.95, 0.95,
                true, true, "center",
                0x1C1C1E, true, 0.27
        ));
        m.put("parkour", new HologramDefinition(
                "parkour",
                6.5, 81.2, -4.5,
                List.of(
                        "<gradient:#AE3AF3:#C58AF0><bold>ПАРКУР</bold></gradient>",
                        "",
                        "<#FFF2E0>Проверь свою <#C58AF0>реакцию<#FFF2E0>, <#C58AF0>точность <#FFF2E0>и <#C58AF0>контроль",
                        "<#9A8E7A>Сможешь дойти до конца, не сорвавшись?",
                        "",
                        "<#AE3AF3>▸ <#C58AF0>подойди ближе, чтобы начать"
                ),
                "center",
                0.8, 0.8, 0.8,
                true, true, "center",
                0x1C1C1E, true, 0.25
        ));
        return m;
    }

    public static HologramsConfig get() {
        HologramsConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized HologramsConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, HologramsConfig.class);
        } catch (Exception e) {
            System.err.println("[HologramsConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new HologramsConfig();
        }
        for (Consumer<HologramsConfig> l : listeners) {
            try { l.accept(instance); } catch (Exception ex) { ex.printStackTrace(); }
        }
        return instance;
    }

    public static void addListener(Consumer<HologramsConfig> listener) {
        listeners.add(listener);
    }

    public static void init() {
        load();
        ConfigReload.register("holograms", HologramsConfig::load);
    }
}
