package ua.vsevolod.lobby.feature.parkour.leaderboard;

import java.util.UUID;

public record ParkourRunResult(
        UUID playerUuid,
        String playerName,
        int score,
        long durationMillis,
        long finishedAtEpochMillis
) {
}
