package xyz.overdyn.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;
import xyz.overdyn.bootstrap.module.InstanceModule;
import xyz.overdyn.util.Text;

import java.net.InetSocketAddress;
import java.util.List;

public final class LobbyConfig {

    private LobbyConfig() {
    }

    public static final class Project {
        public static final String NAME = "OVERDYN";

        public static final SocialLinks SOCIAL_LINKS = new SocialLinks(
                "t.me/OverdynMC", "dsc.gg/Overdyn", "vk.com/overdyn", "overdyn.xyz"
        );

        public static final String SERVER_IP = "play.overdyn.xyz";
        public static final String WEBSITE = "overdyn.xyz";
        public static final String DISCORD = "discord.gg/overdyn";

        public static final String WHITE_COLOR_ORIGINAL = "&#FFF2E0";
        public static final TextColor WHITE_COLOR_TEXT_ORIGINAL = TextColor.color(0xFFF2E0);

        public record SocialLinks(
                String telegram,
                String discord,
                String vk,
                String website
        ) {}

        private Project() {
        }
    }

    public static final class Settings {
        public static final String WORLD_MAP_PATH = "worlds/lobby";
        public static final String HOST_ADDRESS = "0.0.0.0";
        public static final int HOST_PORT = 25565;
        public static final List<String> BYPASS_USERS = List.of("LPVania");
        public static final GameMode DEFAULT_GAME_MODE = GameMode.ADVENTURE;
        public static final String IDENTIFIER_VELOCITY_MESSAGE = "stefan_protocol";
        public static final Tag<Integer> IDENTIFIER_CLIENT_PROTOCOL =
                Tag.Integer("stefanlobby:version:client_protocol");

        public static final byte REQUEST_PROTOCOL = 0x01;
        public static final byte RESPONSE_PROTOCOL = 0x02;

        public static volatile boolean SHUTTING_DOWN = false;

        /**
         * Protocol versions that require compatibility workarounds when the backend
         * Minestom server is running behind a proxy with protocol translation
         * (Velocity / BungeeCord + ViaVersion / ViaBackwards).
         *
         * <p>These versions are known to cause issues during the Minecraft
         * configuration phase due to registry and tag synchronization changes.
         * When this happens, clients may disconnect with a
         * <i>"Network Protocol Error"</i>.</p>
         *
         * <p>Upstream reference:</p>
         * https://github.com/Minestom/Minestom/issues/3061
         *
         * <p>Additional context:</p>
         * https://github.com/ViaVersion/ViaVersion/issues/4241
         *
         * <p><b>Affected protocol versions:</b></p>
         * <ul>
         *     <li>766 → Minecraft 1.20.5 / 1.20.6</li>
         *     <li>767 → Minecraft 1.21 / 1.21.1</li>
         * </ul>
         *
         * <p>The protocol version is propagated from the proxy to the backend
         * server using a {@link com.velocitypowered.api.util.GameProfile.Property}.
         * This allows the backend to determine the exact client version even
         * when ViaVersion performs protocol translation.</p>
         *
         * <p><b>Velocity example:</b></p>
         *
         * <pre>{@code
         * int protocol = Via.getAPI().getPlayerVersion(player.getUniqueId());
         *
         * player.setGameProfileProperties(List.of(
         *     new GameProfile.Property(
         *         "stefan_protocol",
         *         Integer.toString(protocol),
         *         ""
         *     )
         * ));
         * }</pre>
         *
         * <p><b>BungeeCord example:</b></p>
         *
         * <pre>{@code
         * int protocol = Via.getAPI().getPlayerVersion(player.getUniqueId());
         *
         * player.getPendingConnection().getLoginProfile().getProperties().add(
         *     new Property(
         *         "stefan_protocol",
         *         Integer.toString(protocol),
         *         ""
         *     )
         * );
         * }</pre>
         *
         * <p>The backend Minestom server can then read the protocol and apply
         * compatibility logic such as:</p>
         *
         * <ul>
         *     <li>temporary packet workarounds</li>
         *     <li>feature toggles</li>
         *     <li>bossbar version warnings</li>
         * </ul>
         *
         * <p>Example usage for displaying a bossbar recommendation:</p>
         *
         * <pre>{@code
         * if (PROTOCOLS_WITH_WORKAROUNDS.contains(protocol)) {
         *     // show bossbar recommending a newer Minecraft version
         * }
         * }</pre>
         */
        public static final List<Integer> PROTOCOLS_WITH_WORKAROUNDS =
                List.of(766);

