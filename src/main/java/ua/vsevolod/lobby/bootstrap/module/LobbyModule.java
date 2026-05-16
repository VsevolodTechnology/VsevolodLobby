package ua.vsevolod.lobby.bootstrap.module;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
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
import ua.vsevolod.lobby.feature.lobby.ui.menu.MenuManager;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfigSection;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologram;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologramBuilder;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologramStyle;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbyModeSelectorMenu;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.ui.tab.LobbyTabListManager;
import ua.vsevolod.lobby.util.Text;

public class LobbyModule implements Module {

    public static TextHologram holo;
    public static NpcManager npcManager;
    public static NpcActionExecutor npcActionExecutor;
    public static MenuManager menuManager;

    @Override
    public void load() {
        var events = MinecraftServer.getGlobalEventHandler();

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

        // `open-menu <id>` action: try the config-driven MenuManager first; fall back to the
        // legacy hardcoded mode-selector for backwards compatibility.
        npcActionExecutor.register("open-menu", (player, action) -> {
            if (!menuManager.openFor(player, action.target())) {
                if ("mode-selector".equals(action.target())) {
                    player.openInventory(menu.getMenu());
                }
            }
        });
        // `transfer-server` stub — Phase 4. Real cross-backend transfer arrives later together
        // with Velocity plugin-channel wiring; for now we just chat the destination at the player.
        npcActionExecutor.register("transfer-server", (player, action) ->
                player.sendMessage("§7Подключение к §f" + action.target() + "§7... (transfer не реализован)"));
        // parkour-start handler is wired later inside LobbyEventRegistrar because it depends on
        // LobbyParkourService which is constructed there.

        npcManager = new NpcManager(InstanceModule.lobby);
        NpcConfigSection.INSTANCE.addListener(npcManager::onConfigApplied);
        npcManager.onConfigApplied(NpcConfigSection.INSTANCE.current());
        new NpcCommand(npcManager);
        new MenuCommand(menuManager);

        // Legacy parkour hologram — still built here because Phase 2 only adapter-fed NPCs to config;
        // hologram-per-NPC coupling is Phase 5 polish per ROADMAP.
        holo = new TextHologramBuilder(LobbyConfig.Parkour.NPC_POS.withY(LobbyConfig.Parkour.NPC_POS.y() + 2.2))
                .line(Text.c("&#F1BB58&lП&#F1B958&lА&#F1B658&lР&#F1B458&lК&#F1B158&lУ&#F1AF58&lР").appendNewline()
                                .append(Component.text("Проверь свою реакцию, точность и контроль", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                                .appendNewline()
                                .append(Component.text("можешь дойти до конца и не упасть?", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)),
                        TextHologramStyle.defaults()
                                .backgroundColor(0x1C1C1E)
                                .useDefaultBackground(true)
                                .billboard(AbstractDisplayMeta.BillboardConstraints.FIXED)
                                .alignment(TextDisplayMeta.Alignment.CENTER)
                                .scale(new Vec(0.8, 0.8, 0.8))
                                .shadow(true)
                                .seeThrough(true)
                )
                .build();

        new LobbyEventRegistrar(events, InstanceModule.lobby, npcManager, npcActionExecutor, sidebar, menu);
    }
}
