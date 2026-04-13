package xyz.overdyn.feature.lobby.ui.hologram;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.feature.parkour.ParkourTimeFormatter;
import xyz.overdyn.feature.parkour.leaderboard.ParkourLeaderboardEntry;
import xyz.overdyn.feature.parkour.leaderboard.ParkourLeaderboardService;
import xyz.overdyn.util.MinecraftFontMetrics;
import xyz.overdyn.util.Text;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ParkourLeaderboardHologramService {

    // Меняй только это значение, чтобы увеличить или уменьшить весь лидерборд целиком.
    private static final double HOLOGRAM_SCALE = 1.5;

    private static final double ROW_SPACING = scaledDistance(0.24);
    private static final double TITLE_CENTER_OFFSET = scaledHorizontalDistance(2.76);
    private static final double COLUMN_HEADER_OFFSET = ROW_SPACING * 3.05;
    private static final double RANK_COLUMN_OFFSET = scaledHorizontalDistance(0.42);
    private static final double NAME_COLUMN_OFFSET = scaledHorizontalDistance(1.78);
    private static final double SCORE_COLUMN_OFFSET = scaledHorizontalDistance(3.18);
    private static final double TIME_COLUMN_OFFSET = scaledHorizontalDistance(4.46);

    private static final int ENTRY_START_LINE = 1;
    private static final int NAME_COLUMN_WIDTH = scaledWidth(94);
    private static final int TITLE_LINE_WIDTH = scaledWidth(360);
    private static final int SUBTITLE_LINE_WIDTH = scaledWidth(360);
    private static final int RANK_HEADER_LINE_WIDTH = scaledWidth(60);
    private static final int RANK_ROW_LINE_WIDTH = scaledWidth(28);
    private static final int NAME_LINE_WIDTH = scaledWidth(128);
    private static final int SCORE_LINE_WIDTH = scaledWidth(58);
    private static final int TIME_LINE_WIDTH = scaledWidth(82);

    private final ParkourLeaderboardService leaderboardService;
    private final TextHologram titleHologram;
    private final TextHologram rankColumnHologram;
    private final TextHologram nameColumnHologram;
    private final TextHologram scoreColumnHologram;
    private final TextHologram timeColumnHologram;
    private final List<TextHologram> holograms;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    public ParkourLeaderboardHologramService(ParkourLeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
        this.titleHologram = createTitleHologram();
        this.rankColumnHologram = createRankColumn();
        this.nameColumnHologram = createNameColumn();
        this.scoreColumnHologram = createScoreColumn();
        this.timeColumnHologram = createTimeColumn();
        this.holograms = List.of(
                titleHologram,
                rankColumnHologram,
                nameColumnHologram,
                scoreColumnHologram,
                timeColumnHologram
        );

        this.leaderboardService.addChangeListener(this::refreshAll);
    }

    public void showTo(Player player) {
        if (viewers.add(player.getUuid())) {
            holograms.forEach(hologram -> hologram.show(player));
        }

        refreshFor(List.of(player));
    }

    public void hideFrom(Player player) {
        if (viewers.remove(player.getUuid())) {
            holograms.forEach(hologram -> hologram.hide(player));
        }
    }

    private TextHologram createTitleHologram() {
        return new TextHologramBuilder(offsetOnBoard(TITLE_CENTER_OFFSET, 0.0))
                .spacing(ROW_SPACING)
                .line(Text.c("&6&lПАРКУР"), titleStyle())
                .line(Text.c("&7Лидеры по очкам и времени"), subtitleStyle())
                .build();
    }

    private TextHologram createRankColumn() {
        TextHologramBuilder builder = new TextHologramBuilder(offsetOnBoard(RANK_COLUMN_OFFSET, COLUMN_HEADER_OFFSET))
                .spacing(ROW_SPACING)
                .line(Text.c("&8Место"), columnHeaderStyle(TextDisplayMeta.Alignment.RIGHT, RANK_HEADER_LINE_WIDTH));

        for (int rank = 1; rank <= LobbyConfig.Parkour.LEADERBOARD_MAX_ENTRIES; rank++) {
            builder.line(rankComponent(rank, null), rankRowStyle());
        }

        return builder.build();
    }

    private TextHologram createNameColumn() {
        TextHologramBuilder builder = new TextHologramBuilder(offsetOnBoard(NAME_COLUMN_OFFSET, COLUMN_HEADER_OFFSET))
                .spacing(ROW_SPACING)
                .line(nameHeaderComponent(), columnHeaderStyle(TextDisplayMeta.Alignment.RIGHT, NAME_LINE_WIDTH));

        for (int rank = 1; rank <= LobbyConfig.Parkour.LEADERBOARD_MAX_ENTRIES; rank++) {
            builder.line(nameComponent(rank, null), nameRowStyle());
        }

        return builder.build();
    }

    private TextHologram createScoreColumn() {
        TextHologramBuilder builder = new TextHologramBuilder(offsetOnBoard(SCORE_COLUMN_OFFSET, COLUMN_HEADER_OFFSET))
                .spacing(ROW_SPACING)
                .line(Text.c("&#FFB347Очки"), columnHeaderStyle(TextDisplayMeta.Alignment.RIGHT, SCORE_LINE_WIDTH));

        for (int rank = 1; rank <= LobbyConfig.Parkour.LEADERBOARD_MAX_ENTRIES; rank++) {
            builder.line(scoreComponent(rank, null), scoreRowStyle());
        }

        return builder.build();
    }

    private TextHologram createTimeColumn() {
        TextHologramBuilder builder = new TextHologramBuilder(offsetOnBoard(TIME_COLUMN_OFFSET, COLUMN_HEADER_OFFSET))
                .spacing(ROW_SPACING)
                .line(Text.c("&#7DE3FFВремя"), columnHeaderStyle(TextDisplayMeta.Alignment.RIGHT, TIME_LINE_WIDTH));

        for (int rank = 1; rank <= LobbyConfig.Parkour.LEADERBOARD_MAX_ENTRIES; rank++) {
            builder.line(timeComponent(rank, null), timeRowStyle());
        }

        return builder.build();
    }

    private void refreshAll() {
        Collection<Player> players = currentViewers();
        if (players.isEmpty()) {
            return;
        }

        refreshFor(players);
    }

    private void refreshFor(Collection<Player> players) {
        List<ParkourLeaderboardEntry> entries = leaderboardService.topEntries(LobbyConfig.Parkour.LEADERBOARD_MAX_ENTRIES);

        for (int index = 0; index < LobbyConfig.Parkour.LEADERBOARD_MAX_ENTRIES; index++) {
            int rank = index + 1;
            ParkourLeaderboardEntry entry = index < entries.size() ? entries.get(index) : null;

            rankColumnHologram.updateLineTextAll(players, ENTRY_START_LINE + index, rankComponent(rank, entry));
            nameColumnHologram.updateLineTextAll(players, ENTRY_START_LINE + index, nameComponent(rank, entry));
            scoreColumnHologram.updateLineTextAll(players, ENTRY_START_LINE + index, scoreComponent(rank, entry));
            timeColumnHologram.updateLineTextAll(players, ENTRY_START_LINE + index, timeComponent(rank, entry));
        }
    }

    private Collection<Player> currentViewers() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(player -> viewers.contains(player.getUuid()))
                .toList();
    }

    private Component rankComponent(int rank, ParkourLeaderboardEntry entry) {
        String color = entry == null ? "&8" : rowColor(rank);
        return Text.c(color + String.format("%2d.", rank));
    }

    private Component nameHeaderComponent() {
        return Text.c("&7" + MinecraftFontMetrics.padStartToWidth("Игрок", NAME_COLUMN_WIDTH));
    }

    private Component nameComponent(int rank, ParkourLeaderboardEntry entry) {
        if (entry == null) {
            return Text.c("&8" + MinecraftFontMetrics.padStartToWidth("---", NAME_COLUMN_WIDTH));
        }

        String trimmedName = MinecraftFontMetrics.trimToWidth(entry.playerName(), NAME_COLUMN_WIDTH, "...");
        String alignedName = MinecraftFontMetrics.padStartToWidth(trimmedName, NAME_COLUMN_WIDTH);
        return Text.c(rowColor(rank) + alignedName);
    }

    private Component scoreComponent(int rank, ParkourLeaderboardEntry entry) {
        if (entry == null) {
            return Text.c("&8" + String.format("%4s", "--"));
        }

        return Text.c(rowColor(rank) + String.format("%4d", entry.score()));
    }

    private Component timeComponent(int rank, ParkourLeaderboardEntry entry) {
        if (entry == null) {
            return Text.c("&8--:--.--");
        }

        return Text.c(rowColor(rank) + ParkourTimeFormatter.leaderboard(entry.durationMillis()));
    }

    private String rowColor(int rank) {
        return switch (rank) {
            case 1 -> "&#F7C948";
            case 2 -> "&#D7E1EC";
            case 3 -> "&#C98855";
            case 4 -> "&#90E07A";
            case 5 -> "&#7DE3FF";
            case 6 -> "&#B39DFF";
            case 7 -> "&#FF9EC4";
            case 8 -> "&#FFB347";
            case 9 -> "&#78E6C7";
            case 10 -> "&#D3D8E2";
            default -> "&7";
        };
    }

    private Pos offsetOnBoard(double rightOffset, double downOffset) {
        Pos base = LobbyConfig.Parkour.LEADERBOARD_HOLOGRAM_POS;
        double yawRadians = Math.toRadians(base.yaw());
        double rightX = Math.cos(yawRadians);
        double rightZ = Math.sin(yawRadians);

        return new Pos(
                base.x() + rightX * rightOffset,
                base.y() - downOffset,
                base.z() + rightZ * rightOffset,
                base.yaw(),
                base.pitch()
        );
    }

    private TextHologramStyle titleStyle() {
        return TextHologramStyle.defaults()
                .billboard(AbstractDisplayMeta.BillboardConstraints.FIXED)
                .alignment(TextDisplayMeta.Alignment.CENTER)
                .scale(scaledVec(0.95))
                .shadow(false)
                .seeThrough(false)
                .lineWidth(TITLE_LINE_WIDTH)
                .brightness(15, 15);
    }

    private TextHologramStyle subtitleStyle() {
        return TextHologramStyle.defaults()
                .billboard(AbstractDisplayMeta.BillboardConstraints.FIXED)
                .alignment(TextDisplayMeta.Alignment.CENTER)
                .scale(scaledVec(0.78))
                .shadow(false)
                .seeThrough(false)
                .lineWidth(SUBTITLE_LINE_WIDTH)
                .brightness(15, 15);
    }

    private TextHologramStyle columnHeaderStyle(TextDisplayMeta.Alignment alignment, int lineWidth) {
        return TextHologramStyle.defaults()
                .billboard(AbstractDisplayMeta.BillboardConstraints.FIXED)
                .alignment(alignment)
                .scale(scaledVec(0.74))
                .shadow(false)
                .seeThrough(false)
                .lineWidth(lineWidth)
                .brightness(15, 15);
    }

    private TextHologramStyle rankRowStyle() {
        return columnRowStyle(TextDisplayMeta.Alignment.RIGHT, RANK_ROW_LINE_WIDTH);
    }

    private TextHologramStyle nameRowStyle() {
        return columnRowStyle(TextDisplayMeta.Alignment.RIGHT, NAME_LINE_WIDTH);
    }

    private TextHologramStyle scoreRowStyle() {
        return columnRowStyle(TextDisplayMeta.Alignment.RIGHT, SCORE_LINE_WIDTH);
    }

    private TextHologramStyle timeRowStyle() {
        return columnRowStyle(TextDisplayMeta.Alignment.RIGHT, TIME_LINE_WIDTH);
    }

    private TextHologramStyle columnRowStyle(TextDisplayMeta.Alignment alignment, int lineWidth) {
        return TextHologramStyle.defaults()
                .billboard(AbstractDisplayMeta.BillboardConstraints.FIXED)
                .alignment(alignment)
                .scale(scaledVec(0.74))
                .shadow(false)
                .seeThrough(false)
                .lineWidth(lineWidth)
                .brightness(15, 15);
    }

    private static double scaledDistance(double value) {
        return value * HOLOGRAM_SCALE;
    }

    private static double scaledHorizontalDistance(double value) {
        if (HOLOGRAM_SCALE <= 1.0) {
            return value * HOLOGRAM_SCALE;
        }

        double spacingScale = HOLOGRAM_SCALE + ((HOLOGRAM_SCALE - 1.0) * 0.20);
        return value * spacingScale;
    }

    private static int scaledWidth(int value) {
        return Math.max(1, (int) Math.round(value * effectiveWidthScale()));
    }

    private static Vec scaledVec(double value) {
        double scaled = value * HOLOGRAM_SCALE;
        return new Vec(scaled, scaled, scaled);
    }

    private static double effectiveWidthScale() {
        if (HOLOGRAM_SCALE <= 1.0) {
            return HOLOGRAM_SCALE;
        }

        return 1.0 + ((HOLOGRAM_SCALE - 1.0) * 0.35);
    }
}