        /**
         * Temporary workaround toggle for protocol compatibility issues.
         *
         * <p>This flag enables a temporary patch that mitigates disconnects caused by
         * protocol translation when the backend Minestom server runs behind a proxy
         * (Velocity / BungeeCord) with ViaVersion / ViaBackwards.</p>
         *
         * <p>The issue occurs during the Minecraft configuration phase due to
         * registry and tag synchronization differences between protocol versions.
         * In affected cases, clients may disconnect with a
         * <i>"Network Protocol Error"</i>.</p>
         *
         * <p>This workaround currently targets the following protocol versions:</p>
         * <ul>
         *     <li>766 → Minecraft 1.20.5 / 1.20.6</li>
         *     <li>767 → Minecraft 1.21 / 1.21.1</li>
         * </ul>
         *
         * <p>The root cause appears to be related to protocol translation
         * (ViaVersion / ViaBackwards) or upstream Minecraft changes in the
         * registry/tag system.</p>
         *
         * <p>This patch acts as a temporary compatibility layer until the issue
         * is resolved upstream.</p>
         *
         * <p><b>Enable this if players experience configuration-phase disconnects.</b></p>
         */
        public static final boolean ENABLE_PROTOCOL_WORKAROUND = false;

        /**
         * Controls whether the VOID_GUARD protection system is active.
         * <p>
         * The VOID_GUARD system prevents players from falling into the void by
         * generating a block beneath them once they pass the configured
         * void threshold level.
         *
         * @value true if the protection system is enabled, false otherwise.
         */
        public static final boolean VOID_GUARD = true;

        public static final InetSocketAddress HOST = new InetSocketAddress(HOST_ADDRESS, HOST_PORT);

        private Settings() {

        }
    }

    public static final class Messages {
        public static final List<String> WELCOME_MSG = List.of(
                "",
                " &#EA1B40&lʜᴏᴛᴡᴏʀʟᴅ &8» &#FFF2E0Добро пожаловать, &#FFE259{player}&#FFF2E0!",
                " &#FFF2E0Рады видеть тебя на нашем проекте.",
                "",
                " &6&l| &#FFF2E0Основное меню: &e/menu",
                " &6&l| &#FFF2E0Выбор режима: &6Компас &7(в руке)",
                " &6&l| &#FFF2E0Наш магазин: &#FFE259www.hotworld.su",
                "",
                " &e▶ &#FFF2E0Используй &eдвойной прыжок&#FFF2E0, чтобы перемещаться быстрее!",
                ""
        );
        public static final String SHUTTING_DOWN_MSG = "§cServer is shutting down...";
        public static final String SHUTTING_DOWN_KICKING_MSG = "Stopping server...";

        private Messages() {

        }

        public static Component buildVersionWarning(int secondsLeft) {
            return Component.text()
                    .append(Component.text("⚠ Рекомендуем использовать новейшую версию 1.21.11 ", TextColor.color(0xE36666)))
                    .append(Component.text("[" + secondsLeft + " сек]", NamedTextColor.GRAY))
                    .build();
        }

        public static Component welcome(String playerName) {
            String joined = String.join("\n", WELCOME_MSG)
                    .replace("{player}", playerName);

            return Text.c(joined);
        }
    }

    public static final class Commands {

        public static final class Spawn {
            public static final String COMMAND_NAME = "spawn";
            public static final List<String> COMMAND_ALIASES = List.of("hub", "рги", "lobby", "дщиин", "ызфцт");

            private Spawn() {}
        }

        public static final class GameMode {
            public static final String COMMAND_NAME = "gamemode";
            public static final List<String> COMMAND_ALIASES = List.of("gm", "пфьуьщву", "пь", "minecraft:gamemode");

            private GameMode() {}
        }

        private Commands() {

        }
    }

    public static final class Locations {
        public static final Pos SPAWN_POS_PLAYER = new Pos(0.5, 80, 0.5, 180f, 0f);
        public static final Pos MODE_SELECTOR_NPC_POS = new Pos(0.5, 77.0, -29.5, 0f, 0f);

