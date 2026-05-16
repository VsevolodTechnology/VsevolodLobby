package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MenuDefinition(
        String id,
        String title,
        int rows,
        Visibility visibility,
        Map<String, MenuDecor> decor,
        List<MenuItem> items
) {
    public MenuDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("menu id required");
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("rows must be 1..6");
        if (visibility == null) visibility = Visibility.ALL;
        if (decor == null) decor = Map.of();
        else decor = Map.copyOf(decor);
        if (items == null) items = List.of();
        else items = List.copyOf(items);
    }

    public MenuDefinition withVisibility(Visibility v) {
        return new MenuDefinition(id, title, rows, v, new LinkedHashMap<>(decor), items);
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
