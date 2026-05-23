package ua.vsevolod.lobby.config;

import de.exlll.configlib.YamlConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hot-reload registry for ConfigLib-backed configs.
 *
 * <p>ConfigLib classes ({@code @Configuration} + {@code YamlConfigurations.update}) load once
 * at boot and have no built-in {@code /reload} hook. Each such config registers its static
 * {@code load()} here so {@link #reloadAll()} — called by {@code /reload} — re-reads every
 * file from disk and swaps the live snapshot.</p>
 *
 * <p>This is the ConfigLib counterpart of the legacy {@code ConfigManager}; as sections are
 * migrated off {@code ConfigSection} they move their reload wiring here.</p>
 */
public final class ConfigReload {

    /** Directory holding every {@code *.yml} config file. */
    public static final Path CONFIG_DIR = Paths.get("config");

    private static final Map<String, Runnable> RELOADERS = new LinkedHashMap<>();

    /**
     * Shared ConfigLib properties: keep null fields on both read and write. Configs with
     * nullable fields (optional command, NPC name/skin, …) must load with this or ConfigLib
     * rejects the null.
     */
    public static final Consumer<YamlConfigurationProperties.Builder<?>> NULLS =
            b -> b.outputNulls(true).inputNulls(true);

    private ConfigReload() {}

    /** Register a config's reloader under a display name. Called once per config at startup. */
    public static synchronized void register(String name, Runnable reloader) {
        RELOADERS.put(name, reloader);
    }

    /** Re-run every reloader. Returns the names that threw — empty means all succeeded. */
    public static synchronized Result reloadAll() {
        List<String> failed = new ArrayList<>();
        int ok = 0;
        for (Map.Entry<String, Runnable> e : RELOADERS.entrySet()) {
            try {
                e.getValue().run();
                ok++;
            } catch (Exception ex) {
                failed.add(e.getKey());
                System.err.println("[ConfigReload] '" + e.getKey() + "' failed: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        return new Result(ok, failed);
    }

    public record Result(int loaded, List<String> failed) {
        public boolean allOk() { return failed.isEmpty(); }
    }
}
