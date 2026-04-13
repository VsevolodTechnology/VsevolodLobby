package ua.vsevolod.lobby.feature.parkour.leaderboard;

import ua.vsevolod.lobby.config.LobbyConfig;

import java.nio.file.Path;

public final class ParkourLeaderboardStoreFactory {

    private ParkourLeaderboardStoreFactory() {
    }

    public static ParkourLeaderboardStore create() {
        return switch (LobbyConfig.Parkour.LEADERBOARD_STORAGE_MODE) {
            case FILE -> createFileStore();
            case MONGODB -> createMongoOrFallback();
        };
    }

    private static ParkourLeaderboardStore createMongoOrFallback() {
        try {
            MongoParkourLeaderboardStore store = new MongoParkourLeaderboardStore(
                    LobbyConfig.Parkour.Mongo.URI,
                    LobbyConfig.Parkour.Mongo.DATABASE,
                    LobbyConfig.Parkour.Mongo.COLLECTION
            );
            registerShutdown(store);
            System.out.println("[PARKOUR] Таблица лидеров подключена к MongoDB: "
                    + LobbyConfig.Parkour.Mongo.DATABASE + "/" + LobbyConfig.Parkour.Mongo.COLLECTION);
            return store;
        } catch (RuntimeException exception) {
            if (!LobbyConfig.Parkour.Mongo.FALLBACK_TO_FILE) {
                throw exception;
            }

            System.out.println("[PARKOUR] MongoDB недоступна, используем файловое хранилище: " + exception.getMessage());
            return createFileStore();
        }
    }

    private static ParkourLeaderboardStore createFileStore() {
        return new ParkourFileLeaderboardStore(Path.of(LobbyConfig.Parkour.LEADERBOARD_FILE_PATH));
    }

    private static void registerShutdown(MongoParkourLeaderboardStore store) {
        Runtime.getRuntime().addShutdownHook(new Thread(store::close, "parkour-leaderboard-mongo-close"));
    }
}
