package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import java.util.List;

/**
 * One item inside a configured menu.
 *
 * <p>Supports multiple slots (for decoration glass panes) via {@link #slots}.
 * Commands use DeluxeMenus-style prefixes:</p>
 * <ul>
 *   <li>{@code [player] <cmd>} — run as player</li>
 *   <li>{@code [console] <cmd>} — run as console (op)</li>
 *   <li>{@code [op] <cmd>} — run temporarily as op</li>
 *   <li>{@code [message] <text>} — send message to player</li>
 *   <li>{@code [close]} — close the menu</li>
 *   <li>{@code [connect] <server>} — transfer to server</li>
 *   <li>{@code [menu] <id>} — open another menu</li>
 *   <li>{@code [parkour]} — start parkour run</li>
 *   <li>{@code [broadcast] <text>} — broadcast to all</li>
 * </ul>
 *
 * <p>Placeholders in {@link #displayName} and {@link #lore}:
 * {@code {player}} — username, {@code {online}} — online count.</p>
 *
 * <p>If {@link #serverId} is set, the item is a <b>server item</b>: its icon, name and lore
 * are rendered live from {@code config/servers.yml} (ignoring {@link #material},
 * {@link #displayName}, {@link #lore}), it auto-updates while the menu is open, and clicking
 * it connects the player to that server. {@code null} = a normal command item.</p>
 */
public record MenuItem(
        List<Integer> slots,
        String material,
        String displayName,
        List<String> lore,
        boolean glint,
        List<String> leftClickCommands,
        List<String> rightClickCommands,
        String serverId
) {
    public MenuItem {
        if (material == null || material.isBlank()) throw new IllegalArgumentException("material required");
        if (slots == null || slots.isEmpty()) throw new IllegalArgumentException("slot or slots required");
        slots = List.copyOf(slots);
        if (lore == null) lore = List.of();
        if (leftClickCommands == null) leftClickCommands = List.of();
        if (rightClickCommands == null) rightClickCommands = List.of();
        if (serverId != null && serverId.isBlank()) serverId = null;
    }

    /** True when this item is bound to a server (see {@link #serverId}). */
    public boolean isServerItem() {
        return serverId != null;
    }

    /** Primary slot (first in the list). Used for click event tag matching. */
    public int primarySlot() {
        return slots.get(0);
    }
}
