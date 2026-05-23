package ua.vsevolod.lobby.feature.lobby.player.chat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Anti-advertising chat filter.
 *
 * <p>Detects plain links, domain patterns, IP addresses, and obfuscated
 * variants (spaced-out letters, "(dot)" notation, comma-as-dot, etc.).
 */
public final class ChatFilter {

    // TLDs commonly used in Minecraft server advertising
    private static final List<String> AD_TLDS = List.of(
            "com", "net", "org", "ru", "xyz", "gg", "io", "su", "me",
            "info", "biz", "tv", "club", "online", "site", "store", "shop",
            "mc", "fun", "pro", "pw", "tk", "cf", "ga", "ml", "top", "cc"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?|ftp)://\\S+", Pattern.CASE_INSENSITIVE);

    private static final Pattern WWW_PATTERN = Pattern.compile(
            "\\bwww\\s*\\.\\s*[a-z0-9]", Pattern.CASE_INSENSITIVE);

    // IPv4: digits separated by dots or spaces (allows single-space obfuscation like "1 2 3 4")
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(\\d{1,3})[.\\s]+(\\d{1,3})[.\\s]+(\\d{1,3})[.\\s]+(\\d{1,3})\\b");

    // Legacy & color codes and &#RRGGBB hex codes — strip before domain matching
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile(
            "(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]|§[0-9a-fk-or]");

    private static final Pattern DOMAIN_PATTERN;

    static {
        String tlds = String.join("|", AD_TLDS);
        DOMAIN_PATTERN = Pattern.compile(
                "[a-z0-9\\-]{2,}\\s*\\.\\s*(?:" + tlds + ")\\b",
                Pattern.CASE_INSENSITIVE);
    }

    private final boolean blockLinks;
    private final boolean blockDomains;
    private final boolean blockIps;
    private final boolean blockObfuscated;

    public ChatFilter(boolean blockLinks, boolean blockDomains,
                      boolean blockIps, boolean blockObfuscated) {
        this.blockLinks = blockLinks;
        this.blockDomains = blockDomains;
        this.blockIps = blockIps;
        this.blockObfuscated = blockObfuscated;
    }

    /** Returns {@code true} if the message should be blocked. */
    public boolean isBlocked(String raw) {
        // Strip any color/formatting codes first — they can be used to visually break domain names
        String stripped = COLOR_CODE_PATTERN.matcher(raw).replaceAll("");
        String lower = stripped.toLowerCase(Locale.ROOT);
        String normalized = normalize(lower);

        if (check(normalized)) return true;

        if (blockObfuscated) {
            String collapsed = collapseSpacedLetters(normalized);
            if (!collapsed.equals(normalized) && check(collapsed)) return true;
        }

        return false;
    }

    private boolean check(String s) {
        if (blockLinks && (URL_PATTERN.matcher(s).find() || WWW_PATTERN.matcher(s).find())) return true;
        if (blockDomains && DOMAIN_PATTERN.matcher(s).find()) return true;
        if (blockIps && hasValidIp(s)) return true;
        return false;
    }

    /**
     * Normalizes common obfuscation tricks:
     * <ul>
     *   <li>{@code (dot)}, {@code [dot]}, {@code (.)}, {@code dot}, {@code дот} → {@code .}</li>
     *   <li>Comma followed by a letter/digit at a word boundary → {@code .}</li>
     *   <li>Dashes between alphanumeric chars removed</li>
     *   <li>Underscores between alphanumeric chars removed</li>
     *   <li>Zero-width / invisible chars removed</li>
     * </ul>
     */
    private static String normalize(String s) {
        return s
                // (.) or ( . ) — literal dot in parentheses
                .replaceAll("\\(\\s*\\.\\s*\\)", ".")
                // (dot), [dot], {dot}, keyword variants
                .replaceAll("\\(\\s*dot\\s*\\)|\\[\\s*dot\\s*]|\\{\\s*dot\\s*}|\\bдот\\b|\\bdot\\b", ".")
                // comma used instead of dot: "play,server,com"
                .replaceAll(",(?=[a-z0-9])", ".")
                // dashes/underscores between chars: "p-l-a-y" → "play", "mc_server" → "mcserver"
                .replaceAll("(?<=[a-z0-9])[-_](?=[a-z0-9])", "")
                // remove zero-width and other invisible Unicode spaces
                .replaceAll("[\\u200B-\\u200D\\uFEFF\\u00AD]", "");
    }

    /**
     * Collapses spaced-out letter sequences: {@code "p l a y . s e r v e r . c o m"} → {@code "play.server.com"}.
     * Only collapses runs of single characters separated by single spaces.
     */
    private static String collapseSpacedLetters(String s) {
        // Replace " " between single alphanumeric tokens
        return s.replaceAll("(?<=\\b[a-z0-9]) (?=[a-z0-9]\\b)", "");
    }

    /** Returns {@code true} if the string contains a plausible IPv4 address (all octets 0–255). */
    private static boolean hasValidIp(String s) {
        Matcher m = IP_PATTERN.matcher(s);
        while (m.find()) {
            try {
                int a = Integer.parseInt(m.group(1));
                int b = Integer.parseInt(m.group(2));
                int c = Integer.parseInt(m.group(3));
                int d = Integer.parseInt(m.group(4));
                if (a <= 255 && b <= 255 && c <= 255 && d <= 255) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
