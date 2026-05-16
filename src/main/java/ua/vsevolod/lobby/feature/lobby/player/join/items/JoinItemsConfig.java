package ua.vsevolod.lobby.feature.lobby.player.join.items;

import java.util.List;

public record JoinItemsConfig(List<JoinItemDefinition> items) {
    public JoinItemsConfig {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
