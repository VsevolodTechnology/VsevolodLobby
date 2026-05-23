package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.util.Text;

import java.util.ArrayList;
import java.util.List;

public final class LobbyQrMapItem {
    public static final int MAP_ID = 7771;
    public static final Tag<Integer> QR_TAG = Tag.Integer("qr-map-id");

    private LobbyQrMapItem() {
    }

    /** URL encoded into the QR image — read fresh so {@code qr-card.yml} drives it.
     *  Supports {discord}/{telegram}/{website} placeholders resolved from socials.yml. */
    public static String url() {
        return ua.vsevolod.lobby.config.SocialsConfig.get().resolve(QrCardConfig.get().qrUrl);
    }

    public static ItemStack create() {
        QrCardConfig cfg = QrCardConfig.get();
        ItemStack.Builder builder = ItemStack.builder(Material.FILLED_MAP)
                .set(DataComponents.MAP_ID, MAP_ID)
                .set(DataComponents.CUSTOM_NAME,
                        Text.raw(cfg.itemName).decoration(TextDecoration.ITALIC, false))
                .set(QR_TAG, MAP_ID);

        if (!cfg.itemLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(cfg.itemLore.size());
            for (String line : cfg.itemLore) {
                lore.add(Text.raw(line).decoration(TextDecoration.ITALIC, false));
            }
            builder.set(DataComponents.LORE, lore);
        }
        return builder.hideExtraTooltip().build();   // strip "Map #7771" line
    }
}
