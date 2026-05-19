package ua.vsevolod.lobby.feature.lobby.ui.hologram.config;

import java.util.List;

/**
 * Configuration snapshot for a single static lobby hologram.
 *
 * <pre>
 * holograms:
 *   parkour:
 *     position: { x: 6.5, y: 81.2, z: -4.5 }
 *     lines:
 *       - "&#F1BB58&lПАРКУР"
 *       - "&#FFF2E0Проверь реакцию и точность"
 *     billboard: fixed          # center | vertical | horizontal | fixed
 *     scale: 0.8                # uniform, or {x: 1.0, y: 1.0, z: 1.0}
 *     shadow: true
 *     see_through: true
 *     alignment: center         # left | center | right
 *     background: '#1C1C1E'     # packed ARGB hex, or 0 for none
 *     use_default_background: true
 *     line_spacing: 0.25
 * </pre>
 */
public record HologramDefinition(
        String id,
        double x,
        double y,
        double z,
        List<String> lines,
        /** "center" | "vertical" | "horizontal" | "fixed" */
        String billboard,
        double scaleX,
        double scaleY,
        double scaleZ,
        boolean shadow,
        boolean seeThrough,
        /** "left" | "center" | "right" */
        String alignment,
        /** Packed ARGB background colour; 0 = none. */
        int backgroundColor,
        boolean useDefaultBackground,
        double lineSpacing
) {
    public HologramDefinition {
        if (lines == null) lines = List.of();
        if (billboard == null) billboard = "center";
        if (alignment == null) alignment = "center";
    }
}
