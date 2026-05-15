package ua.vsevolod.lobby.feature.admin;

import net.kyori.adventure.bossbar.BossBar;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsBarService {

    private static final StatsBarService INSTANCE = new StatsBarService();

    public static StatsBarService get() {
        return INSTANCE;
    }

    private final Map<Player, BossBar> tpsBars = new ConcurrentHashMap<>();
    private final Map<Player, BossBar> ramBars = new ConcurrentHashMap<>();

    private volatile double lastTickMs = 50.0;
    private boolean registered = false;

    private StatsBarService() {
    }

    public synchronized void register(GlobalEventHandler events) {
        if (registered) return;
        registered = true;

        events.addListener(ServerTickMonitorEvent.class, event ->
                lastTickMs = event.getTickMonitor().getTickTime()
        );

        events.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            BossBar t = tpsBars.remove(player);
            if (t != null) player.hideBossBar(t);
            BossBar r = ramBars.remove(player);
            if (r != null) player.hideBossBar(r);
        });

        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(Duration.ofMillis(500))
                .schedule();
    }

    public boolean toggleTps(Player player) {
        BossBar existing = tpsBars.remove(player);
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
        tpsBars.put(player, bar);
        player.showBossBar(bar);
        return true;
    }

    public boolean toggleRam(Player player) {
        BossBar existing = ramBars.remove(player);
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
        ramBars.put(player, bar);
        player.showBossBar(bar);
        return true;
    }

    private void tick() {
        if (tpsBars.isEmpty() && ramBars.isEmpty()) return;

        double mspt = lastTickMs;
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 0.0001));
        float tpsProgress = (float) Math.max(0, Math.min(1, tps / 20.0));
        BossBar.Color tpsColor =
                tps >= 18.5 ? BossBar.Color.GREEN :
                        tps >= 15.0 ? BossBar.Color.YELLOW :
                                BossBar.Color.RED;
        String tpsColorTag =
                tps >= 18.5 ? "&a" :
                        tps >= 15.0 ? "&e" :
                                "&c";

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

        for (Map.Entry<Player, BossBar> entry : tpsBars.entrySet()) {
            Player p = entry.getKey();
            int ping = p.getLatency();
            String pingTag = ping < 80 ? "&a" : ping < 200 ? "&e" : "&c";
            BossBar bar = entry.getValue();
            bar.name(Text.raw(String.format(
                    Locale.US,
                    "&#FFE259ᴛᴘs: %s%.1f &7| &#FFE259ᴍsᴘᴛ: %s%.2f &7| &#FFE259ᴘɪɴɢ: %s%dᴍs",
                    tpsColorTag, tps,
                    tpsColorTag, mspt,
                    pingTag, ping
            )));
            bar.progress(tpsProgress);
            bar.color(tpsColor);
        }

        if (!ramBars.isEmpty()) {
            for (Map.Entry<Player, BossBar> entry : ramBars.entrySet()) {
                BossBar bar = entry.getValue();
                bar.name(Text.raw(String.format(
                        Locale.US,
                        "&#FFE259ʀᴀᴍ: %s%d ᴍʙ &7/ &f%d ᴍʙ &7(&f%.1f%%&7)",
                        ramColorTag, usedMB, maxMB, ramRatio * 100.0
                )));
                bar.progress(ramProgress);
                bar.color(ramColor);
            }
        }
    }
}
