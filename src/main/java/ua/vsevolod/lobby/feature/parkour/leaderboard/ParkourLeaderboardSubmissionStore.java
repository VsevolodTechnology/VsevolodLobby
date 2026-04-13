package ua.vsevolod.lobby.feature.parkour.leaderboard;

import java.util.List;

public interface ParkourLeaderboardSubmissionStore extends ParkourLeaderboardStore {

    List<ParkourLeaderboardEntry> submitResult(ParkourRunResult result);
}
