package ua.vsevolod.lobby.feature.parkour.leaderboard;

import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.BackgroundScheduler;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
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
        // Off-tick scheduling: this only fires off a virtual thread, so there's no reason for
        // the tick loop to host the timer. The actual loadEntries() runs on the virtual thread
        // (file or MongoDB I/O), and replaceEntries() is synchronized — safe from any thread.
        long periodMs = LobbyConfig.Parkour.LEADERBOARD_SYNC_MILLIS;
        BackgroundScheduler.SHARED.scheduleWithFixedDelay(
                () -> Thread.startVirtualThread(this::refreshFromStore),
                periodMs, periodMs, TimeUnit.MILLISECONDS
        );
    }

    public synchronized void submit(ParkourRunResult result) {
        // The actual write (Mongo upsert / file lock + write) used to run on the tick thread —
        // a finish() handler is invoked from PlayerMoveEvent, so any DB latency or another
        // server holding the file lock would stall the world. Now the write is offloaded to a
        // virtual thread; the local snapshot is updated immediately so the player still sees
        // their entry instantly. Periodic auto-refresh reconciles any drift if the async write
        // happens to fail.
        if (store instanceof ParkourLeaderboardSubmissionStore submissionStore) {
            replaceEntries(merge(entries, result));
            Thread.startVirtualThread(() -> {
                try { submissionStore.submitResult(result); }
                catch (Exception e) {
                    System.err.println("[ParkourLeaderboard] async submit failed: " + e.getMessage());
                }
            });
        } else {
            // File backend: updateEntries blocks on FileLock — must be off the tick.
            Thread.startVirtualThread(() -> {
                try {
                    List<ParkourLeaderboardEntry> updated = store.updateEntries(
                            currentEntries -> merge(currentEntries, result));
                    replaceEntries(updated);
                } catch (Exception e) {
                    System.err.println("[ParkourLeaderboard] async file update failed: " + e.getMessage());
                }
            });
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
