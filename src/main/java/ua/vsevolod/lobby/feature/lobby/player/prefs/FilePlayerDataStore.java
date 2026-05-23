package ua.vsevolod.lobby.feature.lobby.player.prefs;

import net.minestom.server.coordinate.Pos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;

/**
 * File-based {@link PlayerDataStore}. One {@code .properties} file per player
 * under {@code storage/player_data/}.
 *
 * <p>Works without any external dependency (no MongoDB required).
 * Saves are atomic via a temp-file → rename pattern.</p>
 */
public final class FilePlayerDataStore implements PlayerDataStore {

    private static final Path DIR = Paths.get("storage", "player_data");

    public FilePlayerDataStore() {
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            System.err.println("[PlayerPrefs/File] Failed to create directory " + DIR + ": " + e.getMessage());
        }
    }

    @Override
    public PlayerPreferences load(UUID uuid) {
        Path file = DIR.resolve(uuid + ".properties");
        if (!Files.exists(file)) return PlayerPreferences.defaults();

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("[PlayerPrefs/File] Failed to read " + file + ": " + e.getMessage());
            return PlayerPreferences.defaults();
        }

        boolean music = bool(props, "musicEnabled", true);
        boolean hidden = bool(props, "playersHidden", false);
        boolean sidebarHidden = bool(props, "sidebarHidden", false);
        boolean positionSaveEnabled = bool(props, "positionSaveEnabled", true);
        boolean protocolWarningEnabled = bool(props, "protocolWarningEnabled", true);
        long firstSeenEpoch = parseLong(props.getProperty("firstSeenEpoch"), 0L);

        Pos pos = null;
        if (bool(props, "hasPosition", false)) {
            try {
                pos = new Pos(
                        Double.parseDouble(props.getProperty("lastX", "0")),
                        Double.parseDouble(props.getProperty("lastY", "0")),
                        Double.parseDouble(props.getProperty("lastZ", "0")),
                        Float.parseFloat(props.getProperty("lastYaw", "0")),
                        Float.parseFloat(props.getProperty("lastPitch", "0"))
                );
            } catch (NumberFormatException ignored) {}
        }

        boolean timeByIpEnabled = bool(props, "timeByIpEnabled", false);
        String parkourDifficulty = nullable(props, "parkourDifficulty");
        String parkourTheme      = nullable(props, "parkourTheme");
        String parkourDimension  = nullable(props, "parkourDimension");
        boolean parkourTraining  = bool(props, "parkourTraining", false);
        String parkourSound      = nullable(props, "parkourSound");

        return new PlayerPreferences(music, hidden, sidebarHidden, positionSaveEnabled, protocolWarningEnabled, firstSeenEpoch, pos,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    private static String nullable(Properties props, String key) {
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static long parseLong(String s, long fallback) {
        if (s == null) return fallback;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    @Override
    public void save(UUID uuid, PlayerPreferences prefs) {
        Properties props = new Properties();
        props.setProperty("musicEnabled", String.valueOf(prefs.musicEnabled()));
        props.setProperty("playersHidden", String.valueOf(prefs.playersHidden()));
        props.setProperty("sidebarHidden", String.valueOf(prefs.sidebarHidden()));
        props.setProperty("positionSaveEnabled", String.valueOf(prefs.positionSaveEnabled()));
        props.setProperty("protocolWarningEnabled", String.valueOf(prefs.protocolWarningEnabled()));
        props.setProperty("firstSeenEpoch", String.valueOf(prefs.firstSeenEpoch()));
        props.setProperty("timeByIpEnabled", String.valueOf(prefs.timeByIpEnabled()));
        props.setProperty("parkourTraining", String.valueOf(prefs.parkourTraining()));
        if (prefs.parkourDifficulty() != null) props.setProperty("parkourDifficulty", prefs.parkourDifficulty());
        if (prefs.parkourTheme() != null)      props.setProperty("parkourTheme",      prefs.parkourTheme());
        if (prefs.parkourDimension() != null)  props.setProperty("parkourDimension",  prefs.parkourDimension());
        if (prefs.parkourSound() != null)      props.setProperty("parkourSound",      prefs.parkourSound());

        Pos pos = prefs.lastPosition();
        if (pos != null) {
            props.setProperty("hasPosition", "true");
            props.setProperty("lastX", String.valueOf(pos.x()));
            props.setProperty("lastY", String.valueOf(pos.y()));
            props.setProperty("lastZ", String.valueOf(pos.z()));
            props.setProperty("lastYaw", String.valueOf(pos.yaw()));
            props.setProperty("lastPitch", String.valueOf(pos.pitch()));
        } else {
            props.setProperty("hasPosition", "false");
        }

        Path file = DIR.resolve(uuid + ".properties");
        Path tmp = file.resolveSibling(uuid + ".properties.tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(out, null);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[PlayerPrefs/File] Failed to write " + file + ": " + e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() {
        // No resources to release.
    }

    private static boolean bool(Properties props, String key, boolean fallback) {
        String v = props.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }
}
