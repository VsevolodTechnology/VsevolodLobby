package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.text.format.TextColor;

/**
 * Difficulty preset shown in the parkour menu. The numeric jump tuning lives inside
 * {@link ParkourGenerator}; this enum only carries UI metadata + the stats-eligibility flag.
 */
public enum ParkourDifficulty {

    NORMAL("Средний",
            "Короткие прыжки с лёгкими спусками.",
            "Статистика и лидерборд не учитываются.",
            TextColor.color(0x8EB126), false),

    HARD("Сложный",
            "Подъёмы вверх, прыжки в сторону.",
            "Статистика и лидерборд не учитываются.",
            TextColor.color(0xAE3AF3), false),

    EXTREME("Экстрим",
            "Глубокие спуски, диагонали, частые повороты.",
            "Статистика и лидерборд не учитываются.",
            TextColor.color(0xE05555), false),

    COMPETITIVE("Соревновательный",
            "Все режимы вперемешку + дальние прыжки.",
            "Идёт в статистику и лидерборд.",
            TextColor.color(0xAE3AF3), true);

    private final String displayName;
    private final String description;
    private final String statsNote;
    private final TextColor color;
    private final boolean countsForStats;

    ParkourDifficulty(String displayName, String description, String statsNote, TextColor color,
                      boolean countsForStats) {
        this.displayName = displayName;
        this.description = description;
        this.statsNote = statsNote;
        this.color = color;
        this.countsForStats = countsForStats;
    }

    public String displayName()     { return displayName; }
    public String description()     { return description; }
    public String statsNote()       { return statsNote; }
    public TextColor color()        { return color; }
    public boolean countsForStats() { return countsForStats; }
}
