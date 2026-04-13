package xyz.overdyn.feature.parkour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.feature.parkour.leaderboard.ParkourRunResult;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ParkourSession {

    private static final TextColor PARKOUR_ORANGE = TextColor.color(0xF1BB58);

    private final Player player;
    private final InstanceContainer instance;
    private final Pos spawn;
    private final ParkourGenerator generator;

    private final Deque<Point> activeBlocks = new ArrayDeque<>();

    private long startTime;
    private int score;
    private int previousBestScore;
    private long previousBestDurationMillis;
    private boolean finished;
    private boolean surpassedPreviousBest;

    public ParkourSession(
            Player player,
            InstanceContainer instance,
            Pos spawn,
            int previousBestScore,
            long previousBestDurationMillis
    ) {
        this.player = player;
        this.instance = instance;
        this.spawn = spawn;
        this.previousBestScore = previousBestScore;
        this.previousBestDurationMillis = previousBestDurationMillis;
        this.generator = new ParkourGenerator(ParkourTheme.randomTheme());
    }

    public void start() {
        startTime = System.currentTimeMillis();

        Point startBlock = new Pos(spawn.blockX(), spawn.blockY() - 1, spawn.blockZ());
        activeBlocks.add(startBlock);
        instance.setBlock(startBlock.blockX(), startBlock.blockY(), startBlock.blockZ(), Block.STONE);

        for (int i = 0; i < LobbyConfig.Parkour.MAX_VISIBLE_BLOCKS - 1; i++) {
            appendNextBlock();
        }

        player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Паркур начался!", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        player.sendMessage(ParkourService.PARKOUR_TEXT.append(bestRecordMessage()));
        sendHud();
    }

    public void tick() {
        if (finished || !player.isOnline()) {
            return;
        }

        if (player.getPosition().y() < LobbyConfig.Parkour.FAIL_Y) {
            fail();
            return;
        }

        Point under = new Pos(
                player.getPosition().blockX(),
                player.getPosition().blockY() - 1,
                player.getPosition().blockZ()
        );

        Point target = getNextTarget();
        if (target != null && sameBlock(under, target)) {
            onReachBlock();
        }

        sendHud();
    }

    public ParkourRunResult toRunResult() {
        return new ParkourRunResult(
                player.getUuid(),
                player.getUsername(),
                score,
                elapsedMillis(),
                System.currentTimeMillis()
        );
    }

    private void onReachBlock() {
        score++;
        appendNextBlock();

        while (activeBlocks.size() > LobbyConfig.Parkour.MAX_VISIBLE_BLOCKS) {
            Point old = activeBlocks.removeFirst();
            instance.setBlock(old.blockX(), old.blockY(), old.blockZ(), Block.AIR);
        }

        sendProgressMessage();
    }

    private void appendNextBlock() {
        Point from = activeBlocks.getLast();
        Point next = generator.next(from, score);

        instance.setBlock(next.blockX(), next.blockY(), next.blockZ(), generator.randomBlock());
        activeBlocks.addLast(next);
    }

    private Point getNextTarget() {
        if (activeBlocks.size() < 2) {
            return null;
        }
        return activeBlocks.stream().skip(1).findFirst().orElse(null);
    }

    private void fail() {
        finished = true;

        long elapsedMillis = elapsedMillis();

        player.sendMessage(Component.empty());
        player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Забег завершен.", NamedTextColor.RED)));
        player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text(score + " очк. за " + ParkourTimeFormatter.humanReadable(elapsedMillis), LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));

        if (isNewBest(elapsedMillis)) {
            if (score > previousBestScore) {
                int diff = score - previousBestScore;
                player.sendMessage(ParkourService.PARKOUR_TEXT.append(
                        Component.text("Новый личный рекорд", PARKOUR_ORANGE)
                                .append(Component.text(" +" + diff + " к прошлому результату.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                ));
            } else {
                long diffMillis = previousBestDurationMillis - elapsedMillis;
                player.sendMessage(ParkourService.PARKOUR_TEXT.append(
                        Component.text("Новый личный рекорд по времени", PARKOUR_ORANGE)
                                .append(Component.text(" быстрее на " + ParkourTimeFormatter.humanReadable(diffMillis) + ".", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                ));
            }

            previousBestScore = score;
            previousBestDurationMillis = elapsedMillis;
        } else if (score < previousBestScore) {
            int diff = previousBestScore - score;
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(
                    Component.text("До личного рекорда не хватило ", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)
                            .append(Component.text(diff + " очк.", PARKOUR_ORANGE))
                            .append(Component.text(".", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
            ));
        } else {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Ты повторил свой лучший результат.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        }
    }

    private void sendHud() {
        Component hud = Component.text()
                .append(Component.text("Очки ", PARKOUR_ORANGE))
                .append(Component.text(score, LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Время ", PARKOUR_ORANGE))
                .append(Component.text(ParkourTimeFormatter.compact(elapsedMillis()), LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                .build();

        player.sendActionBar(hud);
    }

    public boolean isFinished() {
        return finished;
    }

    public int getBestScore() {
        return previousBestScore;
    }

    public InstanceContainer getInstance() {
        return instance;
    }

    private boolean isNewBest(long elapsedMillis) {
        if (score == 0 && previousBestScore == 0 && previousBestDurationMillis == Long.MAX_VALUE) {
            return false;
        }

        if (score != previousBestScore) {
            return score > previousBestScore;
        }

        return elapsedMillis < previousBestDurationMillis;
    }

    private long elapsedMillis() {
        return System.currentTimeMillis() - startTime;
    }

    private Component bestRecordMessage() {
        if (!hasPreviousBest()) {
            return Component.text("Пока без рекорда. Посмотрим, что получится с этой попытки.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL);
        }

        return Component.text()
                .append(Component.text("Личный рекорд: ", PARKOUR_ORANGE))
                .append(Component.text(previousBestScore + " очк.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                .append(Component.text(" за ", NamedTextColor.DARK_GRAY))
                .append(Component.text(ParkourTimeFormatter.humanReadable(previousBestDurationMillis), LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                .build();
    }

    private boolean hasPreviousBest() {
        return previousBestScore > 0 || previousBestDurationMillis != Long.MAX_VALUE;
    }

    private void sendProgressMessage() {
        if (!surpassedPreviousBest && previousBestScore > 0 && score > previousBestScore) {
            surpassedPreviousBest = true;
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(
                    Component.text("Ты уже превзошел свой лучший забег.", PARKOUR_ORANGE)
            ));
            return;
        }

        if (score == 5) {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Хорошее начало. Уже 5 очков.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        } else if (score == 10) {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Темп отличный. Уже 10 очков.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        } else if (score == 15) {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Это уже выше, чем у многих игроков.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        } else if (score == 25) {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Очень сильно. 25 очков держатся далеко не все.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        } else if (score == 50) {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Монстр паркура. 50 очков — это мощно.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        }
    }

    private boolean sameBlock(Point a, Point b) {
        return a.blockX() == b.blockX()
                && a.blockY() == b.blockY()
                && a.blockZ() == b.blockZ();
    }
}
