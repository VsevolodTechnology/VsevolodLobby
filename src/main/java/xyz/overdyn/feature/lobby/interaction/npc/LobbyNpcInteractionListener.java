package xyz.overdyn.feature.lobby.interaction.npc;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import xyz.overdyn.feature.lobby.bootstrap.LobbyEventRegistration;

import java.util.List;

public final class LobbyNpcInteractionListener implements LobbyEventRegistration {

    private final List<LobbyNpcActionBinding> bindings;

    public LobbyNpcInteractionListener(List<LobbyNpcActionBinding> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerEntityInteractEvent.class, event -> trigger(event.getPlayer(), event.getTarget()));
        handler.addListener(EntityAttackEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                trigger(player, event.getTarget());
            }
        });
    }

    private void trigger(Player player, Entity target) {
        for (LobbyNpcActionBinding binding : bindings) {
            if (!binding.npc().equals(target)) {
                continue;
            }

            binding.action().accept(player);
            return;
        }
    }
}
