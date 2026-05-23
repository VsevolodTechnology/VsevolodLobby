package ua.vsevolod.lobby.bootstrap;

import ua.vsevolod.lobby.bootstrap.server.ServerBootstrap;
import ua.vsevolod.lobby.config.LoggingConfig;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class Main {

    static void main(String[] args) {
        // Must be first: filter known JVM-level warning noise printed to stderr/stdout
        // during library initialization (Spark/AsyncProfiler, protobuf Unsafe, etc.)
        suppressJvmNoise();

        applyDefaultIfAbsent("minestom.chunk-view-distance", "6");
        applyDefaultIfAbsent("minestom.entity-view-distance", "4");
        applyDefaultIfAbsent("minestom.entity-synchronization-ticks", "20");

        // Configure SLF4J SimpleLogger BEFORE Minestom.init() triggers SLF4J initialization.
        // System properties override simplelogger.properties and are picked up on first SLF4J use.
        configureSlfj(LoggingConfig.load());

        ServerBootstrap.bootstrap();
    }

    // ── JVM noise suppression ─────────────────────────────────────────────────

    private static void suppressJvmNoise() {
        System.setErr(filtered(System.err));
        System.setOut(filtered(System.out));
    }

    private static PrintStream filtered(PrintStream delegate) {
        return new PrintStream(new OutputStream() {

            // Line-buffer so we can inspect complete lines before forwarding.
            private final StringBuilder line = new StringBuilder(256);

            @Override
            public void write(int b) {
                char c = (char) (b & 0xFF);
                if (c == '\n') {
                    flush();
                } else {
                    line.append(c);
                }
            }

            @Override
            public void write(byte[] buf, int off, int len) {
                String chunk = new String(buf, off, len, StandardCharsets.UTF_8);
                for (int i = 0; i < chunk.length(); i++) {
                    char c = chunk.charAt(i);
                    if (c == '\n') {
                        flush();
                    } else if (c != '\r') {
                        line.append(c);
                    }
                }
            }

            @Override
            public void flush() {
                String text = line.toString();
                line.setLength(0);
                if (!text.isEmpty() && !isNoise(text)) {
                    delegate.println(text);
                    delegate.flush();
                }
            }
        }, true, StandardCharsets.UTF_8);
    }

    /**
     * Returns true for well-known JVM / library noise lines that add no value to the operator:
     * AsyncProfiler native-access warnings, protobuf Unsafe deprecation warnings.
     */
    private static boolean isNoise(String line) {
        return line.startsWith("WARNING: A restricted method in java.lang.System")
                || line.startsWith("WARNING: java.lang.System::load has been called")
                || line.startsWith("WARNING: Use --enable-native-access")
                || line.startsWith("WARNING: Restricted methods will be blocked")
                || line.startsWith("WARNING: A terminally deprecated method in sun.misc.Unsafe")
                || line.startsWith("WARNING: sun.misc.Unsafe::")
                || line.startsWith("WARNING: Please consider reporting this to the maintainers")
                || line.contains("will be removed in a future release")
                // Incubator module notice — safe to suppress, Vector API is core since Java 22
                || line.startsWith("WARNING: Using incubator modules");
    }

    private static void configureSlfj(LoggingConfig cfg) {
        String zoneId = cfg.timezone.getId();
        // Show timestamps in the same format as ServerLogger: [yyyy-MM-dd HH:mm:ss ZONE_ID] [LEVEL]
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat",
                "yyyy-MM-dd HH:mm:ss '" + zoneId + "'");
        // Mirror the JVM default timezone so SLF4J renders times in the configured zone
        System.setProperty("user.timezone", zoneId);
    }

    private static void applyDefaultIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
}
