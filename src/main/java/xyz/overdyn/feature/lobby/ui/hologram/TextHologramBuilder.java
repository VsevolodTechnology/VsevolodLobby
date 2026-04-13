package xyz.overdyn.feature.lobby.ui.hologram;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;

import java.util.ArrayList;
import java.util.List;

public final class TextHologramBuilder {

    private final Pos basePosition;
    private double lineSpacing = 0.25;
    private final List<LineDefinition> lines = new ArrayList<>();

    public TextHologramBuilder(Pos basePosition) {
        this.basePosition = basePosition;
    }

    public TextHologramBuilder spacing(double spacing) {
        this.lineSpacing = spacing;
        return this;
    }

    public TextHologramBuilder line(Component text) {
        return line(text, TextHologramStyle.defaults());
    }

    public TextHologramBuilder line(Component text, TextHologramStyle style) {
        lines.add(new LineDefinition(text, style.copy()));
        return this;
    }

    public TextHologram build() {
        TextHologram hologram = new TextHologram(basePosition, lineSpacing);
        for (LineDefinition line : lines) {
            hologram.addLine(line.text(), line.style());
        }
        return hologram;
    }

    private record LineDefinition(Component text, TextHologramStyle style) {
    }
}

