package ua.vsevolod.lobby.feature.lobby.ui.hologram.render;

/**
 * Logical display backend a viewer is rendering a hologram with.
 *
 * <p>Modern Minecraft clients (1.19.4+) get the {@link #TEXT_DISPLAY} entity, which supports
 * background colours, alignment, billboard modes, and per-axis scale. Older protocols don't
 * understand that entity type, so we fall back to {@link #ARMOR_STAND} with a nametag — the
 * only line-of-text strategy that has worked since 1.8.</p>
 */
public enum DisplayType {
    TEXT_DISPLAY,
    ARMOR_STAND
}
