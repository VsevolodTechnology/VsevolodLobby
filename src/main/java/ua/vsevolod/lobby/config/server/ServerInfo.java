package ua.vsevolod.lobby.config.server;

import net.minestom.server.item.Material;
import ua.vsevolod.lobby.bootstrap.server.ProxyOnlineService;

/**
 * Runtime view of one server. Built from a {@link ServerEntry} (config) via {@link #from}.
 *
 * <p>{@link #status} is the manual config flag; {@link #effectiveStatus()} combines it with
 * the live proxy poll to give the state actually shown to players.</p>
 */
public record ServerInfo(
        String id,
        String worldName,
        String versionCore,
        ServerStatus status,
        int maxOnline,
        String[] tagsServer,
        Material material
) {

    /** The state shown to players — manual flag merged with live polling. */
    public enum EffectiveStatus {
        ONLINE,
        OFFLINE,
        LOADING,
        SOON
    }

    public ServerInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        ProxyOnlineService service = ProxyOnlineService.get();
        if (service != null) {
            service.addServer(id);
        }
    }

    /** Build a runtime view from a config entry; resolves the material key. */
    public static ServerInfo from(String id, ServerEntry entry) {
        Material material = Material.fromKey(entry.material());
        if (material == null) material = Material.DIAMOND_SWORD;
        return new ServerInfo(
                id,
                entry.displayName(),
                entry.versionCore(),
                entry.status(),
                entry.maxOnline(),
                entry.tags().toArray(new String[0]),
                material
        );
    }

    /** Live player count from the proxy poll (0 when unknown). */
    public int online() {
        return ProxyOnlineService.getOnline(id);
    }

    /** Manual config flag merged with the live proxy poll. */
    public EffectiveStatus effectiveStatus() {
        return switch (status) {
            case SOON -> EffectiveStatus.SOON;
            case OFFLINE -> EffectiveStatus.OFFLINE;
            case ONLINE -> switch (ProxyOnlineService.getStatusStatic(id).state()) {
                case ONLINE -> EffectiveStatus.ONLINE;
                case OFFLINE -> EffectiveStatus.OFFLINE;
                case LOADING -> EffectiveStatus.LOADING;
            };
        };
    }

    /** Configurable status label (MiniMessage) for the current {@link #effectiveStatus()}. */
    public String getStatusName() {
        ServersConfig cfg = ServersConfig.get();
        return switch (effectiveStatus()) {
            case ONLINE -> cfg.statusOnline;
            case OFFLINE -> cfg.statusOffline;
            case LOADING -> cfg.statusLoading;
            case SOON -> cfg.statusSoon;
        };
    }

    public boolean isJoinable() {
        return effectiveStatus() == EffectiveStatus.ONLINE;
    }
}
