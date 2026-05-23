package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;

public enum ParkourSoundPreset {

    OFF("Выключены", "Без звуков паркура.", TextColor.color(0xE05555), Material.BARRIER),
    STANDARD("Стандартные", "Классические звуки.", TextColor.color(0xAE3AF3), Material.NOTE_BLOCK),
    SOFT("Мягкие", "Тихие, спокойные.", TextColor.color(0x87CEEB), Material.AMETHYST_SHARD),
    RETRO("Ретро", "Звуки нот-блоков.", TextColor.color(0x8EB126), Material.JUKEBOX);

    private final String displayName;
    private final String description;
    private final TextColor color;
    private final Material material;

    ParkourSoundPreset(String displayName, String description, TextColor color, Material material) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.material = material;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public TextColor color() { return color; }
    public Material material() { return material; }

    public Sound landing(int score) {
        float pitch = Math.min(0.8f + score * 0.02f, 2.0f);
        return switch (this) {
            case OFF -> null;
            case STANDARD -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_HARP, Sound.Source.MASTER, 0.5f, pitch);
            case SOFT -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_HARP, Sound.Source.MASTER, 0.15f, pitch);
            case RETRO -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BIT, Sound.Source.MASTER, 0.5f, pitch);
        };
    }

    public Sound start() {
        return switch (this) {
            case OFF -> null;
            case STANDARD -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 1.5f);
            case SOFT -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 0.3f, 1.5f);
            case RETRO -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2.0f);
        };
    }

    public Sound fail() {
        return switch (this) {
            case OFF -> null;
            case STANDARD -> Sound.sound(SoundEvent.ENTITY_VILLAGER_NO, Sound.Source.MASTER, 1f, 1f);
            case SOFT -> Sound.sound(SoundEvent.ENTITY_VILLAGER_NO, Sound.Source.MASTER, 0.3f, 1f);
            case RETRO -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BASS, Sound.Source.MASTER, 1f, 0.5f);
        };
    }

    public Sound newRecord() {
        return switch (this) {
            case OFF -> null;
            case STANDARD -> Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 1.2f);
            case SOFT -> Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 0.3f, 1.2f);
            case RETRO -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BELL, Sound.Source.MASTER, 1f, 2.0f);
        };
    }

    public Sound surpass() {
        return switch (this) {
            case OFF -> null;
            case STANDARD -> Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 1f, 1.5f);
            case SOFT -> Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 0.3f, 1.5f);
            case RETRO -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_CHIME, Sound.Source.MASTER, 1f, 1.5f);
        };
    }

    public Sound recovery() {
        return switch (this) {
            case OFF -> null;
            case STANDARD -> Sound.sound(SoundEvent.BLOCK_GLASS_BREAK, Sound.Source.MASTER, 0.7f, 1.2f);
            case SOFT -> Sound.sound(SoundEvent.BLOCK_GLASS_BREAK, Sound.Source.MASTER, 0.2f, 1.2f);
            case RETRO -> Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_SNARE, Sound.Source.MASTER, 0.7f, 1.2f);
        };
    }

    public ParkourSoundPreset next() {
        ParkourSoundPreset[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
