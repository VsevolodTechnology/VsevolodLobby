package ua.vsevolod.lobby.feature.admin;

import net.kyori.adventure.bossbar.BossBar;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import ua.vsevolod.lobby.integration.spark.SparkService;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsBarService {

    private static final StatsBarService INSTANCE = new StatsBarService();

    public static StatsBarService get() {
        return INSTANCE;
    }

    // Audit MED-04: maps were keyed by Player (strong reference). If a player was kicked during
    // configuration phase before PlayerDisconnectEvent fired (or any other rare cleanup miss)
    // the entry pinned the Player + its network connection forever. UUID is the canonical
    // identity used everywhere else in the codebase; the Player is resolved lazily for sends.
    private final Map<UUID, BossBar> tpsBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> ramBars = new ConcurrentHashMap<>();

    /** Skip-if-unchanged caches. */
    private final Map<UUID, String> lastTpsLabel = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastRamLabel = new ConcurrentHashMap<>();

    private boolean registered = false;

    private StatsBarService() {
    }

    public synchronized void register(GlobalEventHandler events) {
        if (registered) return;
        registered = true;

        // MSPT now read from Spark instead of the local tick monitor — no need to listen.

        events.addListener(PlayerDisconnectEvent.class, event -> {
            UUID id = event.getPlayer().getUuid();
            BossBar t = tpsBars.remove(id);
            if (t != null) event.getPlayer().hideBossBar(t);
            BossBar r = ramBars.remove(id);
            if (r != null) event.getPlayer().hideBossBar(r);
            lastTpsLabel.remove(id);
            lastRamLabel.remove(id);
        });

        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(Duration.ofMillis(500))
                .schedule();
    }

    public boolean toggleTps(Player player) {
        UUID id = player.getUuid();
        BossBar existing = tpsBars.remove(id);
        if (existing != null) {
            player.hideBossBar(existing);
            return false;
        }
        BossBar bar = BossBar.bossBar(
                Text.raw("&eTPS: ... &eMSPT: ... &ePing: ..."),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );
        tpsBars.put(id, bar);
        player.showBossBar(bar);
        return true;
    }

    public boolean toggleRam(Player player) {
        UUID id = player.getUuid();
        BossBar existing = ramBars.remove(id);
        if (existing != null) {
            player.hideBossBar(existing);
            return false;
        }
        BossBar bar = BossBar.bossBar(
                Text.raw("&eRAM: ..."),
                0.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );
        ramBars.put(id, bar);
        player.showBossBar(bar);
        return true;
    }

    private void tick() {
        boolean haveTps = !tpsBars.isEmpty();
        boolean haveRam = !ramBars.isEmpty();
        if (!haveTps && !haveRam) return;

        var connectionManager = MinecraftServer.getConnectionManager();

        // Audit MED-07: TPS computation was unconditional. Now both sections are gated by the
        // same `if (have*)` pattern — no derived values are computed unless a viewer needs them.
        if (haveTps) {
            // Use Spark's 1-minute MEAN — the previous "1000/lastTickMs" formula amplified
            // single-tick noise (one slow tick made TPS dive to 15 even though average was 20).
            // The mean stays visually stable; spikes show up on /spark or in MsptLogger.
            double mspt = SparkService.getMspt();
            double tps = SparkService.getTps();
            float tpsProgress = (float) Math.max(0, Math.min(1, tps / 20.0));
            BossBar.Color tpsColor =
                    tps >= 18.5 ? BossBar.Color.GREEN :
                            tps >= 15.0 ? BossBar.Color.YELLOW :
                                    BossBar.Color.RED;
            String tpsColorTag =
                    tps >= 18.5 ? "&a" :
                            tps >= 15.0 ? "&e" :
                                    "&c";

            for (Map.Entry<UUID, BossBar> entry : tpsBars.entrySet()) {
                Player p = connectionManager.getOnlinePlayerByUuid(entry.getKey());
                if (p == null) continue;
                int ping = p.getLatency();
                String pingTag = ping < 80 ? "&a" : ping < 200 ? "&e" : "&c";
                String label = String.format(
                        Locale.US,
                        "&#FFE259ᴛᴘs: %s%.1f &7| &#FFE259ᴍsᴘᴛ: %s%.2f &7| &#FFE259ᴘɪɴɢ: %s%dᴍs",
                        tpsColorTag, tps,
                        tpsColorTag, mspt,
                        pingTag, ping);
                if (label.equals(lastTpsLabel.get(entry.getKey()))) continue;
                lastTpsLabel.put(entry.getKey(), label);
                BossBar bar = entry.getValue();
                bar.name(Text.raw(label));
                bar.progress(tpsProgress);
                bar.color(tpsColor);
            }
        }

        if (haveRam) {
            Runtime rt = Runtime.getRuntime();
            long usedB = rt.totalMemory() - rt.freeMemory();
            long maxB = rt.maxMemory();
            long usedMB = usedB / (1024L * 1024L);
            long maxMB = maxB / (1024L * 1024L);
            double ramRatio = (double) usedB / Math.max(maxB, 1);
            float ramProgress = (float) Math.max(0, Math.min(1, ramRatio));
            BossBar.Color ramColor =
                    ramRatio < 0.7 ? BossBar.Color.GREEN :
                            ramRatio < 0.9 ? BossBar.Color.YELLOW :
                                    BossBar.Color.RED;
            String ramColorTag =
                    ramRatio < 0.7 ? "&a" :
                            ramRatio < 0.9 ? "&e" :
                                    "&c";

            String ramLabel = String.format(
                    Locale.US,
                    "&#FFE259ʀᴀᴍ: %s%d ᴍʙ &7/ &f%d ᴍʙ &7(&f%.1f%%&7)",
                    ramColorTag, usedMB, maxMB, ramRatio * 100.0);
            for (Map.Entry<UUID, BossBar> entry : ramBars.entrySet()) {
                if (ramLabel.equals(lastRamLabel.get(entry.getKey()))) continue;
                if (connectionManager.getOnlinePlayerByUuid(entry.getKey()) == null) continue;
                lastRamLabel.put(entry.getKey(), ramLabel);
                BossBar bar = entry.getValue();
                bar.name(Text.raw(ramLabel));
                bar.progress(ramProgress);
                bar.color(ramColor);
            }
        }
    }
}
