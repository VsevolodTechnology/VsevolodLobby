package ua.vsevolod.lobby.feature.parkour;

import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;

import java.util.function.Consumer;

public final class ParkourListener {

    private final ParkourService service;
    private final Consumer<Player> onSessionFinished;

    public ParkourListener(ParkourService service, Consumer<Player> onSessionFinished) {
        this.service = service;
        this.onSessionFinished = onSessionFinished;
    }

    public void register(EventNode<Event> node) {
        node.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            ParkourSession session = service.getSession(player);
            if (session == null) {
                return;
            }

            session.tick();
            if (session.isFinished()) {
                onSessionFinished.accept(player);
            }
        });

        node.addListener(PlayerDisconnectEvent.class, event -> {
            service.stop(event.getPlayer());
        });
    }
}
