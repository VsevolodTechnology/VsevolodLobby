package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcDefinition;

import java.util.Optional;

public final class LobbyNpcInteractionListener implements LobbyEventRegistration {

    private final NpcManager npcManager;
    private final NpcActionExecutor executor;

    public LobbyNpcInteractionListener(NpcManager npcManager, NpcActionExecutor executor) {
        this.npcManager = npcManager;
        this.executor = executor;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        // Right-click on NPC
        handler.addListener(PlayerEntityInteractEvent.class, event ->
                fire(event.getPlayer(), event.getTarget(), Click.RIGHT));
        // Left-click (attack) on NPC
        handler.addListener(EntityAttackEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                fire(player, event.getTarget(), Click.LEFT);
            }
        });
    }

    private void fire(Player player, Entity target, Click click) {
        Optional<String> id = npcManager.findIdByEntity(target);
        if (id.isEmpty()) return;
        Optional<NpcDefinition> def = npcManager.findById(id.get());
        if (def.isEmpty()) return;

        NpcAction action = switch (click) {
            case RIGHT -> def.get().rightAction();
            case LEFT  -> def.get().leftAction();
        };
        executor.execute(player, action);
    }

    private enum Click { RIGHT, LEFT }
}
