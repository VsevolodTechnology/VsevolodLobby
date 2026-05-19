package ua.vsevolod.lobby.bootstrap.module;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.server.ServerListPingEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import net.minestom.server.ping.Status;
import ua.vsevolod.lobby.bootstrap.server.Module;
import ua.vsevolod.lobby.bootstrap.server.ProxyOnlineService;
import ua.vsevolod.lobby.command.admin.MenuCommand;
import ua.vsevolod.lobby.command.admin.NpcCommand;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.admin.MsptLogger;
import ua.vsevolod.lobby.feature.admin.StatsBarService;
import ua.vsevolod.lobby.feature.admin.VersionGateListener;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistrar;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcActionExecutor;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcConfigSection;
import ua.vsevolod.lobby.feature.lobby.interaction.qr.LobbyQrMapService;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.HologramManager;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramsConfigSection;
import ua.vsevolod.lobby.feature.lobby.ui.menu.MenuManager;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfigSection;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbyModeSelectorMenu;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.ui.tab.LobbyTabListManager;

public class LobbyModule implements Module {

    public static HologramManager hologramManager;
    public static NpcManager npcManager;
    public static NpcActionExecutor npcActionExecutor;
    public static MenuManager menuManager;

    @Override
    public void load() {
        var events = MinecraftServer.getGlobalEventHandler();

        // Render the QR map texture now, not on the first player join. ZXing + Graphics2D
        // would otherwise run inside the LobbyJoinInitializer chain for the first joiner.
        LobbyQrMapService.preinit();

        // Fix: Minestom default sets maxPlayers = onlinePlayers + 1, so at 42 online the MOTD
        // shows "42/43" — Velocity sees the server as full and stops forwarding new connections.
        // Override to always show the configured MAX_PLAYERS regardless of current online count.
        events.addListener(ServerListPingEvent.class, event -> {
            int online = MinecraftServer.getConnectionManager().getOnlinePlayerCount();
            int max = LobbyConfig.Settings.MAX_PLAYERS;
            Status current = event.getStatus();
            event.setStatus(Status.builder(current)
                    .playerInfo(online, max)
                    .build());
        });

        new LobbyTabListManager(events);
        StatsBarService.get().register(events);
        new MsptLogger().register(events);
        VersionGateListener.register(events);
        ProxyOnlineService proxyService = new ProxyOnlineService();
        proxyService.register();
        LobbyModeSelectorMenu menu = new LobbyModeSelectorMenu(events);
        LobbySidebar sidebar = new LobbySidebar();

        // NPC subsystem (Phase 2).
        // The action executor knows how to dispatch open-menu / parkour-start with live service refs;
        // the manager owns the entities and reacts to /reload via the config-section listener.
        npcActionExecutor = new NpcActionExecutor();
        menuManager = new MenuManager(npcActionExecutor);
        menuManager.register(events);
        // On /reload, close all open menus so viewers don't see stale items.
        MenusConfigSection.INSTANCE.addListener(cfg -> menuManager.closeAll());

        // [menu] <id> — open config-driven menu, fall back to legacy hardcoded mode-selector
        npcActionExecutor.registerPrefix("menu", (player, id) -> {
            if (!menuManager.openFor(player, id)) {
                if ("mode-selector".equals(id)) {
                    player.openInventory(menu.getMenu());
                }
            }
        });
        // [connect] <server> — cross-server transfer via BungeeCord plugin messaging
        npcActionExecutor.registerPrefix("connect", (player, server) -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeUTF("Connect");
                out.writeUTF(server);
                player.sendPluginMessage("bungeecord:main", bytes.toByteArray());
            } catch (java.io.IOException e) {
                System.err.println("[connect] Failed to transfer " + player.getUsername() + " to " + server + ": " + e.getMessage());
            }
        });
        // Legacy action-type registry entries (used by join-items)
        npcActionExecutor.register("open-menu", (player, action) -> {
            if (!menuManager.openFor(player, action.target())) {
                if ("mode-selector".equals(action.target())) {
                    player.openInventory(menu.getMenu());
                }
            }
        });
        npcActionExecutor.register("transfer-server", (player, action) -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeUTF("Connect");
                out.writeUTF(action.target());
                player.sendPluginMessage("bungeecord:main", bytes.toByteArray());
            } catch (java.io.IOException e) {
                System.err.println("[transfer-server] Failed to transfer " + player.getUsername() + ": " + e.getMessage());
            }
        });
        // [parkour] / parkour-start — wired later inside LobbyEventRegistrar

        npcManager = new NpcManager(InstanceModule.lobby);
        NpcConfigSection.INSTANCE.addListener(npcManager::onConfigApplied);
        npcManager.onConfigApplied(NpcConfigSection.INSTANCE.current());
        new NpcCommand(npcManager);
        new MenuCommand(menuManager);

        hologramManager = new HologramManager();
        hologramManager.onConfigApplied(HologramsConfigSection.INSTANCE.current());
        HologramsConfigSection.INSTANCE.addListener(hologramManager::onConfigApplied);

        new LobbyEventRegistrar(events, InstanceModule.lobby, npcManager, npcActionExecutor, sidebar, menu);
    }
}
