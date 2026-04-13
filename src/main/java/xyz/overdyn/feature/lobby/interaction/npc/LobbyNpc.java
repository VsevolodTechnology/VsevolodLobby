package xyz.overdyn.feature.lobby.interaction.npc;

import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.entity.metadata.avatar.MannequinMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.ResolvableProfile;

public final class LobbyNpc extends EntityCreature {

    public LobbyNpc(
            Instance instance,
            Pos position,
            Component displayName,
            Component description,
            boolean glowing,
            String skinUsername
    ) {
        super(EntityType.MANNEQUIN);

        boolean nameDisabled = displayName != null ? true : false;

        editEntityMeta(MannequinMeta.class, meta -> {
            meta.setNotifyAboutChanges(false);
            if (nameDisabled) {
                meta.setCustomNameVisible(true);
                set(DataComponents.CUSTOM_NAME, displayName);
                meta.setDescription(description);
            }
            meta.setHasGlowingEffect(glowing);

            ResolvableProfile profile = resolveProfile(skinUsername);
            if (profile != null) {
                meta.setProfile(profile);
            }

            meta.setNotifyAboutChanges(true);
        });

        setNoGravity(true);
        setHasPhysics(false);
        setAutoViewable(false);
        setSilent(true);
        setInstance(instance, position);
    }

    public LobbyNpc(Instance instance, Pos position, Component displayName, Component description) {
        this(instance, position, displayName, description, false, null);
    }

    private ResolvableProfile resolveProfile(String skinUsername) {
        if (skinUsername == null || skinUsername.isBlank()) {
            return null;
        }

        try {
            PlayerSkin skin = PlayerSkin.fromUsername(skinUsername);
            return skin != null ? new ResolvableProfile(skin) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
