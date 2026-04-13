package xyz.overdyn.feature.lobby.interaction.qr;

import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

import java.util.List;

public final class LobbyQrMapItem {
    public static final int MAP_ID = 7771;
    public static final String URL = "https://example.com";
    public static final Tag<Integer> QR_TAG = Tag.Integer("qr-map-id");

    private LobbyQrMapItem() {
    }

    public static ItemStack create() {
        return ItemStack.of(Material.FILLED_MAP)
                .with(DataComponents.MAP_ID, MAP_ID)
                .withTag(QR_TAG, MAP_ID)
                .withCustomName(Component.text("§6QR-карта"))
                .with(DataComponents.LORE, List.of(
                        Component.text("§7В руке отображается QR-код"),
                        Component.text("§8" + URL)
                ));
    }
}
