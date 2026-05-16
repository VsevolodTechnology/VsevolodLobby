package ua.vsevolod.lobby.feature.parkour.leaderboard;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Боевой MongoDB store для лидерборда паркура.
 * Каждому игроку соответствует один документ, поэтому обновления одного результата
 * остаются атомарными даже если несколько лобби пишут почти одновременно.
 */
public final class MongoParkourLeaderboardStore implements ParkourLeaderboardSubmissionStore {

    private static final int MAX_RETRIES = 5;

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public MongoParkourLeaderboardStore(String uri, String databaseName, String collectionName) {
        this.client = MongoClients.create(uri);

        MongoDatabase database = client.getDatabase(databaseName);
        this.collection = database.getCollection(collectionName);

        database.runCommand(new Document("ping", 1));
        ensureIndexes();
    }

    @Override
    public List<ParkourLeaderboardEntry> loadEntries() {
        List<ParkourLeaderboardEntry> entries = new ArrayList<>();

        for (Document document : collection.find().sort(rankingSort())) {
            ParkourLeaderboardEntry entry = toEntry(document);
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }

    @Override
    public List<ParkourLeaderboardEntry> submitResult(ParkourRunResult result) {
        upsertBestResult(result);
        // CRIT-06 fix (audit 2026-05-16): previously this followed every write with a full
        // `loadEntries()` collection scan. The caller (ParkourLeaderboardService) now merges
        // the new result into its existing in-memory snapshot locally; drift is corrected by
        // the periodic `startAutoRefresh` cycle. Return value is intentionally empty — kept
        // for the interface contract but no longer consumed.
        return List.of();
    }

    @Override
    public List<ParkourLeaderboardEntry> updateEntries(UnaryOperator<List<ParkourLeaderboardEntry>> updater) {
        List<ParkourLeaderboardEntry> current = loadEntries();
        List<ParkourLeaderboardEntry> updated = updater.apply(current);

        collection.deleteMany(new Document());
        if (!updated.isEmpty()) {
            collection.insertMany(updated.stream().map(this::toDocument).toList());
        }

        return loadEntries();
    }

    @Override
    public void close() {
        client.close();
    }

    private void ensureIndexes() {
        collection.createIndex(Indexes.compoundIndex(
                Indexes.descending("score"),
                Indexes.ascending("durationMillis"),
                Indexes.ascending("updatedAtEpochMillis"),
                Indexes.ascending("playerName")
        ));
    }

    private void upsertBestResult(ParkourRunResult result) {
        String playerId = result.playerUuid().toString();
        Document replacement = toDocument(result);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Document currentDocument = collection.find(Filters.eq("_id", playerId)).first();
            if (currentDocument == null) {
                try {
                    collection.insertOne(replacement);
                    return;
                } catch (MongoWriteException exception) {
                    if (exception.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                        continue;
                    }
                    throw exception;
                }
            }

            ParkourLeaderboardEntry currentEntry = toEntry(currentDocument);
            if (currentEntry != null && !isBetter(result, currentEntry)) {
                return;
            }

            Object version = currentDocument.get("updatedAtEpochMillis");
            Bson filter = version == null
                    ? Filters.eq("_id", playerId)
                    : Filters.and(Filters.eq("_id", playerId), Filters.eq("updatedAtEpochMillis", version));

            UpdateResult updateResult = collection.replaceOne(filter, replacement);
            if (updateResult.getModifiedCount() == 1 || updateResult.getMatchedCount() == 1) {
                return;
            }
        }

        throw new IllegalStateException("Не удалось атомарно обновить результат игрока в MongoDB.");
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

    private Bson rankingSort() {
        return Sorts.orderBy(
                Sorts.descending("score"),
                Sorts.ascending("durationMillis"),
                Sorts.ascending("updatedAtEpochMillis"),
                Sorts.ascending("playerName")
        );
    }

    private Document toDocument(ParkourRunResult result) {
        return new Document("_id", result.playerUuid().toString())
                .append("playerUuid", result.playerUuid().toString())
                .append("playerName", result.playerName())
                .append("score", result.score())
                .append("durationMillis", result.durationMillis())
                .append("updatedAtEpochMillis", result.finishedAtEpochMillis());
    }

    private Document toDocument(ParkourLeaderboardEntry entry) {
        return new Document("_id", entry.playerUuid().toString())
                .append("playerUuid", entry.playerUuid().toString())
                .append("playerName", entry.playerName())
                .append("score", entry.score())
                .append("durationMillis", entry.durationMillis())
                .append("updatedAtEpochMillis", entry.updatedAtEpochMillis());
    }

    private ParkourLeaderboardEntry toEntry(Document document) {
        try {
            String uuidRaw = stringValue(document, "playerUuid", "_id");
            String playerName = stringValue(document, "playerName");
            int score = intValue(document, "score");
            long durationMillis = longValue(document, "durationMillis");
            long updatedAtEpochMillis = longValue(document, "updatedAtEpochMillis");

            return new ParkourLeaderboardEntry(
                    UUID.fromString(uuidRaw),
                    playerName,
                    score,
                    durationMillis,
                    updatedAtEpochMillis
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String stringValue(Document document, String... keys) {
        for (String key : keys) {
            Object value = document.get(key);
            if (value instanceof String string && !string.isBlank()) {
                return string;
            }
        }

        throw new IllegalStateException("String field is missing");
    }

    private int intValue(Document document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }

        throw new IllegalStateException("Integer field is missing");
    }

    private long longValue(Document document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalStateException("Long field is missing");
    }
}
