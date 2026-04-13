package ua.vsevolod.lobby.feature.parkour;

import java.util.ArrayList;
import java.util.List;

public final class ParkourTimeFormatter {

    private ParkourTimeFormatter() {
    }

    public static String compact(long durationMillis) {
        long safeDuration = Math.max(0L, durationMillis);
        long totalSeconds = safeDuration / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long centiseconds = (safeDuration % 1000) / 10;

        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm %02d.%02ds", minutes, seconds, centiseconds);
        }
        return String.format("%d.%02ds", seconds, centiseconds);
    }

    public static String leaderboard(long durationMillis) {
        long safeDuration = Math.max(0L, durationMillis);
        long totalSeconds = safeDuration / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long centiseconds = (safeDuration % 1000) / 10;

        return String.format("%02d:%02d.%02d", minutes, seconds, centiseconds);
    }

    public static String humanReadable(long durationMillis) {
        long safeDuration = Math.max(0L, durationMillis);
        long totalSeconds = safeDuration / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        List<String> parts = new ArrayList<>(3);
        if (hours > 0) {
            parts.add(hours + " " + plural(hours, "час", "часа", "часов"));
        }
        if (minutes > 0) {
            parts.add(minutes + " " + plural(minutes, "минута", "минуты", "минут"));
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + " " + plural(seconds, "секунда", "секунды", "секунд"));
        }

        return String.join(" ", parts);
    }

    private static String plural(long value, String one, String few, String many) {
        long normalized = Math.abs(value) % 100;
        long lastDigit = normalized % 10;

        if (normalized >= 11 && normalized <= 19) {
            return many;
        }
        if (lastDigit == 1) {
            return one;
        }
        if (lastDigit >= 2 && lastDigit <= 4) {
            return few;
        }
        return many;
    }
}
