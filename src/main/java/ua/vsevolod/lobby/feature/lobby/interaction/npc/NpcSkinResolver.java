package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.minestom.server.entity.PlayerSkin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a skin string from the NPC config into a {@link PlayerSkin}.
 *
 * <p>Supported formats:</p>
 * <ul>
 *   <li><b>Username</b>: {@code Notch} — Mojang lookup via {@link PlayerSkin#fromUsername}.</li>
 *   <li><b>Texture URL</b>: {@code url:https://textures.minecraft.net/texture/...} — fetched
 *       through {@code api.mineskin.org} which returns the signed texture pair Minecraft
 *       requires. Lookup is async; result is cached per-URL.</li>
 *   <li><b>Raw value</b>: {@code value:<base64>} or {@code value:<base64>;sig:<base64>} —
 *       texture and (optional) signature passed straight through. Use this when you already
 *       have a pre-generated signed skin and want zero network hops at startup.</li>
 * </ul>
 *
 * <p>For URL/Mineskin lookups, the call is offloaded to a {@linkplain Thread#startVirtualThread
 * virtual thread} so the server bootstrap / reload does not block on HTTP. While the texture
 * downloads, the NPC spawns with no custom skin — once the future completes,
 * {@link #onResolved} is invoked so the manager can re-apply the entity meta.</p>
 */
public final class NpcSkinResolver {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Pattern VALUE_SIG = Pattern.compile(
            "value:(?<value>[^;]+)(;sig:(?<sig>.+))?",
            Pattern.CASE_INSENSITIVE
    );

    /** Successful resolutions cached forever; same spec returns the same {@link PlayerSkin}. */
    private static final Map<String, PlayerSkin> CACHE = new ConcurrentHashMap<>();

    private NpcSkinResolver() {}

    /**
     * Resolve {@code spec} synchronously if cached or trivially derivable.
     * Returns {@code null} if the lookup needs network — in that case the caller should
     * pass an {@code onResolved} callback to be notified when async resolution finishes.
     */
    public static PlayerSkin resolveSync(String spec) {
        if (spec == null || spec.isBlank()) return null;

        PlayerSkin cached = CACHE.get(spec);
        if (cached != null) return cached;

        String s = spec.trim();
        // url:... — must be async
        if (s.regionMatches(true, 0, "url:", 0, 4) ||
                s.regionMatches(true, 0, "http://", 0, 7) ||
                s.regionMatches(true, 0, "https://", 0, 8)) {
            return null;
        }

        // value:<base64>[;sig:<base64>]
        Matcher m = VALUE_SIG.matcher(s);
        if (m.matches()) {
            String value = m.group("value");
            String sig = m.group("sig");
            PlayerSkin skin = new PlayerSkin(value, sig == null ? "" : sig);
            CACHE.put(spec, skin);
            return skin;
        }

        // Plain username — mojang lookup; PlayerSkin.fromUsername does synchronous HTTP but
        // Minestom caches it internally. Acceptable on startup; falls back to null on miss.
        try {
            PlayerSkin skin = PlayerSkin.fromUsername(s);
            if (skin != null) CACHE.put(spec, skin);
            return skin;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolve a URL-bearing spec asynchronously. The callback runs on a virtual thread
     * after the Mineskin request completes (or fails — then it never fires).
     */
    public static void resolveAsync(String spec, java.util.function.Consumer<PlayerSkin> onResolved) {
        if (spec == null || spec.isBlank()) return;
        PlayerSkin cached = CACHE.get(spec);
        if (cached != null) {
            onResolved.accept(cached);
            return;
        }

        String url = extractUrl(spec);
        if (url == null) return; // not actually a URL spec

        Thread.startVirtualThread(() -> {
            try {
                PlayerSkin skin = fetchFromMineskin(url);
                if (skin != null) {
                    CACHE.put(spec, skin);
                    onResolved.accept(skin);
                }
            } catch (Exception e) {
                System.err.println("[NpcSkinResolver] Failed to fetch skin from " + url + ": " + e.getMessage());
            }
        });
    }

    private static String extractUrl(String spec) {
        String s = spec.trim();
        if (s.regionMatches(true, 0, "url:", 0, 4)) return s.substring(4).trim();
        if (s.regionMatches(true, 0, "http://", 0, 7)) return s;
        if (s.regionMatches(true, 0, "https://", 0, 8)) return s;
        return null;
    }

    /**
     * Calls api.mineskin.org which converts a texture URL into a signed Mojang skin.
     * Free tier, no auth required. Response JSON shape:
     * <pre>{ "data": { "texture": { "value": "...", "signature": "..." } } }</pre>
     */
    private static PlayerSkin fetchFromMineskin(String url) throws Exception {
        String endpoint = "https://api.mineskin.org/generate/url?url=" +
                URI.create(url).toASCIIString().replace("&", "%26");
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "VsevolodLobby/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            System.err.println("[NpcSkinResolver] Mineskin returned " + resp.statusCode() + " for " + url);
            return null;
        }
        String body = resp.body();
        String value = extractJsonField(body, "value");
        String sig = extractJsonField(body, "signature");
        if (value == null) return null;
        return new PlayerSkin(value, sig == null ? "" : sig);
    }

    /** Tiny JSON field extractor — avoids pulling in Gson just for two strings. */
    private static String extractJsonField(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(?<v>[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group("v") : null;
    }

    /** Test helper / refresh hook — wipes cached skins so the next resolve refetches. */
    public static void clearCache() {
        CACHE.clear();
    }
}
