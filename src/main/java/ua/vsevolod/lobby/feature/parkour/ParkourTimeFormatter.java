package ua.vsevolod.lobby.feature.parkour;

public final class ParkourTimeFormatter {

    private ParkourTimeFormatter() {
    }

    public static String compact(long durationMillis) {
        long safeDuration = Math.max(0L, durationMillis);
        long totalSeconds = safeDuration / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
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

        StringBuilder sb = new StringBuilder(32);
        if (hours > 0) {
            sb.append(hours).append(' ').append(plural(hours, "час", "часа", "часов"));
        }
        if (minutes > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(minutes).append(' ').append(plural(minutes, "минута", "минуты", "минут"));
        }
        if (seconds > 0 || sb.isEmpty()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(seconds).append(' ').append(plural(seconds, "секунда", "секунды", "секунд"));
        }

        return sb.toString();
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
