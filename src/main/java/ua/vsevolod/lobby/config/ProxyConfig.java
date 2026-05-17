package ua.vsevolod.lobby.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Reads runtime proxy/forwarding settings from {@code proxy.properties} in the working directory.
 * If the file is absent or the secret is empty, the server runs without Velocity forwarding (offline mode).
 *
 * <p>Properties:</p>
 * <ul>
 *   <li>{@code velocity.forwarding-secret} — shared secret from Velocity's {@code forwarding.secret}.
 *       When set, Minestom only accepts connections forwarded by that Velocity instance.</li>
 *   <li>{@code host.address} — bind address (default: {@code 0.0.0.0})</li>
 *   <li>{@code host.port} — bind port (default: {@code 25565})</li>
 * </ul>
 */
public final class ProxyConfig {

    private static final Path FILE = Paths.get("proxy.properties");

    private final String velocitySecret;
    private final String hostAddress;
    private final int hostPort;

    private ProxyConfig(String velocitySecret, String hostAddress, int hostPort) {
        this.velocitySecret = velocitySecret;
        this.hostAddress = hostAddress;
        this.hostPort = hostPort;
    }

    public boolean velocityEnabled() {
        return velocitySecret != null && !velocitySecret.isBlank();
    }

    public String velocitySecret() {
        return velocitySecret;
    }

    public String hostAddress() {
        return hostAddress;
    }

    public int hostPort() {
        return hostPort;
    }

    public static ProxyConfig load() {
        if (!Files.exists(FILE)) {
            writeTemplate();
        }

        Properties props = new Properties();
        if (Files.exists(FILE)) {
            try (var in = Files.newInputStream(FILE)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("[ProxyConfig] Failed to read " + FILE + ": " + e.getMessage());
            }
        }

        String secret = props.getProperty("velocity.forwarding-secret", "").trim();
        String address = props.getProperty("host.address", LobbyConfig.Settings.HOST_ADDRESS).trim();
        int port;
        try {
            port = Integer.parseInt(props.getProperty("host.port", String.valueOf(LobbyConfig.Settings.HOST_PORT)).trim());
        } catch (NumberFormatException e) {
            port = LobbyConfig.Settings.HOST_PORT;
        }
        try {
            LobbyConfig.Settings.MAX_PLAYERS = Integer.parseInt(
                    props.getProperty("max-players", String.valueOf(LobbyConfig.Settings.MAX_PLAYERS)).trim());
        } catch (NumberFormatException e) {
            System.err.println("[ProxyConfig] Invalid max-players value, using default: " + LobbyConfig.Settings.MAX_PLAYERS);
        }
        return new ProxyConfig(secret, address, port);
    }

    private static void writeTemplate() {
        String template = """
                # =============================================
                # Proxy / forwarding configuration
                # =============================================
                # This file is auto-created on first launch.
                # Edit values, then restart the server.

                # Velocity modern forwarding secret.
                # Take this value from Velocity's `forwarding.secret` file.
                # When set (non-empty), Minestom will ONLY accept connections forwarded by
                # this Velocity instance — direct connections to this port will be rejected.
                # Leave empty to run without Velocity (plain offline mode).
                velocity.forwarding-secret=

                # Bind address for the Minecraft listener.
                # - If Velocity is on the SAME machine, set to 127.0.0.1 so the backend
                #   is not reachable from the outside.
                # - If Velocity is on a DIFFERENT machine, keep 0.0.0.0 and restrict
                #   access to Velocity's IP at the firewall level.
                host.address=0.0.0.0

                # Bind port. Should match the port you configured in velocity.toml under
                # the [servers] section for this backend (e.g. `lobby = "127.0.0.1:25566"`).
                host.port=25565

                # Maximum number of players allowed on the server simultaneously.
                # Players in BYPASS_USERS (ops) are never blocked regardless of this limit.
                # Shown in the server list MOTD. Requires restart to apply.
                max-players=100
                """;
        try {
            Files.writeString(FILE, template, StandardCharsets.UTF_8);
            System.out.println("[ProxyConfig] Created default " + FILE.toAbsolutePath());
            System.out.println("[ProxyConfig] Edit it to enable Velocity forwarding, then restart.");
        } catch (IOException e) {
            System.err.println("[ProxyConfig] Failed to write template " + FILE + ": " + e.getMessage());
        }
    }
}
