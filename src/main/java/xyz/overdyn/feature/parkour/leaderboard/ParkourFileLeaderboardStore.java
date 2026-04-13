package xyz.overdyn.feature.parkour.leaderboard;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Файловое хранилище лидерборда.
 * Для синхронизации между несколькими лобби все инстансы должны смотреть в один общий файл
 * на шареном диске или volume.
 */
public final class ParkourFileLeaderboardStore implements ParkourLeaderboardStore {

    private static final String HEADER = "uuid\tplayerName\tscore\tdurationMillis\tupdatedAtEpochMillis";

    private final Path filePath;
    private final Path lockPath;

    public ParkourFileLeaderboardStore(Path filePath) {
        this.filePath = filePath;
        this.lockPath = filePath.resolveSibling(filePath.getFileName() + ".lock");
        ensureParentDirectory();
    }

    @Override
    public List<ParkourLeaderboardEntry> loadEntries() {
        return readEntries();
    }

    @Override
    public List<ParkourLeaderboardEntry> updateEntries(UnaryOperator<List<ParkourLeaderboardEntry>> updater) {
        ensureParentDirectory();

        try (FileChannel ignored = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = ignored.lock()) {
            List<ParkourLeaderboardEntry> current = readEntries();
            List<ParkourLeaderboardEntry> updated = normalize(updater.apply(current));
            writeEntries(updated);
            return List.copyOf(updated);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось обновить таблицу лидеров паркура.", exception);
        }
    }

    private List<ParkourLeaderboardEntry> readEntries() {
        ensureParentDirectory();

        if (!Files.exists(filePath)) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            List<ParkourLeaderboardEntry> entries = new ArrayList<>();

            for (String line : lines) {
                if (line.isBlank() || HEADER.equals(line)) {
                    continue;
                }

                ParkourLeaderboardEntry entry = parseEntry(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }

            return normalize(entries);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать таблицу лидеров паркура.", exception);
        }
    }

    private ParkourLeaderboardEntry parseEntry(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 5) {
            return null;
        }

        try {
            return new ParkourLeaderboardEntry(
                    UUID.fromString(parts[0]),
                    parts[1],
                    Integer.parseInt(parts[2]),
                    Long.parseLong(parts[3]),
                    Long.parseLong(parts[4])
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void writeEntries(List<ParkourLeaderboardEntry> entries) throws IOException {
        ensureParentDirectory();

        StringBuilder builder = new StringBuilder(HEADER).append('\n');
        for (ParkourLeaderboardEntry entry : entries) {
            builder.append(entry.playerUuid()).append('\t')
                    .append(entry.playerName()).append('\t')
                    .append(entry.score()).append('\t')
                    .append(entry.durationMillis()).append('\t')
                    .append(entry.updatedAtEpochMillis()).append('\n');
        }

        Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        Files.writeString(
                tempFile,
                builder.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        try {
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<ParkourLeaderboardEntry> normalize(List<ParkourLeaderboardEntry> entries) {
        Map<UUID, ParkourLeaderboardEntry> uniqueEntries = new LinkedHashMap<>();
        for (ParkourLeaderboardEntry entry : entries) {
            uniqueEntries.merge(entry.playerUuid(), entry, this::pickBetterEntry);
        }

        return uniqueEntries.values().stream()
                .sorted(ParkourLeaderboardEntry.RANKING)
                .toList();
    }

    private ParkourLeaderboardEntry pickBetterEntry(ParkourLeaderboardEntry left, ParkourLeaderboardEntry right) {
        ParkourRunResult candidate = new ParkourRunResult(
                right.playerUuid(),
                right.playerName(),
                right.score(),
                right.durationMillis(),
                right.updatedAtEpochMillis()
        );

        return isBetter(candidate, left) ? right : left;
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

    private void ensureParentDirectory() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось подготовить директорию для таблицы лидеров.", exception);
        }
    }
}
