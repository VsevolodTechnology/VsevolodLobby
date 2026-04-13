package xyz.overdyn.feature.lobby.audio.music;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.feature.lobby.bootstrap.LobbyEventRegistration;

public final class LobbyMusicPlayerListener implements LobbyEventRegistration {

    private final LobbyMusicManager music;

    public LobbyMusicPlayerListener(LobbyMusicManager lobbyMusicManager) {
        this.music = lobbyMusicManager;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getHand() != PlayerHand.MAIN) {
                return;
            }

            ItemStack item = event.getItemStack();
            if (item.material() != Material.MUSIC_DISC_CAT && item.material() != Material.MUSIC_DISC_BLOCKS) {
                return;
            }
            if (!item.hasTag(LobbyMusicManager.MUSIC_TAG)) {
                return;
            }

            var player = event.getPlayer();
            boolean enabled = music.isDisabled(player);
            music.setEnabled(player, enabled);

            player.sendMessage(enabled ? enabledMessage() : disabledMessage());

            int slot = player.getHeldSlot();
            player.getInventory().setItemStack(slot, LobbyMusicManager.getMusicToggle(enabled));
        });
    }

    private Component enabledMessage() {
        return Component.text("[", NamedTextColor.DARK_GRAY).append(LobbyMusicManager.MUSIC_TEXT).append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text("Вы", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)).append(Component.space())
                .append(Component.text("включили", TextColor.color(0x81E366))).append(Component.space())
                .append(Component.text("проигрывание музыки", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));
    }

    private Component disabledMessage() {
        return Component.text("[", NamedTextColor.DARK_GRAY).append(LobbyMusicManager.MUSIC_TEXT).append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text("Вы", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)).append(Component.space())
                .append(Component.text("выключили", TextColor.color(0xE36666))).append(Component.space())
                .append(Component.text("проигрывание музыки", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));
    }
}
