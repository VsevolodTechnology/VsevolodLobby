package ua.vsevolod.lobby.util;

import ua.vsevolod.lobby.config.LoggingConfig;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Central server logger.
 *
 * <p>Console receives only important operational messages (startup, stop, restart,
 * player join/leave, critical errors). Files receive everything.
 *
 * <p>File writes happen on a dedicated virtual-thread writer so the game loop is never
 * blocked by I/O. Errors during file writing are reported to stderr and never crash the server.
 */
public final class ServerLogger {

    // ── Singleton ────────────────────────────────────────────────────────────

    private static volatile ServerLogger INSTANCE;

    public static ServerLogger get() {
        ServerLogger inst = INSTANCE;
        if (inst != null) return inst;
        synchronized (ServerLogger.class) {
            if (INSTANCE == null) INSTANCE = new ServerLogger(LoggingConfig.defaults());
            return INSTANCE;
        }
    }

    /** Must be called once at startup to apply the real config. */
    public static void init(LoggingConfig config) {
        synchronized (ServerLogger.class) {
            ServerLogger old = INSTANCE;
            INSTANCE = new ServerLogger(config);
            if (old != null) old.shutdown();
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────

    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LoggingConfig config;
    private final ZoneId tz;

    // Background async writer
    private final BlockingQueue<String> fileQueue = new LinkedBlockingQueue<>(65_536);
    private final Thread writerThread;

    private volatile PrintWriter latestLog;
    private volatile PrintWriter dailyLog;
    private volatile String currentDate;

    // ── Constructor ──────────────────────────────────────────────────────────

    private ServerLogger(LoggingConfig config) {
        this.config = config;
        this.tz = config.timezone;
        openFiles();
        this.writerThread = Thread.ofVirtual().name("server-logger").start(this::writerLoop);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Console + file. Use for: startup, stop, restart, connect/disconnect, critical. */
    public void info(String message) {
        log(Level.INFO, message, config.toConsole, true);
    }

    /** File only. Use for: mode selection, session events, leaderboard details. */
    public void detail(String message) {
        log(Level.INFO, message, false, config.toFile);
    }

    /** Console + file. Always shown regardless of toConsole setting. */
    public void warn(String message) {
        log(Level.WARN, message, true, true);
    }

    /** Console + file. Always shown. */
    public void error(String message) {
        log(Level.ERROR, message, true, true);
    }

    /** File only. Printed only if debugLogs=true. */
    public void debug(String message) {
        if (config.debugLogs) log(Level.DEBUG, message, false, config.toFile);
    }

    /**
     * Prints the server-ready line with a full timestamp.
     * The line contains {@code Done (X.XXXs)! For help, type "help"} which Pterodactyl
     * detects via a substring regex — the timestamp prefix does not break detection.
     * Always printed to stdout regardless of logToConsole setting.
     */
    public void pterodactylReady(double uptimeSec) {
        String readyMsg = String.format("Done (%.3fs)! For help, type \"help\"", uptimeSec);
        String formatted = format(Level.INFO, readyMsg);
        System.out.println(formatted);
        enqueueFile(formatted);
    }

    // ── Convenience wrappers checked against config flags ──────────────────

    public void playerConnect(String playerName, int protocolVersion) {
        String suffix = protocolVersion > 0 ? " via protocol " + protocolVersion : "";
        String message = "Player " + playerName + " connected" + suffix;
        if (config.logPlayerConnect) info(message);
        else detail(message);
    }

    public void playerDisconnect(String playerName) {
        if (config.logPlayerDisconnect) info("Player " + playerName + " disconnected");
        else detail("Player " + playerName + " disconnected");
    }

    public void playerMode(String playerName, String modeName) {
        if (config.logPlayerMode) detail("Player " + playerName + " selected mode: " + modeName);
    }

    public void playerModeChange(String playerName, String oldMode, String newMode) {
        if (config.logPlayerMode) detail("Player " + playerName + " changed mode: " + oldMode + " → " + newMode);
    }

    public void sessionStart(String playerName, String modeName) {
        detail("Parkour session started: player=" + playerName + ", mode=" + modeName);
    }

    public void sessionEnd(String playerName, String modeName, int score, String time) {
        detail("Parkour session ended: player=" + playerName + ", mode=" + modeName
                + ", score=" + score + ", time=" + time);
    }

    public void leaderboardSaved(String playerName, String modeName, int score) {
        if (config.logLeaderboardEvents)
            detail("Result saved to leaderboard: player=" + playerName + ", mode=" + modeName + ", score=" + score);
    }

    public void leaderboardIgnored(String playerName, String modeName) {
        if (config.logLeaderboardEvents)
            detail("Result ignored for leaderboard: player=" + playerName + ", mode=" + modeName
                    + " (non-competitive mode)");
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void log(Level level, String message, boolean toConsole, boolean toFile) {
        String formatted = format(level, message);
        if (toConsole) System.out.println(formatted);
        if (toFile && config.toFile) enqueueFile(formatted);
    }

    private String format(Level level, String message) {
        ZonedDateTime now = ZonedDateTime.now(tz);
        return String.format("[%s %s] [%s] %s", STAMP_FMT.format(now), tz.getId(), level.name(), message);
    }

    private void enqueueFile(String line) {
        if (!fileQueue.offer(line)) {
            // Queue full — write direct to stderr to avoid losing the line silently
            System.err.println("[ServerLogger] Queue full, dropping: " + line);
        }
    }

    private void writerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String line = fileQueue.take();
                if (line == POISON_PILL) break;
                writeToFiles(line);
                // Drain remaining items in one batch
                String next;
                while ((next = fileQueue.poll()) != null) {
                    if (next == POISON_PILL) return;
                    writeToFiles(next);
                }
                flushFiles();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        flushFiles();
    }

    private static final String POISON_PILL = "<<SHUTDOWN>>";

    private void writeToFiles(String line) {
        try {
            rotateDailyIfNeeded();
            PrintWriter latest = latestLog;
            PrintWriter daily = dailyLog;
            if (latest != null) latest.println(line);
            if (daily != null) daily.println(line);
        } catch (Exception e) {
            System.err.println("[ServerLogger] File write error: " + e.getMessage());
        }
    }

    private void flushFiles() {
        try {
            PrintWriter latest = latestLog;
            PrintWriter daily = dailyLog;
            if (latest != null) latest.flush();
            if (daily != null) daily.flush();
        } catch (Exception ignored) {}
    }

    private void openFiles() {
        if (!config.toFile) return;
        try {
            Path logsDir = Paths.get("storage", "logs");
            Files.createDirectories(logsDir);
            currentDate = LocalDate.now(tz).toString();
            latestLog = openWriter(logsDir.resolve("latest.log"), false);
            dailyLog  = openWriter(logsDir.resolve(currentDate + ".log"), true);
        } catch (IOException e) {
            System.err.println("[ServerLogger] Cannot open log files: " + e.getMessage());
        }
    }

    private void rotateDailyIfNeeded() {
        String today = LocalDate.now(tz).toString();
        if (today.equals(currentDate)) return;
        try {
            PrintWriter old = dailyLog;
            if (old != null) { old.flush(); old.close(); }
            currentDate = today;
            dailyLog = openWriter(Paths.get("storage", "logs", today + ".log"), true);
        } catch (IOException e) {
            System.err.println("[ServerLogger] Cannot rotate daily log: " + e.getMessage());
        }
    }

    private static PrintWriter openWriter(Path path, boolean append) throws IOException {
        return new PrintWriter(new BufferedWriter(new FileWriter(path.toFile(), append)));
    }

    private void shutdown() {
        fileQueue.offer(POISON_PILL);
        try { writerThread.join(3000); } catch (InterruptedException ignored) {}
        flushFiles();
        PrintWriter latest = latestLog;
        PrintWriter daily = dailyLog;
        if (latest != null) { latest.flush(); latest.close(); }
        if (daily != null) { daily.flush(); daily.close(); }
    }

    public void close() {
        shutdown();
    }

    // ── Level enum ────────────────────────────────────────────────────────────

    public enum Level { INFO, WARN, ERROR, DEBUG }
}
