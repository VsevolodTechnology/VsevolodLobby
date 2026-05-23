package ua.vsevolod.lobby.feature.lobby.player.join.welcome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows the animated gradient welcome title to a player after their lobby spawn.
 *
 * <h3>Animation model</h3>
 * Each character of the title text is given a colour interpolated between
 * {@code gradient_start} and {@code gradient_end} where the interpolation parameter is a
 * smooth sine wave whose phase is offset by character index. The phase is advanced by
 * {@code wave_speed} on every frame, so the wave appears to roll across the text. Decorations
 * ({@code &l}, {@code &o}, {@code &n}) on the configured raw text are applied uniformly to
 * every character; legacy and hex colour codes in the title are stripped (they would fight
 * the gradient).
 *
 * <h3>Packet discipline</h3>
 * We send {@link TitlePart#TIMES} once at the start with {@code fadeIn=0, fadeOut=0} and a
 * {@code stay} duration comfortably longer than the per-frame interval — that prevents
 * flicker between updates. The subtitle is sent once and never re-sent. Each frame fires a
 * single {@link TitlePart#TITLE} packet, which restarts the stay timer on the client.
 */
public final class WelcomeAnimationService {

    private final Map<UUID, Task> active = new ConcurrentHashMap<>();

    public void play(Player player, boolean firstJoin, long firstSeenEpoch) {
        WelcomeConfig cfg = WelcomeConfig.get();
        if (!cfg.titleEnabled) return;

        long days = firstSeenEpoch <= 0L ? 0L
                : Math.max(0L, (System.currentTimeMillis() - firstSeenEpoch) / 86_400_000L);

        String rawTitle    = firstJoin ? cfg.firstJoinTitle    : cfg.returningTitle;
        String rawSubtitle = firstJoin ? cfg.firstJoinSubtitle : cfg.returningSubtitle;
        rawTitle    = applyPlaceholders(rawTitle,    player.getUsername(), days);
        rawSubtitle = applyPlaceholders(rawSubtitle, player.getUsername(), days);

        DecoratedText titleSpec = fromMiniMessage(rawTitle);
        if (titleSpec.text.isEmpty()) return;

        TextColor cStart = parseColor(cfg.gradientStartHex, 0xC58AF0);
        TextColor cEnd   = parseColor(cfg.gradientEndHex,   0xAE3AF3);
        int frameTicks  = Math.max(1, cfg.frameTicks);
        int totalFrames = Math.max(1, cfg.durationTicks / frameTicks);

        cancel(player.getUuid());

        // Times: 0 fade-in, stay comfortably longer than one frame so frames overlap cleanly.
        Title.Times times = Title.Times.times(
                Duration.ZERO,
                Duration.ofMillis((long) frameTicks * 50L + 200L),
                Duration.ofMillis(400L));
        player.sendTitlePart(TitlePart.TIMES, times);
        player.sendTitlePart(TitlePart.SUBTITLE, Text.raw(rawSubtitle));

        int[] frame = {0};
        Task task = MinecraftServer.getSchedulerManager().submitTask(() -> {
            if (!player.isOnline()) {
                active.remove(player.getUuid());
                return TaskSchedule.stop();
            }
            Component animated = renderFrame(titleSpec, cStart, cEnd, frame[0],
                    cfg.waveSpeed, cfg.wavePeriod);
            player.sendTitlePart(TitlePart.TITLE, animated);
            frame[0]++;
            if (frame[0] >= totalFrames) {
                active.remove(player.getUuid());
                return TaskSchedule.stop();
            }
            return TaskSchedule.tick(frameTicks);
        });
        active.put(player.getUuid(), task);
    }

    public void cancel(UUID uuid) {
        Task t = active.remove(uuid);
        if (t != null) t.cancel();
    }

    private static String applyPlaceholders(String raw, String playerName, long days) {
        return raw
                .replace("{player}", playerName)
                .replace("{days_phrase}", daysPhrase(days))
                .replace("{days}", String.valueOf(days));
    }

    /**
     * Russian pluralization for the day count, with a friendlier zero case.
     *
     * <ul>
     *   <li>0 days → "первый день" (no number — "ноль дней" reads poorly on returning joins
     *       that happened the same day as registration)</li>
     *   <li>1, 21, 31, … → "N день"</li>
     *   <li>2..4, 22..24, … → "N дня"</li>
     *   <li>everything else (incl. 11..14) → "N дней"</li>
     * </ul>
     */
    static String daysPhrase(long days) {
        if (days <= 0L) return "первый день";
        long mod100 = days % 100L;
        long mod10  = days % 10L;
        if (mod100 >= 11 && mod100 <= 14) return days + " дней";
        if (mod10 == 1)                   return days + " день";
        if (mod10 >= 2 && mod10 <= 4)     return days + " дня";
        return days + " дней";
    }

    private static Component renderFrame(DecoratedText spec, TextColor c1, TextColor c2,
                                         int frame, double waveSpeed, double wavePeriod) {
        TextComponent.Builder b = Component.text();
        String s = spec.text;
        double period = wavePeriod <= 0.001 ? 4.0 : wavePeriod;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ' ') {
                b.append(Component.text(" "));
                continue;
            }
            double phase = i / period + frame * waveSpeed;
            double t = 0.5 + 0.5 * Math.sin(phase);
            TextColor c = lerp(c1, c2, t);
            Component piece = Component.text(ch, c);
            if (spec.bold)      piece = piece.decoration(TextDecoration.BOLD, true);
            if (spec.italic)    piece = piece.decoration(TextDecoration.ITALIC, true);
            if (spec.underline) piece = piece.decoration(TextDecoration.UNDERLINED, true);
            b.append(piece);
        }
        return b.build();
    }

    private static TextColor lerp(TextColor a, TextColor b, double t) {
        int r = (int) Math.round(a.red()   + (b.red()   - a.red())   * t);
        int g = (int) Math.round(a.green() + (b.green() - a.green()) * t);
        int bl = (int) Math.round(a.blue() + (b.blue()  - a.blue())  * t);
        return TextColor.color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static TextColor parseColor(String hex, int fallback) {
        if (hex == null) return TextColor.color(fallback);
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return TextColor.color(Integer.parseInt(h, 16));
        } catch (NumberFormatException e) {
            return TextColor.color(fallback);
        }
    }

    private record DecoratedText(String text, boolean bold, boolean italic, boolean underline) {}

    /**
     * Parses {@code raw} as MiniMessage (the project's text format) and extracts the plain
     * visible text plus its decoration flags. The colour itself is discarded — the per-frame
     * gradient owns the colour — but {@code <bold>}/{@code <italic>}/{@code <underlined>}
     * are kept and re-applied uniformly to every character.
     *
     * <p>This is what makes the title respect {@code <bold>} instead of printing the tag
     * literally — the animation builds {@code Component.text(char)} per glyph, so the title
     * string must be reduced to plain glyphs first.</p>
     */
    private static DecoratedText fromMiniMessage(String raw) {
        Component parsed = Text.raw(raw);
        StringBuilder text = new StringBuilder();
        boolean[] deco = {false, false, false}; // bold, italic, underline
        collect(parsed, text, deco);
        return new DecoratedText(text.toString(), deco[0], deco[1], deco[2]);
    }

    /** Depth-first walk: concatenates text content and OR-s decoration flags. */
    private static void collect(Component component, StringBuilder text, boolean[] deco) {
        if (component instanceof TextComponent tc) {
            text.append(tc.content());
        }
        if (component.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE)       deco[0] = true;
        if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE)     deco[1] = true;
        if (component.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE) deco[2] = true;
        for (Component child : component.children()) {
            collect(child, text, deco);
        }
    }
}
