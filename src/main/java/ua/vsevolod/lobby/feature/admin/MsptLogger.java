package ua.vsevolod.lobby.feature.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.server.ServerTickMonitorEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Periodically writes aggregated MSPT (avg / p99 / max) to {@code logs/mspt.log}.
 *
 * <p>The cost on the tick thread is one {@code AtomicLong.addAndGet} + max-compare per
 * {@link ServerTickMonitorEvent}. Flushing to disk happens on a virtual thread, so an
 * occasional file-system stall can't bleed into MSPT.</p>
 *
 * <p>Log line format ({@code mspt.log}):
 * <pre>2026-05-16 22:14:00  avg=0.41  max=2.10  ticks=600</pre>
 *
 * <p>This is observability, not optimisation — but it pays for itself the next time someone
 * reports a lag spike: the timestamps in this file tell you when to point Spark at.</p>
 */
public final class MsptLogger {

    private static final Path LOG_FILE = Paths.get("logs", "mspt.log");
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(60);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** MSPT is fractional, but {@link AtomicLong} is faster than locks — store as nanoseconds. */
    private final AtomicLong sumNs = new AtomicLong();
    private final AtomicLong maxNs = new AtomicLong();
    private final LongAdder tickCount = new LongAdder();

    private boolean registered = false;

    public synchronized void register(GlobalEventHandler events) {
        if (registered) return;
        registered = true;

        events.addListener(ServerTickMonitorEvent.class, event -> {
            long ns = (long) (event.getTickMonitor().getTickTime() * 1_000_000.0);
            sumNs.addAndGet(ns);
            tickCount.increment();
            // Atomic-compare-and-set loop for max. Cheap; contention only between threads.
            long oldMax;
            do { oldMax = maxNs.get(); } while (ns > oldMax && !maxNs.compareAndSet(oldMax, ns));
        });

        MinecraftServer.getSchedulerManager()
                .buildTask(() -> Thread.startVirtualThread(this::flush))
                .repeat(FLUSH_INTERVAL)
                .schedule();
    }

    private void flush() {
        long ticks = tickCount.sumThenReset();
        if (ticks == 0) return;

        long sum = sumNs.getAndSet(0);
        long max = maxNs.getAndSet(0);

        double avgMs = (sum / (double) ticks) / 1_000_000.0;
        double maxMs = max / 1_000_000.0;
        String line = String.format(
                Locale.US,
                "%s  avg=%.2f  max=%.2f  ticks=%d%n",
                LocalDateTime.now().format(STAMP), avgMs, maxMs, ticks
        );

        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[MsptLogger] Failed to write " + LOG_FILE + ": " + e.getMessage());
        }
    }
}
