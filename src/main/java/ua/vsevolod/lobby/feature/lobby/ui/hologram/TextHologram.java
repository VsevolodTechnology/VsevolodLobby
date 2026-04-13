package ua.vsevolod.lobby.feature.lobby.ui.hologram;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TextHologram {

    public final List<TextHologramEntry> entries = new ArrayList<>();
    private Pos basePosition;
    private final double lineSpacing;

    public TextHologram(Pos basePosition, double lineSpacing) {
        this.basePosition = basePosition;
        this.lineSpacing = lineSpacing;
    }

    public TextHologram addLine(Component text, TextHologramStyle style) {
        Pos linePosition = basePosition.sub(0, entries.size() * lineSpacing, 0);
        entries.add(new TextHologramEntry(linePosition, text, style));
        return this;
    }

    public void show(Player player) {
        entries.forEach(entry -> entry.show(player));
    }

    public void hide(Player player) {
        entries.forEach(entry -> entry.hide(player));
    }

    public void showAll(Collection<Player> players) {
        entries.forEach(entry -> entry.showAll(players));
    }

    public void hideAll(Collection<Player> players) {
        entries.forEach(entry -> entry.hideAll(players));
    }

    public void teleportAll(Collection<Player> players, Pos newBasePosition) {
        this.basePosition = newBasePosition;
        for (int index = 0; index < entries.size(); index++) {
            Pos linePosition = newBasePosition.sub(0, index * lineSpacing, 0);
            entries.get(index).teleportAll(players, linePosition);
        }
    }

    public void updateLineTextAll(Collection<Player> players, int index, Component text) {
        if (isInvalidIndex(index)) {
            return;
        }
        entries.get(index).updateTextAll(players, text);
    }

    public void updateLineStyleAll(Collection<Player> players, int index, TextHologramStyle style) {
        if (isInvalidIndex(index)) {
            return;
        }
        entries.get(index).updateStyleAll(players, style);
    }

    private boolean isInvalidIndex(int index) {
        return index < 0 || index >= entries.size();
    }
}

