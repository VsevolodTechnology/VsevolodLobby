package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

import java.util.List;

/**
 * Snapshot of a single NPC's configuration (DeluxeMenus-inspired format).
 *
 * <p>An NPC is normally a player-like mannequin (set {@link #skin}). Set {@link #entityType}
 * to a mob key (e.g. {@code minecraft:allay}) to make it a decorative creature instead;
 * {@link #scale} then enlarges it. Leave {@code entityType} null for a regular NPC.</p>
 *
 * <h3>Skin formats (set via {@link #skin}):</h3>
 * <pre>
 * skin: Notch                       # by username
 * skin: 'url:https://...'           # by texture URL
 * skin: 'value:&lt;base64&gt;;sig:&lt;base64&gt;'  # pre-baked texture
 * skin: null                        # no custom skin
 * </pre>
 *
 * <h3>Click commands (separate left and right):</h3>
 * <pre>
 * right_click_commands:
 *   - '[menu] mode-selector'
 * left_click_commands:
 *   - '[connect] lobby'
 * </pre>
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
        List<String> rightClickCommands,
        List<String> leftClickCommands,
        String entityType,
        double scale
) {
    public NpcDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("NPC id must be non-blank");
        if (position == null) throw new IllegalArgumentException("NPC position is required");
        if (rightClickCommands == null) rightClickCommands = List.of();
        if (leftClickCommands == null)  leftClickCommands  = List.of();
        if (entityType != null && entityType.isBlank()) entityType = null;
        if (scale <= 0) scale = 1.0;
    }

    /** True when this NPC is a decorative mob rather than a player mannequin. */
    public boolean isMob() {
        return entityType != null;
    }

    public NpcDefinition withPosition(NpcPosition p) {
        return new NpcDefinition(id, p, name, description, skin, glowing, glowColor, visible, rightClickCommands, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withName(String n) {
        return new NpcDefinition(id, position, n, description, skin, glowing, glowColor, visible, rightClickCommands, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withDescription(String d) {
        return new NpcDefinition(id, position, name, d, skin, glowing, glowColor, visible, rightClickCommands, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withSkin(String s) {
        return new NpcDefinition(id, position, name, description, s, glowing, glowColor, visible, rightClickCommands, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withGlowing(boolean g) {
        return new NpcDefinition(id, position, name, description, skin, g, glowColor, visible, rightClickCommands, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withGlowColor(String c) {
        return new NpcDefinition(id, position, name, description, skin, glowing, c, visible, rightClickCommands, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withVisible(boolean v) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, v, rightClickCommands, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withRightClickCommands(List<String> cmds) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, visible, cmds, leftClickCommands, entityType, scale);
    }
    public NpcDefinition withLeftClickCommands(List<String> cmds) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, visible, rightClickCommands, cmds, entityType, scale);
    }
}
