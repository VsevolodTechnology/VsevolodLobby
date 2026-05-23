package ua.vsevolod.lobby.feature.lobby.player.join.cutscene;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Cinematic-flyover config, backed by {@code config/cutscene.yml}.
 *
 * <p>ConfigLib-powered ({@code @Configuration}), hot-reloadable via {@link ConfigReload}.
 * The camera flies through {@link #waypoints} in order, holding on each then gliding to the
 * next; after the last one control returns to the player.</p>
 */
@Configuration
public final class CutsceneConfig {

    private static final Path FILE = Paths.get("config", "cutscene.yml");
    private static volatile CutsceneConfig instance;

    @Comment("Главный выключатель кат-сцены.")
    public boolean enabled = true;

    @Comment("true — кат-сцена только при ПЕРВОМ заходе игрока; false — при каждом заходе.")
    public boolean firstJoinOnly = true;

    @Comment("Если true, зажатие SHIFT досрочно завершает кат-сцену.")
    public boolean skippable = true;

    @Comment("Тиков на плавный перелёт между соседними точками (20 = 1 сек).")
    public int interpolationTicks = 30;

    @Comment({
            "Звук/музыка ВО ВРЕМЯ кат-сцены (vanilla sound key). Пусто = тишина."
    })
    public String cinematicSound = "minecraft:music_disc.creator_music_box";

    @Comment({
            "Музыка СРАЗУ ПОСЛЕ кат-сцены (специальный трек). По умолчанию — Otherside.",
            "После него плавно подключается обычная ротация лобби. Пусто = сразу ротация."
    })
    public String postCutsceneSound = "minecraft:music_disc.otherside";

    @Comment({
            "Точки камеры. Камера побывает на каждой по очереди.",
            "Координаты в блоках (центр = +0.5). yaw/pitch — ванильные. hold — тиков стоять."
    })
    public List<Waypoint> waypoints = List.of(
            new Waypoint( 0.5, 100.0,  20.0, 180f,  55f, 40),
            new Waypoint( 4.0,  86.0, -10.0, 200f,  25f, 35),
            new Waypoint( 5.0,  80.0, -24.0, 200f,  10f, 50),
            new Waypoint(15.0,  86.0, -18.0, 250f,  15f, 35),
            new Waypoint(11.0,  83.0,  -1.0, 295f,  20f, 45),
            new Waypoint( 0.5,  92.0,   0.5, 180f,  70f, 30)
    );

    /** One camera stop. ConfigLib serialises the record components as a YAML map. */
    public record Waypoint(double x, double y, double z, float yaw, float pitch, int holdTicks) {}

    public static CutsceneConfig get() {
        CutsceneConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized CutsceneConfig load() {
        CutsceneConfig c;
        try {
            c = YamlConfigurations.update(FILE, CutsceneConfig.class);
        } catch (Exception e) {
            System.err.println("[CutsceneConfig] Failed to load " + FILE + ": " + e.getMessage() + " — using defaults");
            c = new CutsceneConfig();
        }
        instance = c;
        return c;
    }

    /** Persist the given snapshot to disk and make it live. Used by {@code /cutscene save}. */
    public static synchronized void save(CutsceneConfig snapshot) {
        YamlConfigurations.save(FILE, CutsceneConfig.class, snapshot);
        instance = snapshot;
    }

    /** Load now and wire {@code /reload} support. Call once from bootstrap. */
    public static void init() {
        load();
        ConfigReload.register("cutscene", CutsceneConfig::load);
    }
}
