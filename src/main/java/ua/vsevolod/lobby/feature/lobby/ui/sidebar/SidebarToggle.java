package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemsConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.items.ToggleItemDefinition;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

/**
 * Per-player scoreboard (sidebar) toggle.
 * Gives a toggle item at slot {@link #ITEM_SLOT} on spawn; clicking it shows/hides the sidebar.
 * Preference is persisted to MongoDB via {@link PlayerPreferencesService}.
 */
public final class SidebarToggle {

    public static final int ITEM_SLOT = 6;

    private static final Tag<String> TOGGLE_TAG = Tag.String("lobby-toggle-sidebar");
    private static final String STATE_SHOWN  = "shown";
    private static final String STATE_HIDDEN = "hidden";

    private final LobbySidebar sidebar;
    private PlayerPreferencesService preferencesService;

    /** Players who have explicitly hidden their sidebar. */
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();

    public SidebarToggle(LobbySidebar sidebar) {
        this.sidebar = sidebar;
    }

    public void setPreferencesService(PlayerPreferencesService service) {
        this.preferencesService = service;
    }

    /**
     * Apply a saved preference silently (no messages, no sidebar state change).
     * Call this BEFORE the spawn listener that gives the item so the item reflects the correct state.
     */
    public void applyPreference(UUID uuid, boolean sidebarHidden) {
        if (sidebarHidden) hiddenPlayers.add(uuid);
        else hiddenPlayers.remove(uuid);
    }

    /** True if this player has hidden their sidebar via the toggle. */
    public boolean isHidden(Player player) {
        return hiddenPlayers.contains(player.getUuid());
    }

    public void register(EventNode<Event> node) {
        node.addListener(PlayerDisconnectEvent.class, event -> hiddenPlayers.remove(event.getPlayer().getUuid()));
    }

    public void toggle(Player player) {
        boolean nowHidden = !hiddenPlayers.contains(player.getUuid());
        ToggleItemDefinition def = JoinItemsConfig.get().toggleItems.sidebar();
        if (nowHidden) {
            hiddenPlayers.add(player.getUuid());
            sidebar.hide(player);
            player.sendMessage(def.message(false));
        } else {
            hiddenPlayers.remove(player.getUuid());
            sidebar.show(player);
            player.sendMessage(def.message(true));
        }
        if (preferencesService != null) {
            preferencesService.saveSidebarHidden(player.getUuid(), nowHidden);
        }
    }

    private void updateItem(Player player) {
        player.getInventory().setItemStack(ITEM_SLOT, getItem(hiddenPlayers.contains(player.getUuid())));
    }

    /** Builds the sidebar toggle item live from {@code config/toggle-items.yml}. */
    public static ItemStack getItem(boolean hidden) {
        ToggleItemDefinition def = JoinItemsConfig.get().toggleItems.sidebar();
        boolean enabled = !hidden;
        return ItemStack.builder(def.material(enabled))
                .set(DataComponents.CUSTOM_NAME, def.displayName(enabled))
                .set(DataComponents.LORE, def.lore(enabled))
                .set(TOGGLE_TAG, hidden ? STATE_HIDDEN : STATE_SHOWN)
                .build();
    }
}
