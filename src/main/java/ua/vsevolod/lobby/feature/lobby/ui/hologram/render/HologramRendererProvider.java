package ua.vsevolod.lobby.feature.lobby.ui.hologram.render;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.player.compat.ClientCompat;

/**
 * Picks the right {@link HologramRenderer} for a given player or display type.
 *
 * <p>Decision is driven by {@link ClientCompat#supportsTextDisplay} which in turn reads the
 * player's protocol via the upstream Velocity property or the handshake — so clients that
 * connect through a translating proxy (ViaBackwards) but report a legacy protocol still get
 * the armor-stand fallback automatically.</p>
 */
public final class HologramRendererProvider {

    private HologramRendererProvider() {}

    public static HologramRenderer forPlayer(Player player) {
        return ClientCompat.supportsTextDisplay(player)
                ? TextDisplayRenderer.INSTANCE
                : ArmorStandRenderer.INSTANCE;
    }

    public static DisplayType displayTypeFor(Player player) {
        return ClientCompat.supportsTextDisplay(player)
                ? DisplayType.TEXT_DISPLAY
                : DisplayType.ARMOR_STAND;
    }

    public static HologramRenderer rendererFor(DisplayType type) {
        return switch (type) {
            case TEXT_DISPLAY -> TextDisplayRenderer.INSTANCE;
            case ARMOR_STAND  -> ArmorStandRenderer.INSTANCE;
        };
    }
}
