package xyz.overdyn.feature.lobby.ui.hologram;

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

import java.util.Collection;
import java.util.UUID;

public final class TextHologramEntry {

    private final int entityId;
    private final UUID uuid;

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
        player.sendPacket(new SpawnEntityPacket(
                entityId,
                uuid,
                EntityType.TEXT_DISPLAY,
                position,
                0f,
                0,
                Vec.ZERO
        ));
        player.sendPacket(createMetadataPacket());
    }

    void hide(Player player) {
        player.sendPacket(new DestroyEntitiesPacket(entityId));
    }

    void showAll(Collection<Player> players) {
        players.forEach(this::show);
    }

    void hideAll(Collection<Player> players) {
        players.forEach(this::hide);
    }

    void teleportAll(Collection<Player> players, Pos newPosition) {
        this.position = newPosition;
        EntityTeleportPacket packet = new EntityTeleportPacket(entityId, newPosition, Vec.ZERO, 0, false);
        players.forEach(player -> player.sendPacket(packet));
    }

    void updateTextAll(Collection<Player> players, Component newText) {
        this.text = newText;
        EntityMetaDataPacket packet = createMetadataPacket();
        players.forEach(player -> player.sendPacket(packet));
    }

    void updateStyleAll(Collection<Player> players, TextHologramStyle newStyle) {
        this.style = newStyle.copy();
        EntityMetaDataPacket packet = createMetadataPacket();
        players.forEach(player -> player.sendPacket(packet));
    }

    private EntityMetaDataPacket createMetadataPacket() {
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

    public Entity createEntityPacketT() {
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

        return fake;
    }
}

