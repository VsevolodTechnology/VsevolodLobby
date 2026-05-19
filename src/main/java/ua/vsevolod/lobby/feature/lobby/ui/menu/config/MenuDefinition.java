package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of a single chest menu configuration (DeluxeMenus-style).
 *
 * <p>{@link #size} is the total number of inventory slots (rows × 9, e.g. 5 rows = 45).
 * Decoration items are just regular {@link MenuItem} entries with multiple {@code slots}.</p>
 */
public record MenuDefinition(
        String id,
        String menuTitle,
        int size,
        String openCommand,
        Visibility visibility,
        Map<String, MenuItem> items
) {
    public MenuDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("menu id required");
        if (size < 9 || size > 54 || size % 9 != 0)
            throw new IllegalArgumentException("size must be 9–54 and divisible by 9 (rows * 9)");
        if (visibility == null) visibility = Visibility.ALL;
        if (items == null) items = Map.of();
        else items = Map.copyOf(items);
    }

    /** Rows derived from size (e.g. size=45 → 5 rows). */
    public int rows() {
        return size / 9;
    }

    public MenuDefinition withVisibility(Visibility v) {
        return new MenuDefinition(id, menuTitle, size, openCommand, v, new LinkedHashMap<>(items));
    }

    public enum Visibility {
        ALL, BYPASS_ONLY;

        public static Visibility fromString(String raw) {
            if (raw == null) return ALL;
            return "bypass-only".equalsIgnoreCase(raw.replace('_', '-')) ? BYPASS_ONLY : ALL;
        }

        public String toYaml() {
            return this == BYPASS_ONLY ? "bypass-only" : "all";
        }
    }
}
