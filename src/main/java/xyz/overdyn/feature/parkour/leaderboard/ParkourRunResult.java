package xyz.overdyn.feature.parkour.leaderboard;

import java.util.UUID;

public record ParkourRunResult(
        UUID playerUuid,
        String playerName,
        int score,
        long durationMillis,
        long finishedAtEpochMillis
) {
}
