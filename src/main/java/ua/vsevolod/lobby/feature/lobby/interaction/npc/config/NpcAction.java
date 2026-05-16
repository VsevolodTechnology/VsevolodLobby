package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

/**
 * What happens when a player clicks an NPC.
 *
 * <p>{@link #type}:</p>
 * <ul>
 *   <li>{@code none} — no-op (default)</li>
 *   <li>{@code run-command} — {@link #target} is executed as a chat command
 *       (with or without the leading {@code /}). {@link #executeAsOp} = true raises
 *       the player's permission-level to 4 for the duration of the command.</li>
 *   <li>{@code open-menu} — opens menu by {@link #target} id. (Wired in Phase 4 fully;
 *       for now only the hardcoded {@code mode-selector} resolves.)</li>
 *   <li>{@code parkour-start} — special action that starts the parkour run for the player.</li>
 * </ul>
 */
public record NpcAction(String type, String target, boolean executeAsOp) {

    public static final NpcAction NONE = new NpcAction("none", "", false);

    public boolean isNone() {
        return type == null || "none".equalsIgnoreCase(type);
    }
}
