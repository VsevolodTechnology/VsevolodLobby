package ua.vsevolod.lobby.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import ua.vsevolod.lobby.config.LobbyConfig;

/**
 * Project-wide message style.
 *
 * <p>Every system-emitted line shares the same visual envelope:</p>
 * <pre>[Система] текст…</pre>
 *
 * <p>Brackets render in dark gray, the brand label
 * <q>Система</q> uses the project's signature orange
 * ({@link LobbyConfig.Project#ORANGE_COLOR_TEXT_ORIGINAL}), and the body uses a calm
 * grayscale by default with accent colors for variants.</p>
 *
 * <p>Use the level helpers — {@link #info}, {@link #success}, {@link #warning}, {@link #error} —
 * for one-shot lines, and {@link #compose} when you need to mix accent fragments
 * ({@link #accent}, {@link #muted}) into a sentence.</p>
 */
public final class Messages {

    // ── Brand palette — all warm, sits around the project's #AE3AF3 / #FFF2E0 ─────────
    //
    // Anchor: the project brand is a warm orange and the headline text is a warm cream
    // (LobbyConfig.Project.{ORANGE,WHITE}_COLOR_TEXT_ORIGINAL). Every accent below is
    // chosen so it doesn't fight that brand — no cold blues, no cool greys.
    private static final TextColor BRACKET_COLOR = NamedTextColor.DARK_GRAY;
    private static final TextColor BRAND_COLOR   = LobbyConfig.Project.ORANGE_COLOR_TEXT_ORIGINAL; // #AE3AF3
    private static final TextColor TEXT_COLOR    = LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL;  // #FFF2E0
    private static final TextColor MUTED_COLOR   = TextColor.color(0x9A8E7A);  // warm taupe
    private static final TextColor ACCENT_COLOR  = TextColor.color(0xC58AF0);  // bright honey — highlights values
    private static final TextColor SUCCESS_COLOR = TextColor.color(0xB5D85E);  // warm olive-green
    private static final TextColor WARNING_COLOR = TextColor.color(0xD9A6F0);  // soft purple — on-brand "attention"
    private static final TextColor ERROR_COLOR   = TextColor.color(0xE36666);  // warm coral red

    private static final Style BRAND_STYLE = Style.style(BRAND_COLOR, TextDecoration.BOLD);

    private static final Component PREFIX = Component.empty()
            .append(Component.text("[", BRACKET_COLOR))
            .append(Component.text("Система", BRAND_STYLE))
            .append(Component.text("] ", BRACKET_COLOR));

    private Messages() {}

    // ── Prefix ────────────────────────────────────────────────────────────────

    /** The standalone 【Система】 prefix with trailing space. Prepend it manually when composing
     *  multi-fragment lines via {@link #compose}. */
    public static Component prefix() {
        return PREFIX;
    }

    // ── Level helpers — one-shot lines with the system prefix ────────────────

    public static Component info(String body) {
        return PREFIX.append(Component.text(body, TEXT_COLOR));
    }

    public static Component success(String body) {
        return PREFIX.append(Component.text(body, SUCCESS_COLOR));
    }

    public static Component warning(String body) {
        return PREFIX.append(Component.text(body, WARNING_COLOR));
    }

    public static Component error(String body) {
        return PREFIX.append(Component.text(body, ERROR_COLOR));
    }

    // ── Inline fragments — pieces meant to be appended into a single line ────

    /** Plain body text fragment in the standard text color (no prefix). */
    public static Component text(String body) {
        return Component.text(body, TEXT_COLOR);
    }

    /** Highlight color (cyan-blue) for player names, ids, values inside a sentence. */
    public static Component accent(String value) {
        return Component.text(value, ACCENT_COLOR);
    }

    /** Soft gray for secondary/parenthetical text. */
    public static Component muted(String value) {
        return Component.text(value, MUTED_COLOR);
    }

    /** Green fragment — successes within composed sentences. */
    public static Component successText(String value) {
        return Component.text(value, SUCCESS_COLOR);
    }

    /** Yellow fragment — warnings within composed sentences. */
    public static Component warningText(String value) {
        return Component.text(value, WARNING_COLOR);
    }

    /** Red fragment — errors within composed sentences. */
    public static Component errorText(String value) {
        return Component.text(value, ERROR_COLOR);
    }

    // ── Composition ──────────────────────────────────────────────────────────

    /** Prepends the system prefix to an externally-built body. */
    public static Component compose(Component body) {
        return PREFIX.append(body);
    }

    /** Same as {@link #compose(Component)} but joins multiple fragments. */
    public static Component compose(Component first, Component... rest) {
        Component out = PREFIX.append(first);
        for (Component piece : rest) out = out.append(piece);
        return out;
    }

    // ── Convenience send ─────────────────────────────────────────────────────

    public static void info(Audience to, String body) { to.sendMessage(info(body)); }
    public static void success(Audience to, String body) { to.sendMessage(success(body)); }
    public static void warning(Audience to, String body) { to.sendMessage(warning(body)); }
    public static void error(Audience to, String body) { to.sendMessage(error(body)); }
}
