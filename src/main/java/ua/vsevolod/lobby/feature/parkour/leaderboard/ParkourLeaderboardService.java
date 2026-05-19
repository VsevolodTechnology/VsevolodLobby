package ua.vsevolod.lobby.feature.parkour.leaderboard;

import net.minestom.server.MinecraftServer;
import ua.vsevolod.lobby.config.LobbyConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ParkourLeaderboardService {

    private final ParkourLeaderboardStore store;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private List<ParkourLeaderboardEntry> entries = List.of();
    private Map<UUID, ParkourLeaderboardEntry> entryByUuid = new HashMap<>();

    public ParkourLeaderboardService(ParkourLeaderboardStore store) {
        this.store = store;
        replaceEntries(store.loadEntries());
    }

    public void startAutoRefresh() {
        // store.loadEntries() blocks on file or MongoDB I/O; run it on a virtual thread so the
        // tick scheduler doesn't stall waiting for network. The result is then handed back to
        // the tick by replaceEntries() (synchronized — safe to call from any thread).
        MinecraftServer.getSchedulerManager()
                .buildTask(() -> Thread.startVirtualThread(this::refreshFromStore))
                .repeat(Duration.ofMillis(LobbyConfig.Parkour.LEADERBOARD_SYNC_MILLIS))
                .schedule();
    }

    public synchronized void submit(ParkourRunResult result) {
        if (store instanceof ParkourLeaderboardSubmissionStore submissionStore) {
            // Persist the upsert but DON'T pay for a full-collection re-read. We merge the new
            // result into our existing snapshot in-memory; the periodic auto-refresh cycle
            // reconciles any drift with the database. Audit CRIT-06.
            submissionStore.submitResult(result);
            replaceEntries(merge(entries, result));
        } else {
            replaceEntries(store.updateEntries(currentEntries -> merge(currentEntries, result)));
        }
    }

    public synchronized int bestScore(UUID playerUuid) {
        ParkourLeaderboardEntry e = entryByUuid.get(playerUuid);
        return e != null ? e.score() : 0;
    }

    public synchronized Optional<ParkourLeaderboardEntry> bestEntry(UUID playerUuid) {
        return Optional.ofNullable(entryByUuid.get(playerUuid));
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
        Map<UUID, ParkourLeaderboardEntry> index = new HashMap<>(normalized.size());
        for (ParkourLeaderboardEntry e : normalized) {
            index.put(e.playerUuid(), e);
        }
        entryByUuid = index;
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
