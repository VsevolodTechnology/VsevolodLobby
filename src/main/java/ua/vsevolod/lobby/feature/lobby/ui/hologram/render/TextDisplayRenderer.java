package ua.vsevolod.lobby.feature.lobby.ui.hologram.render;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.TextHologramStyle;

import java.util.UUID;

/** Renders a hologram line as a {@code TEXT_DISPLAY} entity (1.19.4+ clients). */
public final class TextDisplayRenderer implements HologramRenderer {

    public static final TextDisplayRenderer INSTANCE = new TextDisplayRenderer();

    private TextDisplayRenderer() {}

    @Override
    public DisplayType type() {
        return DisplayType.TEXT_DISPLAY;
    }

    @Override
    public void spawn(Player player, int entityId, UUID uuid, Pos position,
                      Component text, TextHologramStyle style) {
        player.sendPacket(new SpawnEntityPacket(
                entityId, uuid, EntityType.TEXT_DISPLAY, position, 0f, 0, Vec.ZERO));
        player.sendPacket(buildMetadata(entityId, uuid, text, style));
    }

    @Override
    public void despawn(Player player, int entityId) {
        player.sendPacket(new DestroyEntitiesPacket(entityId));
    }

    @Override
    public void teleport(Player player, int entityId, Pos newPosition) {
        player.sendPacket(new EntityTeleportPacket(entityId, newPosition, Vec.ZERO, 0, false));
    }

    @Override
    public EntityMetaDataPacket buildMetadata(int entityId, UUID uuid,
                                              Component text, TextHologramStyle style) {
        Entity fake = new Entity(EntityType.TEXT_DISPLAY, uuid);
        fake.editEntityMeta(TextDisplayMeta.class, meta -> {
            meta.setText(text);
            meta.setLineWidth(style.lineWidth());
            meta.setBackgroundColor(style.backgroundColor());
            meta.setTextOpacity(style.textOpacity());
            meta.setSeeThrough(style.seeThrough());
            meta.setUseDefaultBackground(style.useDefaultBackground());
            meta.setShadow(style.shadow());
            meta.setAlignment(style.alignment());
            meta.setBillboardRenderConstraints(style.billboard());
            meta.setScale(style.scale());
            meta.setTranslation(style.translation());
            meta.setTransformationInterpolationStartDelta(style.interpolationDelay());
            meta.setTransformationInterpolationDuration(style.transformationInterpolationDuration());
            meta.setPosRotInterpolationDuration(style.posRotInterpolationDuration());

            if (style.brightnessOverride() >= 0) {
                meta.setBrightnessOverride(style.brightnessOverride());
            }

            meta.setViewRange(style.viewRange());
            meta.setShadowRadius(style.shadowRadius());
            meta.setShadowStrength(style.shadowStrength());
            meta.setWidth(style.width());
            meta.setHeight(style.height());

            if (style.glowColorOverride() >= 0) {
                meta.setGlowColorOverride(style.glowColorOverride());
            }
        });
        return new EntityMetaDataPacket(entityId, fake.getMetadataPacket().entries());
    }
}
