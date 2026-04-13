package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.minestom.server.entity.Player;

import java.util.List;

public final class LobbyNpcService {

    private final List<LobbyNpc> npcs;

    public LobbyNpcService(List<LobbyNpc> npcs) {
        this.npcs = List.copyOf(npcs);
    }

    public void showTo(Player player) {
        npcs.forEach(npc -> npc.addViewer(player));
    }

    public void hideFrom(Player player) {
        npcs.forEach(npc -> npc.removeViewer(player));
    }
}
