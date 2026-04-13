package xyz.overdyn.feature.parkour.leaderboard;

import net.minestom.server.MinecraftServer;
import xyz.overdyn.config.LobbyConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ParkourLeaderboardService {

    private final ParkourLeaderboardStore store;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private List<ParkourLeaderboardEntry> entries = List.of();

    public ParkourLeaderboardService(ParkourLeaderboardStore store) {
        this.store = store;
        this.entries = store.loadEntries();
    }

    public void startAutoRefresh() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::refreshFromStore)
                .repeat(Duration.ofMillis(LobbyConfig.Parkour.LEADERBOARD_SYNC_MILLIS))
                .schedule();
    }

    public synchronized void submit(ParkourRunResult result) {
        List<ParkourLeaderboardEntry> updated;
        if (store instanceof ParkourLeaderboardSubmissionStore submissionStore) {
            updated = submissionStore.submitResult(result);
        } else {
            updated = store.updateEntries(currentEntries -> merge(currentEntries, result));
        }
        replaceEntries(updated);
    }

    public synchronized int bestScore(UUID playerUuid) {
        return entries.stream()
                .filter(entry -> entry.playerUuid().equals(playerUuid))
                .findFirst()
                .map(ParkourLeaderboardEntry::score)
                .orElse(0);
    }

    public synchronized Optional<ParkourLeaderboardEntry> bestEntry(UUID playerUuid) {
        return entries.stream()
                .filter(entry -> entry.playerUuid().equals(playerUuid))
                .findFirst();
    }

    public synchronized List<ParkourLeaderboardEntry> topEntries(int limit) {
        return entries.stream()
                .limit(limit)
                .toList();
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void refreshFromStore() {
        replaceEntries(store.loadEntries());
    }

    private synchronized void replaceEntries(List<ParkourLeaderboardEntry> newEntries) {
        List<ParkourLeaderboardEntry> normalized = newEntries.stream()
                .sorted(ParkourLeaderboardEntry.RANKING)
                .toList();

        if (entries.equals(normalized)) {
            return;
        }

        entries = normalized;
        notifyListeners();
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private List<ParkourLeaderboardEntry> merge(List<ParkourLeaderboardEntry> currentEntries, ParkourRunResult result) {
        List<ParkourLeaderboardEntry> merged = new ArrayList<>(currentEntries);

        int index = findEntryIndex(merged, result.playerUuid());
        if (index < 0) {
            merged.add(new ParkourLeaderboardEntry(
                    result.playerUuid(),
                    result.playerName(),
                    result.score(),
                    result.durationMillis(),
                    result.finishedAtEpochMillis()
            ));
            return merged;
        }

        ParkourLeaderboardEntry current = merged.get(index);
        if (!isBetter(result, current)) {
            return merged;
        }

        merged.set(index, new ParkourLeaderboardEntry(
                result.playerUuid(),
                result.playerName(),
                result.score(),
                result.durationMillis(),
                result.finishedAtEpochMillis()
        ));
        return merged;
    }

    private int findEntryIndex(List<ParkourLeaderboardEntry> entries, UUID playerUuid) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).playerUuid().equals(playerUuid)) {
                return index;
            }
        }
        return -1;
    }

    private boolean isBetter(ParkourRunResult result, ParkourLeaderboardEntry current) {
        if (result.score() != current.score()) {
            return result.score() > current.score();
        }

        if (result.durationMillis() != current.durationMillis()) {
            return result.durationMillis() < current.durationMillis();
        }

        return result.finishedAtEpochMillis() > current.updatedAtEpochMillis();
    }
}
