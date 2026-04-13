package ua.vsevolod.lobby.feature.lobby.player.join;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerSpawnEvent;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public final class LobbyJoinListener implements LobbyEventRegistration {

    private final LobbyJoinInitializer initializer;

    public LobbyJoinListener(LobbyJoinInitializer initializer) {
        this.initializer = initializer;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                initializer.initialize(event.getPlayer());
//                attachCustomNametag(event.getPlayer());
            }
        });
    }
//
//    public static void attachCustomNametag(Player player) {
//        Entity nametag = new Entity(EntityType.TEXT_DISPLAY);
//        nametag.setInstance(player.getInstance(), player.getPosition());
//        nametag.setNoGravity(true);
//
//        nametag.editEntityMeta(TextDisplayMeta.class, nametagmeta -> {
//            nametagmeta.setText(Component.text(player.getUsername()));
//            nametagmeta.setShadow(true);
//            nametagmeta.setTranslation(new Pos(0, 0.30, 0));
//            nametagmeta.setBackgroundColor(0xF1BB58);
//            nametagmeta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
//        });
//
//        player.addPassenger(nametag);
//    }
}

