package ua.vsevolod.lobby.feature.lobby.ui.hologram.render;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologramStyle;

import java.util.UUID;

/**
 * Packet-level {@code ARMOR_STAND} hologram renderer for legacy clients.
 *
 * <p>The armor stand sits {@value #NAMETAG_OFFSET} blocks below the logical hologram origin
 * so that the nametag (rendered slightly above the head) visually lines up with where the
 * modern text-display would render. Without this offset, the legacy line floats noticeably
 * higher than the same line on modern clients.</p>
 *
 * <p>Most of the style fields don't translate: armor-stand nametags have no width, no
 * background colour, no billboard mode. We honour the text content (the most important
 * thing) and silently ignore everything else — that's the cost of supporting old protocols.</p>
 */
public final class ArmorStandRenderer implements HologramRenderer {

    public static final ArmorStandRenderer INSTANCE = new ArmorStandRenderer();

    /** Vertical offset that aligns the armor-stand nametag with the text_display's origin. */
    public static final double NAMETAG_OFFSET = 1.7;

    private ArmorStandRenderer() {}

    @Override
    public DisplayType type() {
        return DisplayType.ARMOR_STAND;
    }

    @Override
    public void spawn(Player player, int entityId, UUID uuid, Pos position,
                      Component text, TextHologramStyle style) {
        player.sendPacket(new SpawnEntityPacket(
                entityId, uuid, EntityType.ARMOR_STAND, lower(position), 0f, 0, Vec.ZERO));
        player.sendPacket(buildMetadata(entityId, uuid, text, style));
    }

    @Override
    public void despawn(Player player, int entityId) {
        player.sendPacket(new DestroyEntitiesPacket(entityId));
    }

    @Override
    public void teleport(Player player, int entityId, Pos newPosition) {
        player.sendPacket(new EntityTeleportPacket(entityId, lower(newPosition), Vec.ZERO, 0, false));
    }

    @Override
    public EntityMetaDataPacket buildMetadata(int entityId, UUID uuid,
                                              Component text, TextHologramStyle style) {
        Entity fake = new Entity(EntityType.ARMOR_STAND, uuid);
        fake.setNoGravity(true);
        fake.setInvisible(true);
        fake.setCustomName(text);
        fake.setCustomNameVisible(true);
        fake.editEntityMeta(ArmorStandMeta.class, meta -> {
            meta.setMarker(true);
            meta.setSmall(true);
        });
        return new EntityMetaDataPacket(entityId, fake.getMetadataPacket().entries());
    }

    private static Pos lower(Pos logical) {
        return logical.sub(0, NAMETAG_OFFSET, 0);
    }
}
