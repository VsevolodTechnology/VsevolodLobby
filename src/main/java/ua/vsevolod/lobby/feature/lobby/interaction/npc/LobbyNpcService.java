package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.minestom.server.entity.Player;

/**
 * Thin pass-through to {@link NpcManager} kept for backwards compatibility with the existing
 * {@code LobbyJoinInitializer} signature. The actual list of NPCs is owned by the manager and
 * changes on /reload — this wrapper does not cache anything.
 */
public final class LobbyNpcService {

    private final NpcManager manager;

    public LobbyNpcService(NpcManager manager) {
        this.manager = manager;
    }

    public void showTo(Player player) {
        manager.showTo(player);
    }

    public void hideFrom(Player player) {
        manager.hideFrom(player);
    }
}
