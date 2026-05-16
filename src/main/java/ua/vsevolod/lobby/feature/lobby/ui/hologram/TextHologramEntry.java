package ua.vsevolod.lobby.feature.lobby.ui.hologram;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import ua.vsevolod.lobby.feature.lobby.player.compat.ClientCompat;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public final class TextHologramEntry {

    /** Vertical offset that aligns an armor-stand nametag with the text_display's visual origin. */
    private static final double ARMOR_STAND_NAMETAG_OFFSET = 1.7;

    private final int entityId;
    private final UUID uuid;

    /** Viewers whose client does not understand text_display and was given an armor_stand instead. */
    private final Set<UUID> armorStandViewers = ConcurrentHashMap.newKeySet();

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
        if (ClientCompat.supportsTextDisplay(player)) {
            armorStandViewers.remove(player.getUuid());
            sendTextDisplaySpawn(player);
        } else {
            armorStandViewers.add(player.getUuid());
            sendArmorStandSpawn(player);
        }
    }

    void hide(Player player) {
        armorStandViewers.remove(player.getUuid());
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
        EntityTeleportPacket textDisplayTp = new EntityTeleportPacket(entityId, newPosition, Vec.ZERO, 0, false);
        EntityTeleportPacket armorStandTp = new EntityTeleportPacket(entityId, armorStandPos(newPosition), Vec.ZERO, 0, false);
        for (Player player : players) {
            player.sendPacket(armorStandViewers.contains(player.getUuid()) ? armorStandTp : textDisplayTp);
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
        EntityMetaDataPacket textDisplayPacket = null;
        EntityMetaDataPacket armorStandPacket = null;
        for (Player player : players) {
            if (armorStandViewers.contains(player.getUuid())) {
                if (armorStandPacket == null) armorStandPacket = createArmorStandMetadata();
                player.sendPacket(armorStandPacket);
            } else {
                if (textDisplayPacket == null) textDisplayPacket = createTextDisplayMetadata();
                player.sendPacket(textDisplayPacket);
            }
        }
    }

    private void sendTextDisplaySpawn(Player player) {
        player.sendPacket(new SpawnEntityPacket(
                entityId, uuid, EntityType.TEXT_DISPLAY, position, 0f, 0, Vec.ZERO
        ));
        player.sendPacket(createTextDisplayMetadata());
    }

    private void sendArmorStandSpawn(Player player) {
        player.sendPacket(new SpawnEntityPacket(
                entityId, uuid, EntityType.ARMOR_STAND, armorStandPos(position), 0f, 0, Vec.ZERO
        ));
        player.sendPacket(createArmorStandMetadata());
    }

    private static Pos armorStandPos(Pos textDisplayPos) {
        return textDisplayPos.sub(0, ARMOR_STAND_NAMETAG_OFFSET, 0);
    }

    private EntityMetaDataPacket createTextDisplayMetadata() {
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

    private EntityMetaDataPacket createArmorStandMetadata() {
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
