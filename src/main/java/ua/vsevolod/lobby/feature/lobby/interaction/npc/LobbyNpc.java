package ua.vsevolod.lobby.feature.lobby.interaction.npc;

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
            PlayerSkin skin
    ) {
        super(EntityType.MANNEQUIN);

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

        setNoGravity(true);
        setHasPhysics(false);
        setAutoViewable(false);
        setSilent(true);
        setInstance(instance, position);
    }

    public LobbyNpc(Instance instance, Pos position, Component displayName, Component description) {
        this(instance, position, displayName, description, false, (PlayerSkin) null);
    }

    /**
     * Replaces the skin of the live entity without respawning it. Used when an async
     * URL skin resolve completes after the NPC was already shown to players.
     */
    public void updateSkin(PlayerSkin skin) {
        if (skin == null) return;
        editEntityMeta(MannequinMeta.class, meta -> meta.setProfile(new ResolvableProfile(skin)));
    }
}
