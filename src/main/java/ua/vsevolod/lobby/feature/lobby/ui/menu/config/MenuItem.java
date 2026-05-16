package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;

import java.util.List;

/**
 * One clickable slot inside a configured menu.
 *
 * <p>Placeholders supported in {@link #name} and {@link #lore}:</p>
 * <ul>
 *   <li>{@code {player}} — viewing player's username</li>
 *   <li>{@code {online}} — total online on this server</li>
 * </ul>
 */
public record MenuItem(
        int slot,
        String material,
        String name,
        List<String> lore,
        boolean glint,
        NpcAction action
) {
    public MenuItem {
        if (material == null || material.isBlank()) throw new IllegalArgumentException("material required");
        if (lore == null) lore = List.of();
        if (action == null) action = NpcAction.NONE;
    }
}
