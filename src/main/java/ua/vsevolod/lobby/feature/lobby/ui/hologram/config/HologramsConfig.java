package ua.vsevolod.lobby.feature.lobby.ui.hologram.config;

import java.util.Map;

public record HologramsConfig(Map<String, HologramDefinition> holograms) {
    public HologramsConfig {
        if (holograms == null) holograms = Map.of();
    }
}
