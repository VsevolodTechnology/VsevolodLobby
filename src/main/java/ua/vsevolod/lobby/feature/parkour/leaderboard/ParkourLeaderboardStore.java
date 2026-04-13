package ua.vsevolod.lobby.feature.parkour.leaderboard;

import java.util.List;
import java.util.function.UnaryOperator;

public interface ParkourLeaderboardStore extends AutoCloseable {

    /**
     * Загружает актуальный снимок лидерборда из внешнего хранилища.
     */
    List<ParkourLeaderboardEntry> loadEntries();

    /**
     * Атомарно обновляет данные в хранилище и возвращает уже нормализованный результат.
     * Этот контракт позволяет без изменения сервиса заменить файловую реализацию на MongoDB.
     */
    List<ParkourLeaderboardEntry> updateEntries(UnaryOperator<List<ParkourLeaderboardEntry>> updater);

    @Override
    default void close() {
    }
}
