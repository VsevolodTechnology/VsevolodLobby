package ua.vsevolod.lobby.bootstrap.module;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.server.ServerListPingEvent;

import net.minestom.server.ping.Status;
import ua.vsevolod.lobby.bootstrap.server.Module;
import ua.vsevolod.lobby.bootstrap.server.ProxyOnlineService;
import ua.vsevolod.lobby.command.admin.MenuCommand;
import ua.vsevolod.lobby.command.admin.NpcCommand;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.config.server.ServerInfo;
import ua.vsevolod.lobby.config.server.ServerRegistry;
import ua.vsevolod.lobby.config.server.ServersConfig;
import ua.vsevolod.lobby.feature.admin.MsptLogger;
import ua.vsevolod.lobby.feature.admin.StatsBarService;
import ua.vsevolod.lobby.feature.admin.VersionGateListener;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistrar;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcActionExecutor;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcsConfig;
import ua.vsevolod.lobby.feature.lobby.interaction.qr.LobbyQrMapService;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.HologramManager;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramsConfig;
import ua.vsevolod.lobby.feature.lobby.ui.menu.MenuManager;
import ua.vsevolod.lobby.feature.lobby.ui.menu.ServerConnector;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfig;
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
        new ua.vsevolod.lobby.feature.lobby.player.time.PlayerTimeZoneService().register(events);
        StatsBarService.get().register(events);
        new MsptLogger().register(events);
        VersionGateListener.register(events);
        ProxyOnlineService proxyService = new ProxyOnlineService();
        proxyService.register();
        // Feed the proxy poller with every configured server id; refresh on /reload.
        registerServersForPolling(proxyService);
        ServersConfig.addListener(cfg -> registerServersForPolling(proxyService));
        LobbyModeSelectorMenu menu = new LobbyModeSelectorMenu(events);
        LobbySidebar sidebar = new LobbySidebar();

        // NPC subsystem (Phase 2).
        // The action executor knows how to dispatch open-menu / parkour-start with live service refs;
        // the manager owns the entities and reacts to /reload via the config-section listener.
        npcActionExecutor = new NpcActionExecutor();
        menuManager = new MenuManager(npcActionExecutor);
        menuManager.register(events);
        // On /reload, close all open menus so viewers don't see stale items.
        MenusConfig.addListener(cfg -> menuManager.closeAll());

        // [menu] <id> — open config-driven menu, fall back to legacy hardcoded mode-selector
        npcActionExecutor.registerPrefix("menu", (player, id) -> {
            if (!menuManager.openFor(player, id)) {
                if ("mode-selector".equals(id)) {
                    player.openInventory(menu.getMenu());
                }
            }
        });
        // [connect] <server> — status-gated cross-server transfer (see ServerConnector)
        npcActionExecutor.registerPrefix("connect",
                (player, server) -> ServerConnector.connect(player, server));
        // Legacy action-type registry entries (used by join-items)
        npcActionExecutor.register("open-menu", (player, action) -> {
            if (!menuManager.openFor(player, action.target())) {
                if ("mode-selector".equals(action.target())) {
                    player.openInventory(menu.getMenu());
                }
            }
        });
        npcActionExecutor.register("transfer-server",
                (player, action) -> ServerConnector.connect(player, action.target()));
        // [parkour] / parkour-start — wired later inside LobbyEventRegistrar

        npcManager = new NpcManager(InstanceModule.lobby);
        NpcsConfig.addListener(npcManager::onConfigApplied);
        npcManager.onConfigApplied(NpcsConfig.get());
        new NpcCommand(npcManager);
        new MenuCommand(menuManager);

        hologramManager = new HologramManager();
        hologramManager.onConfigApplied(HologramsConfig.get());
        HologramsConfig.addListener(hologramManager::onConfigApplied);

        new LobbyEventRegistrar(events, InstanceModule.lobby, npcManager, npcActionExecutor, sidebar, menu);
    }

    /** Whitelists every configured server id with the proxy poller. */
    private static void registerServersForPolling(ProxyOnlineService proxyService) {
        for (ServerInfo server : ServerRegistry.servers()) {
            proxyService.addServer(server.id());
        }
    }
}
