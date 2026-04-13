package ua.vsevolod.lobby.bootstrap.module;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import ua.vsevolod.lobby.bootstrap.server.Module;
import ua.vsevolod.lobby.bootstrap.server.ProxyOnlineService;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistrar;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.LobbyNpc;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologram;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologramBuilder;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologramStyle;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbyModeSelectorMenu;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.ui.tab.LobbyTabListManager;
import ua.vsevolod.lobby.util.Text;

public class LobbyModule implements Module {

    public static TextHologram holo;

    @Override
    public void load() {
        var events = MinecraftServer.getGlobalEventHandler();

        new LobbyTabListManager(events);
        ProxyOnlineService proxyService = new ProxyOnlineService();
        proxyService.register();
        LobbyModeSelectorMenu menu = new LobbyModeSelectorMenu(events);
        LobbySidebar sidebar = new LobbySidebar();

        LobbyNpc modeSelectorNpc = new LobbyNpc(
                InstanceModule.lobby,
                LobbyConfig.Locations.MODE_SELECTOR_NPC_POS,
                Text.c("&6&lВыбор режима"),
                Text.c("&7Нажмите, чтобы открыть меню")
        );

        LobbyNpc parkourNpc = new LobbyNpc(
                InstanceModule.lobby,
                LobbyConfig.Parkour.NPC_POS,
                null,
                Component.text("Проверь свою реакцию, точность и контроль", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL).appendNewline()
                    .append(Component.text("можешь дойти до конца и не упасть?", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)),
                true,
                LobbyConfig.Parkour.NPC_SKIN_USERNAME
        );

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

        new LobbyEventRegistrar(events, InstanceModule.lobby, modeSelectorNpc, parkourNpc, sidebar, menu);
    }
}
