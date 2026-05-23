package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.entity.metadata.EntityMeta;
import net.minestom.server.entity.metadata.avatar.MannequinMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.ResolvableProfile;

/**
 * A lobby NPC entity. Normally a player-like {@code MANNEQUIN} (with optional skin); when
 * spawned with a mob {@link EntityType} it becomes a static, scaled decorative creature.
 */
public final class LobbyNpc extends EntityCreature {

    public LobbyNpc(
            Instance instance,
            Pos position,
            Component displayName,
            Component description,
            boolean glowing,
            PlayerSkin skin,
            EntityType type,
            double scale
    ) {
        super(type);

        if (getEntityType() == EntityType.MANNEQUIN) {
            boolean nameDisabled = displayName != null;
            editEntityMeta(MannequinMeta.class, meta -> {
                meta.setNotifyAboutChanges(false);
                if (nameDisabled) {
                    meta.setCustomNameVisible(true);
                    set(DataComponents.CUSTOM_NAME, displayName);
                    meta.setDescription(description);
                }
                meta.setHasGlowingEffect(glowing);
                if (skin != null) {
                    meta.setProfile(new ResolvableProfile(skin));
                }
                meta.setNotifyAboutChanges(true);
            });
        } else {
            // Decorative mob — no skin/description, just an optional floating name.
            if (displayName != null) {
                set(DataComponents.CUSTOM_NAME, displayName);
                editEntityMeta(EntityMeta.class, meta -> meta.setCustomNameVisible(true));
            }
            if (glowing) {
                setGlowing(true);
            }
        }

        if (scale > 0 && scale != 1.0) {
            Attribute scaleAttribute = Attribute.fromKey("minecraft:scale");
            if (scaleAttribute != null) {
                AttributeInstance instanceAttr = getAttribute(scaleAttribute);
                if (instanceAttr != null) instanceAttr.setBaseValue(scale);
            }
        }

        setNoGravity(true);
        setHasPhysics(false);
        setAutoViewable(false);
        setSilent(true);
        setInvulnerable(true); // NPCs never take damage from attack-clicks
        setInstance(instance, position);
    }

    /** Convenience constructor for a plain mannequin NPC. */
    public LobbyNpc(Instance instance, Pos position, Component displayName, Component description) {
        this(instance, position, displayName, description, false, null, EntityType.MANNEQUIN, 1.0);
    }

    /**
     * Replaces the skin of a live mannequin NPC without respawning it. No-op for mob NPCs.
     */
    public void updateSkin(PlayerSkin skin) {
        if (skin == null || getEntityType() != EntityType.MANNEQUIN) return;
        editEntityMeta(MannequinMeta.class, meta -> meta.setProfile(new ResolvableProfile(skin)));
    }
}
