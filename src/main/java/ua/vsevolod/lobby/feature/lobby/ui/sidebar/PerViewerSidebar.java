package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.utils.PacketSendingUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Collections;

/**
 * A {@link Sidebar} extension that can target a single viewer with a line-content update.
 *
 * <h3>Why this exists</h3>
 * <p>The base {@link Sidebar#updateLineContent(String, Component)} broadcasts to every viewer of
 * the sidebar. The lobby uses one shared {@code Sidebar} for all players and overlays per-viewer
 * text on a single "ping" line — so we need to send a team-prefix-update packet to one viewer
 * <b>without</b> touching the sidebar's canonical state.</p>
 *
 * <h3>Bug it fixes</h3>
 * <p>Minestom's {@code ScoreboardLine.refreshContent(c)} delegates to {@code SidebarTeam.refreshPrefix(c)}
 * which <b>mutates</b> the team's {@code prefix} field. That field is read by {@code addViewer()}
 * when sending {@code TeamsPacket.CreateTeamAction} to a freshly-joined player. The previous
 * implementation called {@code refreshContent} during per-viewer updates, so every new joiner saw
 * the prefix from the last per-viewer update — i.e. all players ended up seeing the last player's
 * ping on the scoreboard.</p>
 *
 * <p>{@code SidebarTeam.updatePrefix(c)} does <b>not</b> mutate — it only constructs a fresh
 * {@code TeamsPacket}. So per-viewer updates are safe as long as we go through {@code updatePrefix}
 * and never through {@code refreshContent}/{@code refreshPrefix}.</p>
 */
public class PerViewerSidebar extends Sidebar {

    private static final MethodHandle updatePrefixHandle;
    private static final Field sidebarTeamField;

    static {
        try {
            Class<?> lineClass = Class.forName("net.minestom.server.scoreboard.Sidebar$ScoreboardLine");
            sidebarTeamField = lineClass.getDeclaredField("sidebarTeam");
            sidebarTeamField.setAccessible(true);

            Class<?> teamClass = Class.forName("net.minestom.server.scoreboard.Sidebar$SidebarTeam");
            var method = teamClass.getDeclaredMethod("updatePrefix", Component.class);
            method.setAccessible(true);
            updatePrefixHandle = MethodHandles.lookup().unreflect(method);
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

    /**
     * Sends a team-prefix-update packet to {@code viewer} for the line with id {@code id},
     * leaving the sidebar's canonical state untouched so other viewers and future joiners
     * still see the baseline prefix established at line creation.
     */
    public void updateLineContent(String id, Component content, Player viewer) {
        final ScoreboardLine line = getLine(id);
        if (line == null) return;

        try {
            Object sidebarTeam = sidebarTeamField.get(line);
            SendablePacket packet = (SendablePacket) updatePrefixHandle.invoke(sidebarTeam, content);
            sendPacketToViewer(packet, viewer);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
