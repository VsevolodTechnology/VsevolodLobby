package xyz.overdyn.feature.lobby.ui.sidebar;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.utils.PacketSendingUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

public class PerViewerSidebar extends Sidebar {

    private static Method refreshContentMethod;
    private static Field sidebarTeamField;

    static {
        try {
            Class<?> lineClass = Class.forName("net.minestom.server.scoreboard.Sidebar$ScoreboardLine");

            refreshContentMethod = lineClass.getDeclaredMethod("refreshContent", Component.class);
            refreshContentMethod.setAccessible(true);

            sidebarTeamField = lineClass.getDeclaredField("sidebarTeam");
            sidebarTeamField.setAccessible(true);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PerViewerSidebar(Component title) {
        super(title);
    }

    public void sendPacketToViewer(SendablePacket packet, Player viewer) {
        if (packet instanceof ServerPacket serverPacket) {
            PacketSendingUtils.sendGroupedPacket(Collections.singleton(viewer), serverPacket);
        } else {
            viewer.sendPacket(packet);
        }
    }

    public void updateLineContent(String id, Component content, Player viewer) {

        try {

            final ScoreboardLine line = getLine(id);
            if (line == null) return;

            refreshContentMethod.invoke(line, content);

            Object sidebarTeam = sidebarTeamField.get(line);

            Method updatePrefix = sidebarTeam.getClass()
                    .getDeclaredMethod("updatePrefix", Component.class);

            updatePrefix.setAccessible(true);

            SendablePacket packet = (SendablePacket) updatePrefix.invoke(sidebarTeam, content);

            sendPacketToViewer(packet, viewer);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
