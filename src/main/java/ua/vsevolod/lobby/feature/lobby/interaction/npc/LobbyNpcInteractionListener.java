package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcDefinition;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyNpcInteractionListener implements LobbyEventRegistration {

    private static final long COOLDOWN_MS = 400;

    private final NpcManager npcManager;
    private final NpcActionExecutor executor;
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<>();

    public LobbyNpcInteractionListener(NpcManager npcManager, NpcActionExecutor executor) {
        this.npcManager = npcManager;
        this.executor = executor;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerEntityInteractEvent.class, event ->
                fire(event.getPlayer(), event.getTarget(), Click.RIGHT));
        handler.addListener(EntityAttackEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                fire(player, event.getTarget(), Click.LEFT);
            }
        });
    }

    private void fire(Player player, Entity target, Click click) {
        long now = System.currentTimeMillis();
        Long last = lastInteract.put(player.getUuid(), now);
        if (last != null && (now - last) < COOLDOWN_MS) return;

        Optional<String> id = npcManager.findIdByEntity(target);
        if (id.isEmpty()) return;
        Optional<NpcDefinition> def = npcManager.findById(id.get());
        if (def.isEmpty()) return;

        List<String> commands = switch (click) {
            case RIGHT -> def.get().rightClickCommands();
            case LEFT  -> def.get().leftClickCommands();
        };
        executor.executeCommands(player, commands);
    }

    private enum Click { RIGHT, LEFT }
}
