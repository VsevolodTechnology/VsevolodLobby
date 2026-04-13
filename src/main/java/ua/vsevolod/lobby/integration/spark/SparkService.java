package ua.vsevolod.lobby.integration.spark;

import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.GenericStatistic;
import me.lucko.spark.minestom.SparkMinestom;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;

import java.nio.file.Path;
import java.util.Locale;

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

    public static String getMsptFormatted() {
        try {
            GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> mspt = spark().mspt();

            if (mspt == null) {
                return "0.00";
            }

            DoubleAverageInfo data = mspt.poll(StatisticWindow.MillisPerTick.SECONDS_10);
            if (data == null) {
                return "0.00";
            }

            double value = data.min();
            return String.format(Locale.US, "%.2f", value);
        } catch (Exception ignored) {
            return "0.00";
        }
    }
}