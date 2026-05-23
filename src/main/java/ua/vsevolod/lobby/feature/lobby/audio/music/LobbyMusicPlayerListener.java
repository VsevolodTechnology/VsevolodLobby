package ua.vsevolod.lobby.feature.lobby.audio.music;

import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemsConfig;

public final class LobbyMusicPlayerListener implements LobbyEventRegistration {

    private final LobbyMusicManager music;
    private final LobbyMusicSelectorMenu selectorMenu;

    public LobbyMusicPlayerListener(LobbyMusicManager lobbyMusicManager, LobbyMusicSelectorMenu selectorMenu) {
        this.music = lobbyMusicManager;
        this.selectorMenu = selectorMenu;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getHand() != PlayerHand.MAIN) {
                return;
            }
            // Identify the toggle by its tag, not material — the material is configurable.
            ItemStack item = event.getItemStack();
            if (!item.hasTag(LobbyMusicManager.MUSIC_TAG)) {
                return;
            }

            var player = event.getPlayer();
            boolean enabled = music.isDisabled(player);
            music.setEnabled(player, enabled);

            player.sendMessage(JoinItemsConfig.get().toggleItems.music().message(enabled));

            int slot = player.getHeldSlot();
            player.getInventory().setItemStack(slot, LobbyMusicManager.getMusicToggle(enabled));
        });

        // Q key (item drop): open the music selector menu
        handler.addListener(ItemDropEvent.class, event -> {
            ItemStack item = event.getItemStack();
            if (!item.hasTag(LobbyMusicManager.MUSIC_TAG)) return;
            event.setCancelled(true);
            selectorMenu.open(event.getPlayer());
        });
    }
}
