package ua.vsevolod.lobby.feature.admin.restart;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;
import ua.vsevolod.lobby.bootstrap.LobbyShutdown;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.BackgroundScheduler;
import ua.vsevolod.lobby.util.Messages;
import ua.vsevolod.lobby.util.ServerLogger;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates a graceful, visible server restart.
 *
 * <p>The countdown runs on a single background-scheduler task, never touching the tick loop —
 * every per-second update queues a single batch of audience effects (title / action bar / sound)
 * onto the global audience. At t=0 we kick every player with the configured message, flush
 * persistence, then {@code System.exit(0)}.</p>
 *
 * <h3>Cadence</h3>
 * <ul>
 *   <li><b>Major marks</b> ({@code 30, 15, 10}) — full title + sound + chat line.</li>
 *   <li><b>Final 5 seconds</b> — title every second, escalating pitch, action bar each tick.</li>
 *   <li><b>Every second of last 10</b> — action bar refresh so players see the time even if
 *       they missed the title.</li>
 * </ul>
 *
 * <p>Asynchronous: nothing in the loop blocks. Sound / title sends are O(online players) and
 * happen on the background thread — Minestom serialises packet writes per-connection anyway.</p>
 */
public final class RestartCountdownService {

    private static final TextColor BRAND  = LobbyConfig.Project.ORANGE_COLOR_TEXT_ORIGINAL; // #AE3AF3
    private static final TextColor TEXT   = LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL;  // #FFF2E0 — surrounding words
    private static final TextColor ACCENT = TextColor.color(0xC58AF0);                       // honey — only the value
    private static final TextColor MUTED  = TextColor.color(0x9A8E7A);                       // warm taupe — the ⟳ icon

    /** When emitting the final-shutdown task, give Minestom this long to flush the kick packets. */
    private static final Duration KICK_GRACE = Duration.ofMillis(500);

    /** Seconds at which we emit a loud title + chat line in addition to the action bar. */
    private static final Set<Integer> ANNOUNCE_MARKS = Set.of(300, 180, 60, 30, 15, 10, 5, 4, 3, 2, 1);

    /** Subset of {@link #ANNOUNCE_MARKS} that also gets a chat line — avoid spamming chat
     *  during the final five seconds where the title is enough. */
    private static final Set<Integer> CHAT_MARKS = Set.of(300, 180, 60, 30, 15, 10);

    private static final RestartCountdownService INSTANCE = new RestartCountdownService();

    /** Currently active countdown, null when idle. */
    private final AtomicReference<Running> active = new AtomicReference<>();

    private RestartCountdownService() {}

    public static RestartCountdownService get() {
        return INSTANCE;
    }

    /** Whether a countdown is currently in progress. */
    public boolean isActive() {
        return active.get() != null;
    }

    /**
     * Start a {@code totalSeconds}-second countdown. If a countdown is already active, returns
     * false — admin must {@link #cancel} the existing one first.
     */
    public boolean start(int totalSeconds) {
        if (totalSeconds < 1) totalSeconds = 1;
        Running fresh = new Running(totalSeconds);
        if (!active.compareAndSet(null, fresh)) return false;

        ServerLogger.get().info("Restart countdown started: " + totalSeconds + "s");
        announceStart(totalSeconds);
        fresh.future = BackgroundScheduler.SHARED.scheduleAtFixedRate(
                () -> tick(fresh), 1, 1, TimeUnit.SECONDS);
        return true;
    }

