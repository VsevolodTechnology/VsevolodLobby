package ua.vsevolod.lobby.integration.spark;

import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;
import me.lucko.spark.minestom.SparkMinestom;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Wrapper around Spark's metrics so every UI surface (tab, sidebar, StatsBar) reads from a
 * single authoritative source.
 *
 * <h3>Display semantics</h3>
 * <p>The previous implementation returned {@code data.min()} over a 10-second window —
 * effectively "the best single tick in the last 10 s". That value was unstable and misleading
 * (server actually felt slower than the displayed MSPT). Both getters now return
 * {@code data.mean()} (arithmetic average), with windows chosen for responsiveness:</p>
 * <ul>
 *   <li><b>MSPT</b> → 10 s window. MSPT swings naturally per tick; 10 s is short enough to
 *       react to a real slowdown within a few seconds.</li>
 *   <li><b>TPS</b> → 5 s window (the shortest Spark exposes). TPS is already a derived
 *       1-second-bucketed value so the underlying smoothing makes a 5 s mean stable enough
 *       to display.</li>
 * </ul>
 */
public final class SparkService {

    private SparkService() {
    }

    public static void init(Path directory) {
        SparkMinestom.builder(directory)
                .commands(true)
                .permissionHandler((sender, permission) -> {
                    if (sender instanceof Player player) {
                        return LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
                    }
                    return true;
                })
                .enable();
    }

    private static Spark spark() {
        return SparkProvider.get();
    }

    /** Mean MSPT over the last minute. */
    public static String getMsptFormatted() {
        Double v = pollMspt();
        return v == null ? "0.00" : String.format(Locale.US, "%.2f", v);
    }

    /** Numeric MSPT mean — for callers that need the raw number (e.g. StatsBarService). */
    public static double getMspt() {
        Double v = pollMspt();
        return v == null ? 50.0 : v;
    }

    /** Mean TPS over the last minute, capped at 20.0. */
    public static double getTps() {
        Double v = pollTps();
        return v == null ? 20.0 : Math.min(20.0, v);
    }

    public static String getTpsFormatted() {
        return String.format(Locale.US, "%.2f", getTps());
    }

    private static Double pollMspt() {
        try {
            Spark spark = spark();
            GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> stat = spark.mspt();
            if (stat == null) return null;
            DoubleAverageInfo data = stat.poll(StatisticWindow.MillisPerTick.SECONDS_10);
            if (data == null || Double.isNaN(data.mean())) return null;
            return data.mean();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double pollTps() {
        try {
            Spark spark = spark();
            DoubleStatistic<StatisticWindow.TicksPerSecond> stat = spark.tps();
            if (stat == null) return null;
            double v = stat.poll(StatisticWindow.TicksPerSecond.SECONDS_5);
            return Double.isNaN(v) || v <= 0 ? null : v;
        } catch (Exception ignored) {
            return null;
        }
    }
}
