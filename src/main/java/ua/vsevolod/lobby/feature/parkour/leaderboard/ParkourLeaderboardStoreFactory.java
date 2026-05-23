package ua.vsevolod.lobby.feature.parkour.leaderboard;

import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.config.StorageConfig;
import ua.vsevolod.lobby.util.ServerLogger;

import java.nio.file.Path;

public final class ParkourLeaderboardStoreFactory {

    private ParkourLeaderboardStoreFactory() {
    }

    public static ParkourLeaderboardStore create() {
        return switch (StorageConfig.get().leaderboard) {
            case FILE    -> createFileStore();
            case MONGODB -> createMongoOrFallback();
        };
    }

    private static ParkourLeaderboardStore createMongoOrFallback() {
        StorageConfig cfg = StorageConfig.get();
        try {
            MongoParkourLeaderboardStore store = new MongoParkourLeaderboardStore(
                    cfg.mongoUri, cfg.mongoDatabase, cfg.mongoLeaderboardCollection);
            registerShutdown(store);
            ServerLogger.get().info("Leaderboard storage: MongoDB ("
                    + cfg.mongoDatabase + "/" + cfg.mongoLeaderboardCollection + ")");
            return store;
        } catch (RuntimeException e) {
            if (!cfg.mongoFallbackToFile) throw e;
            ServerLogger.get().warn("Leaderboard MongoDB unavailable, using file storage");
            return createFileStore();
        }
    }

    private static ParkourLeaderboardStore createFileStore() {
        ServerLogger.get().detail("Leaderboard storage: file (" + LobbyConfig.Parkour.LEADERBOARD_FILE_PATH + ")");
        return new ParkourFileLeaderboardStore(Path.of(LobbyConfig.Parkour.LEADERBOARD_FILE_PATH));
    }

    private static void registerShutdown(MongoParkourLeaderboardStore store) {
        Runtime.getRuntime().addShutdownHook(new Thread(store::close, "parkour-leaderboard-mongo-close"));
    }
}