    /** Cancel an in-progress countdown. */
    public boolean cancel() {
        Running current = active.getAndSet(null);
        if (current == null) return false;
        if (current.future != null) current.future.cancel(false);
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> {
            p.sendMessage(Messages.warning("Перезапуск отменён."));
            p.clearTitle();
        });
        ServerLogger.get().info("Restart countdown cancelled");
        return true;
    }

    // ── Loop ──────────────────────────────────────────────────────────────────

    private void tick(Running r) {
        int remaining = r.remaining.decrementAndGet();
        if (remaining > 0) {
            broadcast(remaining);
            return;
        }
        // Reached zero — kick + schedule shutdown, then clear active.
        active.compareAndSet(r, null);
        ScheduledFuture<?> f = r.future;
        if (f != null) f.cancel(false);
        executeShutdown();
    }

    // ── Audience effects ──────────────────────────────────────────────────────

    private void announceStart(int seconds) {
        // Surrounding words in cream, the time alone in honey — only the changing value pops.
        Component line = Messages.compose(
                Messages.text("Сервер перезапустится через "),
                Messages.accent(seconds + " " + secondsWord(seconds)),
                Messages.text("."));
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> p.sendMessage(line));
        broadcast(seconds);
    }

    private void broadcast(int remaining) {
        Component bar = actionBar(remaining);
        Title title = ANNOUNCE_MARKS.contains(remaining) ? buildTitle(remaining) : null;
        Sound sound = soundFor(remaining);
        Component chatLine = CHAT_MARKS.contains(remaining)
                ? Messages.compose(
                        Messages.text("Перезапуск через "),
                        Messages.accent(remaining + " " + secondsWord(remaining)),
                        Messages.text("."))
                : null;

        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            p.sendActionBar(bar);
            if (title != null) p.showTitle(title);
            if (sound != null) p.playSound(sound);
            if (chatLine != null) p.sendMessage(chatLine);
        }
    }

    private Component actionBar(int remaining) {
        // Same hierarchy as the chat / subtitle: muted icon, cream surrounding text, honey value.
        return Component.text()
                .append(Component.text("⟳ ", MUTED))
                .append(Component.text("Перезапуск через ", TEXT))
                .append(Component.text(String.valueOf(remaining), ACCENT))
                .append(Component.text(" " + secondsWord(remaining), TEXT))
                .build();
    }

    private static final Style MAIN_STYLE = Style.style(BRAND, TextDecoration.ITALIC);

    private Title buildTitle(int remaining) {
        // Headline = the message itself in italic orange (not bold — too heavy).
        // Subtitle = "через N секунд": surrounding words in cream, only the value in honey
        // so the changing number is the visual focus.
        Component main = Component.text("Сервер перезапускается", MAIN_STYLE);
        Component sub = Component.text()
                .append(Component.text("через ", TEXT))
                .append(Component.text(String.valueOf(remaining), ACCENT))
                .append(Component.text(" " + secondsWord(remaining), TEXT))
                .build();
        // Final 5-second tick titles get a short stay (one second each, so they don't overlap).
        // Everything else (the bigger headline marks at 300/180/60/30/15/10) lingers ~2.5s.
        Title.Times times = remaining <= 5
                ? Title.Times.times(
                        Duration.ofMillis(80),
                        Duration.ofMillis(800),
                        Duration.ofMillis(180))
                : Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2500),
                        Duration.ofMillis(400));
        return Title.title(main, sub, times);
    }

    private @Nullable Sound soundFor(int remaining) {
        // Final 5..1 — escalating, dramatic ticks. The user found the chimes "weak"; we use a
        // tight cluster of NOTE_BLOCK_BIT + dragon growl at t=1 for impact.
        if (remaining == 1) {
            return Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, Sound.Source.MASTER, 0.4f, 1.5f);
        }
        if (remaining <= 5) {
            float pitch = 1.0f + (5 - remaining) * 0.15f;
            return Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BIT, Sound.Source.MASTER, 1.0f, pitch);
        }
        if (remaining == 10 || remaining == 15) {
            return Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BELL, Sound.Source.MASTER, 0.7f, 1.0f);
        }
        if (remaining == 30 || remaining == 60 || remaining == 180 || remaining == 300) {
            return Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 0.8f, 1.0f);
        }
        return null;
    }

    private static String secondsWord(int n) {
        int last = n % 10;
        int lastTwo = n % 100;
        if (lastTwo >= 11 && lastTwo <= 14) return "секунд";
        return switch (last) {
            case 1 -> "секунду";
            case 2, 3, 4 -> "секунды";
            default -> "секунд";
        };
    }

    // ── Final shutdown ────────────────────────────────────────────────────────

    private void executeShutdown() {
        ServerLogger.get().info("Server restarting...");
        LobbyConfig.Settings.SHUTTING_DOWN = true;

        Component kick = RestartConfig.kickMessage();
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> p.kick(kick));

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            LobbyShutdown.flushAllPersistence();
            MinecraftServer.stopCleanly();
            ServerLogger.get().close();
            System.exit(0);
        }).delay(KICK_GRACE).schedule();
    }

    // ── State holder ──────────────────────────────────────────────────────────

    private static final class Running {
        final java.util.concurrent.atomic.AtomicInteger remaining;
        volatile @Nullable ScheduledFuture<?> future;

        Running(int seconds) {
            this.remaining = new java.util.concurrent.atomic.AtomicInteger(seconds);
        }
    }
}
