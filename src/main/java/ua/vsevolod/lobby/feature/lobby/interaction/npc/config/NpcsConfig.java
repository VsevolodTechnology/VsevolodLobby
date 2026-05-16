package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

import java.util.List;

public record NpcsConfig(List<NpcDefinition> npcs) {
    public NpcsConfig {
        if (npcs == null) npcs = List.of();
        else npcs = List.copyOf(npcs);
    }
}
