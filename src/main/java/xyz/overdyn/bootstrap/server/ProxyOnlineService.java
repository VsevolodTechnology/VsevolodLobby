package xyz.overdyn.bootstrap.server;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.timer.TaskSchedule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ProxyOnlineService {

    private static final String CHANNEL = "bungeecord:main";
    private static final long COUNT_TTL_MS = 15_000L;

    private static ProxyOnlineService instance;

    public static ProxyOnlineService get() {
        return instance;
    }

    // ===== DATA =====
    private final Map<String, CountSnapshot> counts = new ConcurrentHashMap<>();
    private final Set<String> registeredServers = ConcurrentHashMap.newKeySet();

    // ===== INIT =====
    public void register() {
        instance = this;

        MinecraftServer.getGlobalEventHandler().addListener(PlayerPluginMessageEvent.class, event -> {
            if (!CHANNEL.equalsIgnoreCase(event.getIdentifier())) {
                return;
            }
            handleIncoming(event);
        });

        startAutoUpdater();
    }

    // ===== STATIC API =====
    public static int getOnline(String server) {
        ProxyOnlineService s = instance;
        if (s == null) return 0;
        return s.getOnlineOrZero(server);
    }

    public static ModeStatus getStatusStatic(String server) {
        ProxyOnlineService s = instance;
        if (s == null) {
            return new ModeStatus(server, ModeStatus.State.LOADING, 0);
        }
        return s.getStatus(server);
    }

    // ===== REGISTER SERVERS =====
    public void addServer(String server) {
        registeredServers.add(normalize(server));
    }

    public void addServers(Collection<String> servers) {
        servers.forEach(this::addServer);
    }

    // ===== AUTO UPDATE =====
    private void startAutoUpdater() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            Player player = getAnyPlayer();
            if (player == null) return;

            for (String server : registeredServers) {
                requestPlayerCount(player, server);
            }

        }).repeat(TaskSchedule.seconds(5)).schedule();
    }

    // ===== REQUEST =====
    private void requestPlayerCount(Player player, String serverName) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);

            out.writeUTF("PlayerCount");
            out.writeUTF(serverName);

            player.sendPluginMessage(CHANNEL, bytes.toByteArray());
        } catch (Exception ignored) {
        }
    }

    // ===== STATUS =====
    public ModeStatus getStatus(String serverName) {
        String key = normalize(serverName);
        CountSnapshot snapshot = counts.get(key);

        if (snapshot == null) {
            return new ModeStatus(key, ModeStatus.State.LOADING, 0);
        }

        long age = System.currentTimeMillis() - snapshot.updatedAt();
        if (age > COUNT_TTL_MS) {
            return new ModeStatus(key, ModeStatus.State.LOADING, 0);
        }

        return new ModeStatus(key, ModeStatus.State.ONLINE, snapshot.online());
    }

    public int getOnlineOrZero(String serverName) {
        ModeStatus status = getStatus(serverName);
        return status.state() == ModeStatus.State.ONLINE ? status.online() : 0;
    }

    // ===== INCOMING =====
    private void handleIncoming(PlayerPluginMessageEvent event) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getMessage()));
            String subChannel = in.readUTF();

            if (!"PlayerCount".equals(subChannel)) {
                return;
            }

            String serverName = normalize(in.readUTF());
            int online = in.readInt();

            counts.put(serverName, new CountSnapshot(online, System.currentTimeMillis()));

        } catch (Exception ignored) {
        }
    }

    // ===== UTILS =====
    private Player getAnyPlayer() {
        return MinecraftServer.getConnectionManager()
                .getOnlinePlayers()
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String normalize(String serverName) {
        return serverName.toLowerCase();
    }

    // ===== RECORDS =====
    public record ModeStatus(
            String serverName,
            State state,
            int online
    ) {
        public enum State {
            ONLINE,
            OFFLINE,
            LOADING
        }

        public String statusText() {
            return switch (state) {
                case ONLINE -> "§aРаботает";
                case OFFLINE -> "§cВыключен";
                case LOADING -> "§6Загрузка...";
            };
        }

        public String onlineText() {
            return switch (state) {
                case ONLINE -> "§e" + online;
                case OFFLINE -> "§7—";
                case LOADING -> "§7...";
            };
        }
    }

    private record CountSnapshot(int online, long updatedAt) {
    }
}