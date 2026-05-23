package ua.vsevolod.lobby.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Single shared daemon-thread scheduler for off-tick periodic / delayed work.
 *
 * <p>Anywhere we used to call {@code MinecraftServer.getSchedulerManager().buildTask(...)} just
 * to spawn a virtual thread (e.g. {@code MsptLogger}, {@code ParkourLeaderboardService},
 * {@link WriteBehindCache}), we now schedule on this executor instead — the tick loop sees
 * zero extra work for these timers.</p>
 *
 * <p>One platform thread is plenty: every consumer here finishes the scheduled callback in
 * microseconds (it just hands off to {@code Thread.startVirtualThread(...)} for the real
 * work). Daemon flag so JVM exit isn't blocked by this thread.</p>
 */
public final class BackgroundScheduler {

    public static final ScheduledExecutorService SHARED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Lobby-BackgroundScheduler");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    private BackgroundScheduler() {}
}
