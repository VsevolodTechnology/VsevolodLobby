package xyz.overdyn.config.server;

import net.minestom.server.item.Material;
import xyz.overdyn.bootstrap.server.ProxyOnlineService;

public record ServerInfo(
        String id,
        String worldName,
        String versionCore,
        ServerStatus status,
        int maxOnline,
        String[] tagsServer,
        Material material
) {

    public ServerInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }

        ProxyOnlineService service = ProxyOnlineService.get();
        if (service != null) {
            service.addServer(id);
        }
    }

    public int online() {
        return ProxyOnlineService.getOnline(id);
    }

    public ProxyOnlineService.ModeStatus getStatusStatic() {
        return ProxyOnlineService.getStatusStatic(id);
    }

    public String getStatusName() {
        return switch (getStatusStatic().state()) {
            case ONLINE -> "&#F0C58AАктивен";
            case OFFLINE -> "&#F0C58AОффлайн";
            case LOADING -> "&#F0C58AЗагрузка";
        };
    }

    public boolean isJoinable() {
        return status == ServerStatus.ONLINE;
    }
}
