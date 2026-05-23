package ua.vsevolod.lobby.feature.lobby.ui.hologram.render;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologramStyle;

import java.util.UUID;

/**
 * One way of putting a single hologram line on a player's screen.
 *
 * <p>Each implementation owns the protocol-specific entity type, spawn packet shape,
 * coordinate offset, and metadata layout. The orchestrator (a {@code TextHologramEntry})
 * holds the line's logical state — position, text, style — and asks the renderer to push
 * that state to one viewer at a time. The renderer never owns state itself: it is a pure
 * stateless function from {@code (entityId, uuid, pos, text, style)} to packets.</p>
 *
 * <p>To add a new backend (say, {@code BLOCK_DISPLAY} or {@code BOSS_BAR}), implement this
 * interface and register the new {@link DisplayType} in
 * {@link HologramRendererProvider#rendererFor(DisplayType)}.</p>
 */
public interface HologramRenderer {

    DisplayType type();

    /** Send the spawn packet + initial metadata. Must include the metadata snapshot so the
     *  entity doesn't pop in blank for one frame. */
    void spawn(Player player, int entityId, UUID uuid, Pos position,
               Component text, TextHologramStyle style);

    /** Send a destroy packet so the line disappears for {@code player}. */
    void despawn(Player player, int entityId);

    /** Teleport the entity (Minestom-style coordinates — implementations adjust per-type
     *  offsets internally, e.g. armor-stand nametag drop). */
    void teleport(Player player, int entityId, Pos newPosition);

    /** Build a fresh metadata packet for this entity from the current logical state. */
    EntityMetaDataPacket buildMetadata(int entityId, UUID uuid,
                                       Component text, TextHologramStyle style);
}
