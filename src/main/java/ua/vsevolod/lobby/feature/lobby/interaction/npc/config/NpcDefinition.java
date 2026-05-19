package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

import java.util.List;

/**
 * Snapshot of a single NPC's configuration (DeluxeMenus-inspired format).
 *
 * <h3>Skin formats (set via {@link #skin}):</h3>
 * <pre>
 * # By Mojang username:
 * skin:
 *   type: username
 *   value: Notch
 *
 * # By texture URL (fetched via mineskin.org):
 * skin:
 *   type: url
 *   value: 'https://textures.minecraft.net/texture/abc...'
 *
 * # Pre-baked base64 texture (no network lookup):
 * skin:
 *   type: texture
 *   value: 'eyJ0ZXh0dXJlcyI6...'
 *   signature: 'abc123...'   # optional
 *
 * # No custom skin:
 * skin: null
 * </pre>
 *
 * <h3>Click commands (separate left and right):</h3>
 * <pre>
 * right_click_commands:
 *   - '[menu] mode-selector'
 *   - '[message] &aВы открыли меню!'
 * left_click_commands:
 *   - '[player] server adventure'
 *   - '[connect] lobby'
 * </pre>
 *
 * <p>Multi-word arguments are supported: {@code [player] server adventure} →
 * runs {@code /server adventure} as the player.</p>
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
        List<String> leftClickCommands
) {
    public NpcDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("NPC id must be non-blank");
        if (position == null) throw new IllegalArgumentException("NPC position is required");
        if (rightClickCommands == null) rightClickCommands = List.of();
        if (leftClickCommands == null)  leftClickCommands  = List.of();
    }

    public NpcDefinition withPosition(NpcPosition p) {
        return new NpcDefinition(id, p, name, description, skin, glowing, glowColor, visible, rightClickCommands, leftClickCommands);
    }
    public NpcDefinition withName(String n) {
        return new NpcDefinition(id, position, n, description, skin, glowing, glowColor, visible, rightClickCommands, leftClickCommands);
    }
    public NpcDefinition withDescription(String d) {
        return new NpcDefinition(id, position, name, d, skin, glowing, glowColor, visible, rightClickCommands, leftClickCommands);
    }
    public NpcDefinition withSkin(String s) {
        return new NpcDefinition(id, position, name, description, s, glowing, glowColor, visible, rightClickCommands, leftClickCommands);
    }
    public NpcDefinition withGlowing(boolean g) {
        return new NpcDefinition(id, position, name, description, skin, g, glowColor, visible, rightClickCommands, leftClickCommands);
    }
    public NpcDefinition withGlowColor(String c) {
        return new NpcDefinition(id, position, name, description, skin, glowing, c, visible, rightClickCommands, leftClickCommands);
    }
    public NpcDefinition withVisible(boolean v) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, v, rightClickCommands, leftClickCommands);
    }
    public NpcDefinition withRightClickCommands(List<String> cmds) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, visible, cmds, leftClickCommands);
    }
    public NpcDefinition withLeftClickCommands(List<String> cmds) {
        return new NpcDefinition(id, position, name, description, skin, glowing, glowColor, visible, rightClickCommands, cmds);
    }
}
