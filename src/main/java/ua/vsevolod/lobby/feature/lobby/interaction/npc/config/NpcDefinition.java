package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

/**
 * Snapshot of a single NPC's configuration.
 *
 * <p>{@code id} is the stable identifier used by commands and reload-diff. Renaming an NPC
 * means «delete and re-add»; the {@code id} should never change for the same logical NPC.</p>
 *
 * <p>{@code name} and {@code description} may be {@code null} → no name above the head.
 * {@code skin} may be {@code null}/blank → default skin.</p>
 *
 * <p>{@code visible} = {@code false} keeps the NPC in the config but does not spawn it.
 * {@code glowColor} is one of the Adventure {@code NamedTextColor} names (e.g. {@code red},
 * {@code aqua}, {@code gold}, {@code white}) — applied only when {@code glowing} is true.
 * {@code null} or empty {@code glowColor} → default uncoloured glow (white).</p>
 *
 * <p>{@code rightAction} fires on right-click ({@code PlayerEntityInteractEvent}),
 * {@code leftAction} fires on left-click ({@code EntityAttackEvent}).</p>
 */
public record NpcDefinition(
        String id,
        NpcPosition position,
        String name,
        String description,
        String skin,
        boolean glowing,
        String glowColor,
        boolean visible,
        NpcAction rightAction,
        NpcAction leftAction
) {
    public NpcDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("NPC id must be non-blank");
        if (position == null) throw new IllegalArgumentException("NPC position is required");
        if (rightAction == null) rightAction = NpcAction.NONE;
        if (leftAction == null)  leftAction  = NpcAction.NONE;
    }

    public NpcDefinition withPosition(NpcPosition newPosition) {
        return new NpcDefinition(id, newPosition, name, description, skin, glowing, glowColor, visible, rightAction, leftAction);
    }

    public NpcDefinition withName(String newName) {
        return new NpcDefinition(id, position, newName, description, skin, glowing, glowColor, visible, rightAction, leftAction);
    }

    public NpcDefinition withDescription(String newDescription) {
        return new NpcDefinition(id, position, name, newDescription, skin, glowing, glowColor, visible, rightAction, leftAction);
    }

    public NpcDefinition withSkin(String newSkin) {
        return new NpcDefinition(id, position, name, description, newSkin, glowing, glowColor, visible, rightAction, leftAction);
    }

    public NpcDefinition withGlowing(boolean newGlowing) {
        return new NpcDefinition(id, position, name, description, skin, newGlowing, glowColor, visible, rightAction, leftAction);
    }

    public NpcDefinition withGlowColor(String newGlowColor) {
        return new NpcDefinition(id, position, name, description, skin, glowing, newGlowColor, visible, rightAction, leftAction);
    }

    public NpcDefinition withVisible(boolean newVisible) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, newVisible, rightAction, leftAction);
    }

    public NpcDefinition withRightAction(NpcAction action) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, visible, action, leftAction);
    }

    public NpcDefinition withLeftAction(NpcAction action) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, visible, rightAction, action);
    }
}
