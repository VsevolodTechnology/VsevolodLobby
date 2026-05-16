package ua.vsevolod.lobby.feature.lobby.ui.menu.config;

import java.util.List;

/** A group of decoration slots filled with the same material. */
public record MenuDecor(String material, List<Integer> slots) {
    public MenuDecor {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
