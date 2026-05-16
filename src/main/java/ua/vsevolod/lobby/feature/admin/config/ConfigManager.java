package ua.vsevolod.lobby.feature.admin.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConfigManager {

    public static final Path CONFIG_DIR = Paths.get("config");

    private final Map<String, ConfigSection<?>> sections = new LinkedHashMap<>();

    public synchronized void register(ConfigSection<?> section) {
        if (section == null || section.name() == null || section.name().isBlank()) {
            throw new IllegalArgumentException("Section must have a non-blank name");
        }
        if (sections.containsKey(section.name())) {
            throw new IllegalStateException("Section already registered: " + section.name());
        }
        sections.put(section.name(), section);
    }

    public synchronized void loadAll() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            System.err.println("[ConfigManager] Failed to create " + CONFIG_DIR + ": " + e.getMessage());
            return;
        }
        for (ConfigSection<?> section : sections.values()) {
            loadOne(section, true);
        }
    }

    public synchronized ReloadResult reloadAll() {
        AtomicInteger ok = new AtomicInteger();
        List<String> failed = new ArrayList<>();
        for (ConfigSection<?> section : sections.values()) {
            if (loadOne(section, false)) {
                ok.incrementAndGet();
            } else {
                failed.add(section.name());
            }
        }
        return new ReloadResult(ok.get(), failed);
    }

    private <T> boolean loadOne(ConfigSection<T> section, boolean firstLoad) {
        Path file = CONFIG_DIR.resolve(section.name() + ".yml");
        if (firstLoad && !Files.exists(file)) {
            writeTemplate(file, section);
        }
        if (!Files.exists(file)) {
            System.err.println("[ConfigManager] Missing config: " + file);
            return false;
        }
        try (var in = Files.newInputStream(file)) {
            Object raw = new Yaml().load(in);
            Map<String, Object> map = (raw instanceof Map<?, ?> m) ? coerceMap(m) : Map.of();
            T snapshot = section.parse(map);
            section.apply(snapshot);
            return true;
        } catch (Exception e) {
            System.err.println("[ConfigManager] Failed to load " + file + ": " + e);
            e.printStackTrace();
            return false;
        }
    }

    private static <V> void writeTemplate(Path file, ConfigSection<V> section) {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, section.templateYaml(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("[ConfigManager] Created default config: " + file.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[ConfigManager] Failed to write template " + file + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    public record ReloadResult(int loaded, List<String> failed) {
        public boolean allOk() {
            return failed.isEmpty();
        }
    }
}
