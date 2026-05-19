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
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;
import ua.vsevolod.lobby.util.Text;

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
        if (nowHidden) {
            hiddenPlayers.add(player.getUuid());
            sidebar.hide(player);
            player.sendMessage(buildMessage(false));
        } else {
            hiddenPlayers.remove(player.getUuid());
            sidebar.show(player);
            player.sendMessage(buildMessage(true));
        }
        if (preferencesService != null) {
            preferencesService.saveSidebarHidden(player.getUuid(), nowHidden);
        }
    }

    private void updateItem(Player player) {
        player.getInventory().setItemStack(ITEM_SLOT, getItem(hiddenPlayers.contains(player.getUuid())));
    }

    // ── Static item builders ─────────────────────────────────────────────────

    private static final Component SIDEBAR_TEXT =
            Text.c("&#F1BB58&lС&#F1B958&lк&#F1B658&lо&#F1B458&lр&#F1B158&lб&#F1AF58&lо&#F1AD58&lр&#F1AB58&lд");

    private static final Component NAME_ON = SIDEBAR_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Виден", TextColor.color(0x8EB126)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .decoration(TextDecoration.ITALIC, false);

    private static final Component NAME_OFF = SIDEBAR_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Скрыт", TextColor.color(0xFA3B3B)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .decoration(TextDecoration.ITALIC, false);

    private static final ItemStack ITEM_SHOWN = buildItem(false);
    private static final ItemStack ITEM_HIDDEN = buildItem(true);

    public static ItemStack getItem(boolean hidden) {
        return hidden ? ITEM_HIDDEN : ITEM_SHOWN;
    }

    private static ItemStack buildItem(boolean hidden) {
        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.text(" «Информация»", TextColor.color(0x65D1FC)),
                Component.empty()
                        .append(Component.text(" - ", GRAY))
                        .append(Component.text("Статус: ", TextColor.color(0xFFF2E0)))
                        .append(hidden
                                ? Component.text("Скрыт", NamedTextColor.RED)
                                : Component.text("Отображается", NamedTextColor.GREEN)),
                Component.empty()
                        .append(Component.text(" - ", GRAY))
                        .append(Component.text("Включает/выключает", TextColor.color(0xFFF2E0))),
                Component.empty()
                        .append(Component.text(" - ", GRAY))
                        .append(Component.text("таблицу очков.", TextColor.color(0xFFF2E0))),
                Component.space(),
                Component.text("➥ Нажмите, чтобы переключиться", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(hidden ? Material.GRAY_DYE : Material.MAGENTA_DYE)
                .set(DataComponents.CUSTOM_NAME, hidden ? NAME_OFF : NAME_ON)
                .set(DataComponents.LORE, lore)
                .set(TOGGLE_TAG, hidden ? STATE_HIDDEN : STATE_SHOWN)
                .build();
    }

    private Component buildMessage(boolean nowShown) {
        Component action = nowShown
                ? Component.text("включили", TextColor.color(0x81E366))
                : Component.text("выключили", TextColor.color(0xE36666));
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(SIDEBAR_TEXT)
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text("Вы", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                .append(Component.space())
                .append(action)
                .append(Component.space())
                .append(Component.text("отображение скорборда", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));
    }
}
