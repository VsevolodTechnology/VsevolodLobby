package ua.vsevolod.lobby.bootstrap;

import ua.vsevolod.lobby.bootstrap.server.ServerBootstrap;

public class Main {

    static void main(String[] args) {
        // Tune Minestom ServerFlag defaults BEFORE MinecraftServer.init() — they're read once at boot.
        // Lobby is a tiny static world with many players, so we don't need the vanilla 8-chunk view.
        // Smaller view = less chunk traffic per player = lower MSPT at high online.
        applyDefaultIfAbsent("minestom.chunk-view-distance", "6");
        applyDefaultIfAbsent("minestom.entity-view-distance", "4");
        applyDefaultIfAbsent("minestom.entity-synchronization-ticks", "20");

        ServerBootstrap.bootstrap();
    }

    private static void applyDefaultIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }

}