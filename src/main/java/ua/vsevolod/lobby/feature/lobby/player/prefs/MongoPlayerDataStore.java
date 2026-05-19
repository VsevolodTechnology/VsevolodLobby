package ua.vsevolod.lobby.feature.lobby.player.prefs;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import net.minestom.server.coordinate.Pos;
import org.bson.Document;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB-backed {@link PlayerDataStore}. Collection: {@code player_data}.
 * One document per player, upserted on every save.
 */
public final class MongoPlayerDataStore implements PlayerDataStore {

    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public MongoPlayerDataStore(String uri, String databaseName) {
        // Use a short server-selection timeout so the first failed operation
        // (in preload()) reports quickly instead of blocking for 30 seconds.
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(b -> b
                        .maxSize(20)
                        .minSize(2)
                        .maxConnectionIdleTime(5, TimeUnit.MINUTES))
                .build();
        this.client = MongoClients.create(settings);
        MongoDatabase db = client.getDatabase(databaseName);
        this.collection = db.getCollection("player_data");
        // No ping here — let the first actual operation fail gracefully rather
        // than blocking server startup for 30 s when MongoDB is unavailable.
    }

    @Override
    public PlayerPreferences load(UUID uuid) {
        Document doc = collection.find(Filters.eq("_id", uuid.toString())).first();
        if (doc == null) return PlayerPreferences.defaults();

        boolean music = doc.getBoolean("musicEnabled", true);
        boolean hidden = doc.getBoolean("playersHidden", false);
        boolean sidebarHidden = doc.getBoolean("sidebarHidden", false);
        boolean positionSaveEnabled = doc.getBoolean("positionSaveEnabled", true);

        Pos pos = null;
        if (Boolean.TRUE.equals(doc.getBoolean("hasPosition", false))) {
            try {
                double x = doc.getDouble("lastX");
                double y = doc.getDouble("lastY");
                double z = doc.getDouble("lastZ");
                float yaw   = doc.getDouble("lastYaw").floatValue();
                float pitch = doc.getDouble("lastPitch").floatValue();
                pos = new Pos(x, y, z, yaw, pitch);
            } catch (Exception ignored) {}
        }

        return new PlayerPreferences(music, hidden, sidebarHidden, positionSaveEnabled, pos);
    }

    @Override
    public void save(UUID uuid, PlayerPreferences prefs) {
        Document doc = new Document("_id", uuid.toString())
                .append("musicEnabled", prefs.musicEnabled())
                .append("playersHidden", prefs.playersHidden())
                .append("sidebarHidden", prefs.sidebarHidden())
                .append("positionSaveEnabled", prefs.positionSaveEnabled());

        Pos pos = prefs.lastPosition();
        if (pos != null) {
            doc.append("hasPosition", true)
               .append("lastX", pos.x())
               .append("lastY", pos.y())
               .append("lastZ", pos.z())
               .append("lastYaw",   (double) pos.yaw())
               .append("lastPitch", (double) pos.pitch());
        } else {
            doc.append("hasPosition", false);
        }

        collection.replaceOne(Filters.eq("_id", uuid.toString()), doc, UPSERT);
    }

    @Override
    public void close() {
        client.close();
    }
}
