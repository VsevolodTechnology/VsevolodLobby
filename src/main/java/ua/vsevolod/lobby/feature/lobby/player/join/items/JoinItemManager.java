package ua.vsevolod.lobby.feature.lobby.player.join.items;

import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and gives out the configured hotbar items on join. Each item carries
 * {@link #JOIN_ITEM_ID} so {@link JoinItemUseListener} can dispatch its click action.
 */
public final class JoinItemManager {

    public static final Tag<String> JOIN_ITEM_ID = Tag.String("lobby-join-item");

    private JoinItemManager() {}

    /** Give all items whose {@code condition} matches this player. */
    public static void giveAll(Player player) {
        boolean bypass = LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
        for (JoinItemDefinition def : JoinItemsConfigSection.INSTANCE.current().items()) {
            if (!matchesCondition(def.condition(), bypass)) continue;
            player.getInventory().setItemStack(def.slot(), buildItem(def));
        }
    }

    private static boolean matchesCondition(JoinItemDefinition.Condition cond, boolean bypass) {
        return switch (cond) {
            case ALWAYS -> true;
            case BYPASS_ONLY -> bypass;
            case NON_BYPASS -> !bypass;
        };
    }

    private static ItemStack buildItem(JoinItemDefinition def) {
        Material material = Material.fromKey(def.material());
        if (material == null) {
            System.err.println("[JoinItem] Unknown material '" + def.material()
                    + "' for item '" + def.id() + "' — falling back to BARRIER");
            material = Material.BARRIER;
        }

        ItemStack.Builder builder = ItemStack.builder(material).set(JOIN_ITEM_ID, def.id());

        if (def.name() != null) {
            builder.set(DataComponents.CUSTOM_NAME,
                    Text.raw(def.name()).decoration(TextDecoration.ITALIC, false));
        }
        if (!def.lore().isEmpty()) {
            List<net.kyori.adventure.text.Component> loreLines = new ArrayList<>(def.lore().size());
            for (String line : def.lore()) {
                loreLines.add(Text.raw(line).decoration(TextDecoration.ITALIC, false));
            }
            builder.set(DataComponents.LORE, loreLines);
        }
        if (def.glint()) {
            builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return builder.hideExtraTooltip().build();
    }
}
