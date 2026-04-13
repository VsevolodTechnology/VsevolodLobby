package xyz.overdyn.feature.parkour.leaderboard;

import java.util.Comparator;
import java.util.UUID;

public record ParkourLeaderboardEntry(
        UUID playerUuid,
        String playerName,
        int score,
        long durationMillis,
        long updatedAtEpochMillis
) {

    public static final Comparator<ParkourLeaderboardEntry> RANKING = Comparator
            .comparingInt(ParkourLeaderboardEntry::score).reversed()
            .thenComparingLong(ParkourLeaderboardEntry::durationMillis)
            .thenComparingLong(ParkourLeaderboardEntry::updatedAtEpochMillis)
            .thenComparing(ParkourLeaderboardEntry::playerName, String.CASE_INSENSITIVE_ORDER);
}
