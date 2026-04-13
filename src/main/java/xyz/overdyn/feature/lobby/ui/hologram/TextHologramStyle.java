package xyz.overdyn.feature.lobby.ui.hologram;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;

public final class TextHologramStyle {

    private int lineWidth = 200;
    private int backgroundColor = 0;
    private byte textOpacity = (byte) 255;

    private boolean shadow = false;
    private boolean seeThrough = true;
    private boolean useDefaultBackground = false;

    private TextDisplayMeta.Alignment alignment = TextDisplayMeta.Alignment.CENTER;
    private AbstractDisplayMeta.BillboardConstraints billboard = AbstractDisplayMeta.BillboardConstraints.CENTER;

    private Vec scale = new Vec(1, 1, 1);
    private Point translation = Vec.ZERO;

    private int interpolationDelay = 0;
    private int transformationInterpolationDuration = 0;
    private int posRotInterpolationDuration = 0;

    private int brightnessOverride = -1;
    private float viewRange = 1.0f;
    private float shadowRadius = 0.0f;
    private float shadowStrength = 0.0f;
    private float width = 0.0f;
    private float height = 0.0f;
    private int glowColorOverride = -1;

    public static TextHologramStyle defaults() {
        return new TextHologramStyle();
    }

    public TextHologramStyle copy() {
        TextHologramStyle copy = new TextHologramStyle();
        copy.lineWidth = this.lineWidth;
        copy.backgroundColor = this.backgroundColor;
        copy.textOpacity = this.textOpacity;
        copy.shadow = this.shadow;
        copy.seeThrough = this.seeThrough;
        copy.useDefaultBackground = this.useDefaultBackground;
        copy.alignment = this.alignment;
        copy.billboard = this.billboard;
        copy.scale = this.scale;
        copy.translation = this.translation;
        copy.interpolationDelay = this.interpolationDelay;
        copy.transformationInterpolationDuration = this.transformationInterpolationDuration;
        copy.posRotInterpolationDuration = this.posRotInterpolationDuration;
        copy.brightnessOverride = this.brightnessOverride;
        copy.viewRange = this.viewRange;
        copy.shadowRadius = this.shadowRadius;
        copy.shadowStrength = this.shadowStrength;
        copy.width = this.width;
        copy.height = this.height;
        copy.glowColorOverride = this.glowColorOverride;
        return copy;
    }

    public int lineWidth() { return lineWidth; }
    public int backgroundColor() { return backgroundColor; }
    public byte textOpacity() { return textOpacity; }
    public boolean shadow() { return shadow; }
    public boolean seeThrough() { return seeThrough; }
    public boolean useDefaultBackground() { return useDefaultBackground; }
    public TextDisplayMeta.Alignment alignment() { return alignment; }
    public AbstractDisplayMeta.BillboardConstraints billboard() { return billboard; }
    public Vec scale() { return scale; }
    public Point translation() { return translation; }
    public int interpolationDelay() { return interpolationDelay; }
    public int transformationInterpolationDuration() { return transformationInterpolationDuration; }
    public int posRotInterpolationDuration() { return posRotInterpolationDuration; }
    public int brightnessOverride() { return brightnessOverride; }
    public float viewRange() { return viewRange; }
    public float shadowRadius() { return shadowRadius; }
    public float shadowStrength() { return shadowStrength; }
    public float width() { return width; }
    public float height() { return height; }
    public int glowColorOverride() { return glowColorOverride; }

    public TextHologramStyle lineWidth(int value) { this.lineWidth = value; return this; }
    public TextHologramStyle backgroundColor(int value) { this.backgroundColor = value; return this; }
    public TextHologramStyle textOpacity(byte value) { this.textOpacity = value; return this; }
    public TextHologramStyle shadow(boolean value) { this.shadow = value; return this; }
    public TextHologramStyle seeThrough(boolean value) { this.seeThrough = value; return this; }
    public TextHologramStyle useDefaultBackground(boolean value) { this.useDefaultBackground = value; return this; }
    public TextHologramStyle alignment(TextDisplayMeta.Alignment value) { this.alignment = value; return this; }
    public TextHologramStyle billboard(AbstractDisplayMeta.BillboardConstraints value) { this.billboard = value; return this; }
    public TextHologramStyle scale(Vec value) { this.scale = value; return this; }
    public TextHologramStyle translation(Point value) { this.translation = value; return this; }
    public TextHologramStyle interpolationDelay(int value) { this.interpolationDelay = value; return this; }
    public TextHologramStyle transformationInterpolationDuration(int value) { this.transformationInterpolationDuration = value; return this; }
    public TextHologramStyle posRotInterpolationDuration(int value) { this.posRotInterpolationDuration = value; return this; }
    public TextHologramStyle brightnessOverride(int value) { this.brightnessOverride = value; return this; }
    public TextHologramStyle brightness(int blockLight, int skyLight) {
        this.brightnessOverride = ((blockLight & 0xF) << 4) | ((skyLight & 0xF) << 20);
        return this;
    }
    public TextHologramStyle viewRange(float value) { this.viewRange = value; return this; }
    public TextHologramStyle shadowRadius(float value) { this.shadowRadius = value; return this; }
    public TextHologramStyle shadowStrength(float value) { this.shadowStrength = value; return this; }
    public TextHologramStyle width(float value) { this.width = value; return this; }
    public TextHologramStyle height(float value) { this.height = value; return this; }
    public TextHologramStyle glowColorOverride(int value) { this.glowColorOverride = value; return this; }
}

