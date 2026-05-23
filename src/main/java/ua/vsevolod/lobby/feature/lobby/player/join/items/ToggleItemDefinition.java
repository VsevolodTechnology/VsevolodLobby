package ua.vsevolod.lobby.feature.lobby.player.join.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.item.Material;
import ua.vsevolod.lobby.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * One togglable hotbar item (music / sidebar / player-visibility switch). Lives in
 * {@code config/join-items.yml} under {@code toggleItems} — see {@link JoinItemsConfig}.
 *
 * <p>"enabled" is the item's <i>on</i> state (music playing, sidebar shown, players shown).
 * The icon swaps between {@link #enabledMaterial} and {@link #disabledMaterial}; the display
 * name is {@link #name} plus {@link #enabledLabel}/{@link #disabledLabel}; {@link #lore} is a
 * MiniMessage template where {@code {status}} expands to {@link #statusEnabled}/{@link #statusDisabled}.</p>
 */
public record ToggleItemDefinition(
        String enabledMaterial,
        String disabledMaterial,
        String name,
        String enabledLabel,
        String disabledLabel,
        List<String> lore,
        String statusEnabled,
        String statusDisabled,
        String enabledMessage,
        String disabledMessage
) {
    public ToggleItemDefinition {
        if (enabledMaterial == null || enabledMaterial.isBlank()) enabledMaterial = "stone";
        if (disabledMaterial == null || disabledMaterial.isBlank()) disabledMaterial = "stone";
        if (name == null) name = "";
        if (enabledLabel == null) enabledLabel = "";
        if (disabledLabel == null) disabledLabel = "";
        lore = lore == null ? List.of() : List.copyOf(lore);
        if (statusEnabled == null) statusEnabled = "";
        if (statusDisabled == null) statusDisabled = "";
        if (enabledMessage == null) enabledMessage = "";
        if (disabledMessage == null) disabledMessage = "";
    }

    /** Icon material for the given state (falls back to STONE on a bad key). */
    public Material material(boolean enabled) {
        Material m = Material.fromKey(enabled ? enabledMaterial : disabledMaterial);
        return m != null ? m : Material.STONE;
    }

    /** Display name = name + label for the given state, italic disabled. */
    public Component displayName(boolean enabled) {
        String label = enabled ? enabledLabel : disabledLabel;
        String full = label.isBlank() ? name : name + " " + label;
        return Text.raw(full).decoration(TextDecoration.ITALIC, false);
    }

    /** Lore lines for the given state, {@code {status}} substituted, italic disabled. */
    public List<Component> lore(boolean enabled) {
        String status = enabled ? statusEnabled : statusDisabled;
        List<Component> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(Text.raw(line.replace("{status}", status))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }

    /** Chat feedback line shown when the player flips the toggle. */
    public Component message(boolean enabled) {
        return Text.raw(enabled ? enabledMessage : disabledMessage);
    }
}
