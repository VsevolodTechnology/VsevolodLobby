package ua.vsevolod.lobby.feature.lobby.player.join.items;

import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;

import java.util.List;

/**
 * Snapshot of one configurable join item.
 *
 * <p>Reuses {@link NpcAction} for click handling so the action types
 * ({@code none}, {@code run-command}, {@code open-menu}, {@code parkour-start})
 * are the same across NPCs and items. The set is registered once on the shared
 * {@code NpcActionExecutor}.</p>
 *
 * <p>{@link Condition#BYPASS_ONLY} — only members of {@code BYPASS_USERS} receive the item.
 * {@link Condition#NON_BYPASS} — everyone except {@code BYPASS_USERS} receives the item.
 * {@link Condition#ALWAYS} — everyone.</p>
 */
public record JoinItemDefinition(
        String id,
        int slot,
        String material,
        String name,
        List<String> lore,
        boolean glint,
        Condition condition,
        NpcAction rightAction,
        NpcAction leftAction
) {
    public JoinItemDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        if (material == null || material.isBlank()) throw new IllegalArgumentException("material must be non-blank");
        if (lore == null) lore = List.of();
        if (condition == null) condition = Condition.ALWAYS;
        if (rightAction == null) rightAction = NpcAction.NONE;
        if (leftAction == null) leftAction = NpcAction.NONE;
    }

    public enum Condition {
        ALWAYS, BYPASS_ONLY, NON_BYPASS;

        public static Condition fromString(String raw) {
            if (raw == null) return ALWAYS;
            return switch (raw.trim().toLowerCase().replace('_', '-')) {
                case "bypass-only" -> BYPASS_ONLY;
                case "non-bypass" -> NON_BYPASS;
                default -> ALWAYS;
            };
        }

        public String toYaml() {
            return switch (this) {
                case ALWAYS -> "always";
                case BYPASS_ONLY -> "bypass-only";
                case NON_BYPASS -> "non-bypass";
            };
        }
    }
}
