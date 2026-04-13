package xyz.overdyn.feature.lobby.interaction.npc;

import net.minestom.server.entity.Player;

import java.util.function.Consumer;

public record LobbyNpcActionBinding(LobbyNpc npc, Consumer<Player> action) {
}
