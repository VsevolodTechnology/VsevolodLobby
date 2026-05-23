package ua.vsevolod.lobby.feature.lobby.ui.hologram;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.render.DisplayType;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.render.HologramRenderer;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.render.HologramRendererProvider;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single hologram line. Holds the logical state (position, text, style) and routes packet
 * pushes through a per-viewer {@link HologramRenderer}, picked from
 * {@link HologramRendererProvider} when the player first sees the line.
 *
 * <p>State is owned here, not in the renderers — they are stateless functions. The viewer
 * map remembers which {@link DisplayType} a player was given so that update / teleport
 * packets are dispatched through the same backend they spawned with (mixing types would
 * leave a ghost entity on the client).</p>
 */
public final class TextHologramEntry {

    /**
     * Players further than this from the hologram skip metadata updates.
     * 64 blocks ≈ Minecraft's default entity render distance — anything beyond is invisible to
     * the client anyway, so spending packet bandwidth there is wasted.
     * Squared because we compare against distanceSquared() to avoid sqrt() in the hot path.
     */
    private static final double UPDATE_RANGE_SQ = 64.0 * 64.0;

    private final int entityId;
    private final UUID uuid;

    /** Per-viewer choice of display backend, captured at spawn time. */
    private final Map<UUID, DisplayType> viewerTypes = new ConcurrentHashMap<>();

    private Pos position;
    public Component text;
    private TextHologramStyle style;

    TextHologramEntry(Pos position, Component text, TextHologramStyle style) {
        this.entityId = Entity.generateId();
        this.uuid = UUID.randomUUID();
        this.position = position;
        this.text = text;
        this.style = style.copy();
    }

    void show(Player player) {
        HologramRenderer renderer = HologramRendererProvider.forPlayer(player);
        viewerTypes.put(player.getUuid(), renderer.type());
        renderer.spawn(player, entityId, uuid, position, text, style);
    }

    void hide(Player player) {
        DisplayType type = viewerTypes.remove(player.getUuid());
        if (type == null) type = HologramRendererProvider.displayTypeFor(player);
        HologramRendererProvider.rendererFor(type).despawn(player, entityId);
    }

    void showAll(Collection<Player> players) {
        players.forEach(this::show);
    }

    void hideAll(Collection<Player> players) {
        players.forEach(this::hide);
    }

    void teleportAll(Collection<Player> players, Pos newPosition) {
        this.position = newPosition;
        for (Player player : players) {
            DisplayType type = viewerTypes.get(player.getUuid());
            if (type == null) continue; // never spawned for this viewer
            HologramRendererProvider.rendererFor(type).teleport(player, entityId, newPosition);
        }
    }

    void updateTextAll(Collection<Player> players, Component newText) {
        this.text = newText;
        sendMetadataPerViewer(players);
    }

    void updateStyleAll(Collection<Player> players, TextHologramStyle newStyle) {
        this.style = newStyle.copy();
        sendMetadataPerViewer(players);
    }

    private void sendMetadataPerViewer(Collection<Player> players) {
        // Lazily build one packet per display type — viewers of the same type share it.
        var packetCache = new java.util.EnumMap<DisplayType, net.minestom.server.network.packet.server.play.EntityMetaDataPacket>(DisplayType.class);
        for (Player player : players) {
            // Cull updates for players too far away — their client can't render us anyway.
            if (player.getPosition().distanceSquared(position) > UPDATE_RANGE_SQ) continue;

            DisplayType type = viewerTypes.get(player.getUuid());
            if (type == null) continue;
            var packet = packetCache.computeIfAbsent(type,
                    t -> HologramRendererProvider.rendererFor(t).buildMetadata(entityId, uuid, text, style));
            player.sendPacket(packet);
        }
    }
}