        /**
         * Minimum Y-level before a player is considered to be in the void.
         * <p>
         * When a player's Y position falls below this value,
         * they are teleported back to the spawn location.
         */
        public static final double VOID_THRESHOLD_Y = 76.0; //TODO 76.0
        public static final Instance VOID_THRESHOLD_INSTANCE_WORLD = InstanceModule.lobby; //TODO 76.0

        private Locations() {

        }
    }

    public static final class Parkour {
        public static final Pos NPC_POS = new Pos(6.5, 79.0, -4.5, 52f, 0f);
        public static final String NPC_SKIN_USERNAME = "Dream";
        public static final Pos LEADERBOARD_HOLOGRAM_POS = new Pos(22.5, 82.5, -21.5, 90f, 0f);
        public static final LeaderboardStorageMode LEADERBOARD_STORAGE_MODE = leaderboardStorageMode(
                "overdyn.parkour.leaderboard.storage",
                "OVERDYN_PARKOUR_LEADERBOARD_STORAGE",
                LeaderboardStorageMode.FILE
        );
        public static final String LEADERBOARD_FILE_PATH = "storage/parkour/leaderboard.tsv";
        public static final int LEADERBOARD_MAX_ENTRIES = 10;
        public static final long LEADERBOARD_SYNC_MILLIS = 3000L;
        public static final Pos START_POS = new Pos(0.5, 65.0, 0.5);
        public static final int MAX_VISIBLE_BLOCKS = 4;
        public static final int FAIL_Y = 58;

        private Parkour() {
        }

        public enum LeaderboardStorageMode {
            FILE,
            MONGODB
        }

        public static final class Mongo {
            public static final String URI = propertyOrEnv(
                    "overdyn.mongo.uri",
                    "OVERDYN_MONGO_URI",
                    "mongodb://127.0.0.1:27017"
            );
            public static final String DATABASE = propertyOrEnv(
                    "overdyn.mongo.database",
                    "OVERDYN_MONGO_DATABASE",
                    "overdyn"
            );
            public static final String COLLECTION = propertyOrEnv(
                    "overdyn.mongo.collection",
                    "OVERDYN_MONGO_COLLECTION",
                    "parkour_leaderboard"
            );
            public static final boolean FALLBACK_TO_FILE = propertyOrEnvBoolean(
                    "overdyn.mongo.fallbackToFile",
                    "OVERDYN_MONGO_FALLBACK_TO_FILE",
                    true
            );

            private Mongo() {
            }
        }
    }

    public static final class Lobby {
        public static final String WELCOME = "Добро пожаловать!";
        public static final String MODE_1 = "Скорее выбирай режим";
        public static final String MODE_2 = "для игры на сервере";
        public static final String MODE_3 = "и начинай свой путь";

        private Lobby() {
        }
    }

    public static final class Sections {
        public static final String MODES = "↶ Режимы онлайн ↷";
        public static final String PING = "➜ Ваш пинг: ";
        public static final String SOON = "Скоро";

        private Sections() {
        }
    }

    public static final class Animation {
        public static final List<String> TITLE = List.of(
                "&#FFB300&lO&#FFBE1A&lV&#FFC933&lE&#FFD44D&lR&#FFDF66&lD&#FFEA80&lY&#FFF599&lN",
                "&#FFE066&lO&#FFD94D&lV&#FFD233&lE&#FFCB1A&lR&#FFC400&lD&#FFB000&lY&#FF9C00&lN",
                "&#F7C948&lO&#F5B73D&lV&#F3A533&lE&#F19228&lR&#EF801E&lD&#ED6D13&lY&#EB5B09&lN",
                "&#FF9700&lO&#FFA31A&lV&#FFAF33&lE&#FFBB4D&lR&#FFC766&lD&#FFD380&lY&#FFDF99&lN"
        );

        private Animation() {
        }
    }

    private static String propertyOrEnv(String propertyName, String envName, String fallback) {
        String systemValue = System.getProperty(propertyName);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        return fallback;
    }

    private static boolean propertyOrEnvBoolean(String propertyName, String envName, boolean fallback) {
        String value = propertyOrEnv(propertyName, envName, Boolean.toString(fallback));
        return Boolean.parseBoolean(value);
    }

    private static Parkour.LeaderboardStorageMode leaderboardStorageMode(
            String propertyName,
            String envName,
            Parkour.LeaderboardStorageMode fallback
    ) {
        String rawValue = propertyOrEnv(propertyName, envName, fallback.name());
        try {
            return Parkour.LeaderboardStorageMode.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
