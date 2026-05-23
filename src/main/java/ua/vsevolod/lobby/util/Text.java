package ua.vsevolod.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Project text parser. <b>MiniMessage is the primary format</b> — every string passed here is
 * deserialized with {@link MiniMessage}, so tags like {@code <gradient>}, {@code <#FFF2E0>},
 * {@code <bold>} work everywhere (configs, code literals, menus).
 *
 * <h3>Legacy compatibility</h3>
 * <p>For backwards compatibility the parser also understands the old {@code &}/{@code §} codes
 * (including {@code &#RRGGBB} hex). They are transparently rewritten to MiniMessage tags before
 * deserialization, so configs written before the MiniMessage migration keep rendering. New code
 * and new config defaults should be written in MiniMessage.</p>
 *
 * <h3>Cache discipline</h3>
 * <p>{@link #c(String)} is for <b>constant</b> strings — short literals that recur many times.
 * It is backed by a {@link ConcurrentHashMap} for lock-free reads; if the cache grows past
 * {@link #CACHE_LIMIT} (only possible if a caller passes dynamic strings — a bug) it is cleared
 * as a safety net. {@link #raw(String)} is for <b>dynamic</b> strings (player names, counts,
 * timers) and never touches the cache.</p>
 */
public final class Text {

    private static final int CACHE_LIMIT = 1024;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final ConcurrentHashMap<String, Component> CACHE = new ConcurrentHashMap<>();

    // ── Legacy → MiniMessage rewrite ──────────────────────────────────────────
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)[&§]#([0-9A-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[&§]([0-9A-FK-OR])");

    private Text() {
    }

    /**
     * Cached parse — only for constant strings. Calling this with player-specific or other
     * dynamic data is a bug (defeats the cache and triggers the overflow safety clear).
     */
    public static Component c(String text) {
        if (text == null) return Component.empty();
        Component cached = CACHE.get(text);
        if (cached != null) return cached;
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        return CACHE.computeIfAbsent(text, Text::parse);
    }

    /** Uncached parse — use for dynamic strings (ping, online count, player names, …). */
    public static Component raw(String text) {
        if (text == null) return Component.empty();
        return parse(text);
    }

    private static Component parse(String text) {
        return MM.deserialize(legacyToMiniMessage(text));
    }

    /**
     * Rewrites old {@code &}/{@code §} colour and format codes into MiniMessage tags.
     * A string with no legacy codes is returned untouched, so genuine MiniMessage input
     * passes straight through.
     */
    public static String legacyToMiniMessage(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }

        Matcher hex = LEGACY_HEX.matcher(input);
        StringBuilder afterHex = new StringBuilder();
        while (hex.find()) {
            hex.appendReplacement(afterHex, "<#" + hex.group(1).toUpperCase() + ">");
        }
        hex.appendTail(afterHex);

        Matcher code = LEGACY_CODE.matcher(afterHex);
        StringBuilder out = new StringBuilder();
        while (code.find()) {
            String tag = legacyTag(Character.toLowerCase(code.group(1).charAt(0)));
            code.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        code.appendTail(out);
        return out.toString();
    }

    private static String legacyTag(char c) {
        return switch (c) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
