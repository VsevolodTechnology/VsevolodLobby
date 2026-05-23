package ua.vsevolod.lobby.config.server;

import java.util.List;

/**
 * One server as defined in {@code config/servers.yml}. The map key in
 * {@link ServersConfig#servers} is the server id; this record holds the rest.
 *
 * <p>{@link #material} is a Minecraft item key (e.g. {@code diamond_sword}) — the icon
 * shown for a menu item bound to this server. {@link #status} is the manual override:
 * {@code ONLINE} delegates to live proxy polling, {@code OFFLINE}/{@code SOON} force
 * that state regardless of polling.</p>
 *
 * <p>{@link #itemName} and {@link #itemLore} are <b>per-server overrides</b> of the menu
 * item's look. Leave them empty/null to use the shared template from {@link ServersConfig}
 * ({@code itemName} / {@code itemLore}); fill them to give this one server its own name,
 * description and colours.</p>
 */
public record ServerEntry(
        String displayName,
        String versionCore,
        ServerStatus status,
        int maxOnline,
        List<String> tags,
        String material,
        String itemName,
        List<String> itemLore
) {
    public ServerEntry {
        if (displayName == null || displayName.isBlank()) displayName = "Сервер";
        if (versionCore == null) versionCore = "";
        if (status == null) status = ServerStatus.ONLINE;
        if (maxOnline <= 0) maxOnline = 100;
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (material == null || material.isBlank()) material = "diamond_sword";
        // null = "use the shared ServersConfig template"
        if (itemName != null && itemName.isBlank()) itemName = null;
        itemLore = itemLore == null ? null : List.copyOf(itemLore);
    }

    /** True when this server overrides the shared name template. */
    public boolean hasNameOverride() {
        return itemName != null;
    }

    /** True when this server overrides the shared lore template. */
    public boolean hasLoreOverride() {
        return itemLore != null && !itemLore.isEmpty();
    }
}
