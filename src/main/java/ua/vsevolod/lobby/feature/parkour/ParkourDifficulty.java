package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.text.format.TextColor;

public enum ParkourDifficulty {

    CHILL("Расслабленный", "Короткие прыжки по прямой.", TextColor.color(0x7EC8E3), 2, 0, 0),
    NORMAL("Обычный", "Стандартные прыжки в 3 блока.", TextColor.color(0x8EB126), 3, 0, 1),
    HARD("Сложный", "Прыжки с боковым смещением.", TextColor.color(0xF1BB58), 3, 1, 1),
    EXTREME("Экстрим", "Дальние прыжки с отклонениями.", TextColor.color(0xE05555), 4, 1, 1);

    private final String displayName;
    private final String description;
    private final TextColor color;
    private final int forwardDistance;
    private final int maxSideOffset;
    private final int maxHeightDelta;

    ParkourDifficulty(String displayName, String description, TextColor color,
                      int forwardDistance, int maxSideOffset, int maxHeightDelta) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.forwardDistance = forwardDistance;
        this.maxSideOffset = maxSideOffset;
        this.maxHeightDelta = maxHeightDelta;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public TextColor color() { return color; }
    public int forwardDistance() { return forwardDistance; }
    public int maxSideOffset() { return maxSideOffset; }
    public int maxHeightDelta() { return maxHeightDelta; }
}
