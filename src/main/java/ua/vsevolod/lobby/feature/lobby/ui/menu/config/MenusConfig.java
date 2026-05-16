package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record MenusConfig(Map<String, MenuDefinition> menus) {
    public MenusConfig {
        menus = menus == null ? Map.of() : Map.copyOf(menus);
    }

    public MenusConfig withMenu(MenuDefinition def) {
        Map<String, MenuDefinition> next = new LinkedHashMap<>(menus);
        next.put(def.id(), def);
        return new MenusConfig(next);
    }
}
