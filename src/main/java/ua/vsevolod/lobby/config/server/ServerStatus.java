package ua.vsevolod.lobby.config.server;

public enum ServerStatus {
    ONLINE("&#EA1B40●"),
    OFFLINE("&#FF5555✘"),
    SOON("&#FFCC33⌛");

    private final String icon;

    ServerStatus(String icon) {
        this.icon = icon;
    }

    public String icon() {
        return icon;
    }
}
