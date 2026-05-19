package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.world.DimensionType;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourRunResult;

import java.util.ArrayList;
import java.util.List;

public final class ParkourSession {

    private static final TextColor PARKOUR_ORANGE = TextColor.color(0xF1BB58);
    private static final int FALL_THRESHOLD = 3;
    private static final int BLOCKS_AHEAD = 3;
    private static final int BLOCKS_BEHIND = 0;
    // Compact the allBlocks list when this many dead entries accumulate at the front.
    // With BLOCKS_BEHIND=0 the live window is always ~4 entries, so after COMPACT_THRESHOLD
    // steps the list shrinks back to ~4 instead of growing to hundreds.
    private static final int COMPACT_THRESHOLD = 20;

    private final Player player;
    private InstanceContainer instance;
    private RegistryKey<DimensionType> currentDimension;
    private final Pos spawn;
    private ParkourGenerator generator;
    private final ParkourDifficulty difficulty;
    private ParkourTheme theme;
    private final boolean trainingMode;
    private ParkourSoundPreset soundPreset;

    private static final Component HUD_SCORE_LABEL = Component.text("Очки ", PARKOUR_ORANGE);
    private static final Component HUD_SEPARATOR = Component.text("  •  ", NamedTextColor.DARK_GRAY);
    private static final Component HUD_TIME_LABEL = Component.text("Время ", PARKOUR_ORANGE);
    private static final Component HUD_TRAINING = Component.text("  •  ", NamedTextColor.DARK_GRAY)
            .append(Component.text("Тренировка", NamedTextColor.GRAY));

    private final List<Point> allBlocks = new ArrayList<>();
    private int headIndex = 0;
    private int currentIndex = 0;
    private int cachedMinY = Integer.MAX_VALUE;

    private long startTime;
    private int score;
    private int previousBestScore;
    private long previousBestDurationMillis;
    private volatile boolean finished;
    private boolean surpassedPreviousBest;
    volatile boolean dimensionChangeInProgress = false;

    private int lastHudScore = Integer.MIN_VALUE;
    private long lastHudSecond = -1;
    private long lastRecoveryTime;

    public ParkourSession(
            Player player,
            InstanceContainer instance,
            Pos spawn,
            int previousBestScore,
            long previousBestDurationMillis,
            ParkourDifficulty difficulty,
            ParkourTheme theme,
            boolean trainingMode,
            ParkourSoundPreset soundPreset
    ) {
        this.player = player;
        this.instance = instance;
        this.currentDimension = DimensionType.THE_END;
        this.spawn = spawn;
        this.previousBestScore = previousBestScore;
        this.previousBestDurationMillis = previousBestDurationMillis;
        this.difficulty = difficulty;
        this.theme = theme;
        this.trainingMode = trainingMode;
        this.soundPreset = soundPreset;
        this.generator = new ParkourGenerator(theme, difficulty);
    }

    public void placeInitialBlocks() {
        Point startBlock = new Pos(spawn.blockX(), spawn.blockY() - 1, spawn.blockZ());
        allBlocks.add(startBlock);
        currentIndex = 0;
        headIndex = 0;
        cachedMinY = startBlock.blockY();
        instance.setBlock(startBlock.blockX(), startBlock.blockY(), startBlock.blockZ(), Block.STONE);

        for (int i = 0; i < BLOCKS_AHEAD; i++) {
            appendNextBlock();
        }
    }

    public void start() {
        startTime = System.currentTimeMillis();

        player.setAllowFlying(false);
        player.setFlying(false);
        player.getInventory().clear();
        player.addEffect(new Potion(PotionEffect.NIGHT_VISION, (byte) 0, Integer.MAX_VALUE));
        player.getInventory().setItemStack(ParkourSettingsMenu.ITEM_SLOT, ParkourSettingsMenu.createItem());
        player.getInventory().setItemStack(ParkourSettingsMenu.LEAVE_SLOT, ParkourSettingsMenu.createLeaveItem());

        player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Паркур начался!", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));

        Component diffLabel = Component.text("Режим: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(difficulty.displayName(), difficulty.color()));
        if (trainingMode) {
            diffLabel = diffLabel.append(Component.text(" (тренировка, без очков)", NamedTextColor.GRAY));
        }
        player.sendMessage(ParkourService.PARKOUR_TEXT.append(diffLabel));

        if (!trainingMode) {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(bestRecordMessage()));
        }

        playSound(soundPreset.start());
        sendHud();
    }

    public void tick() {
        if (finished || !player.isOnline()) {
            return;
        }

        int lowestVisibleY = lowestVisibleBlockY();
        if (player.getPosition().y() < lowestVisibleY - FALL_THRESHOLD) {
            if (trainingMode) {
                long now = System.currentTimeMillis();
                if (now - lastRecoveryTime > 1000) {
                    recoverFromFall();
                    lastRecoveryTime = now;
                }
                return;
            }
            fail();
            return;
        }

        Point target = getNextTarget();
        if (target != null && playerOverlapsBlock(target)) {
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

    void swapTheme(ParkourTheme newTheme) {
        this.theme = newTheme;
        this.generator = new ParkourGenerator(newTheme, difficulty);
        for (int i = headIndex; i < allBlocks.size(); i++) {
            Point block = allBlocks.get(i);
            instance.setBlock(block.blockX(), block.blockY(), block.blockZ(), newTheme.randomBlock());
        }
    }

    private void recoverFromFall() {
        Point safeBlock = allBlocks.get(currentIndex);
        Pos safePos = new Pos(safeBlock.blockX() + 0.5, safeBlock.blockY() + 1.0, safeBlock.blockZ() + 0.5,
                player.getPosition().yaw(), player.getPosition().pitch());
        player.teleport(safePos);
        playSound(soundPreset.recovery());
    }

    private void onReachBlock() {
        score++;
        currentIndex++;
        appendNextBlock();
        removeOldTrailingBlocks();

        spawnLandingEffect();
        sendProgressMessage();
    }

    private void removeOldTrailingBlocks() {
        int firstKeep = currentIndex - BLOCKS_BEHIND;
        boolean needRecalcMinY = false;
        while (headIndex < firstKeep) {
            Point old = allBlocks.get(headIndex);
            instance.setBlock(old.blockX(), old.blockY(), old.blockZ(), Block.AIR);
            if (old.blockY() <= cachedMinY) needRecalcMinY = true;
            headIndex++;
        }
        if (needRecalcMinY) recalcMinY();

        // Compact: once COMPACT_THRESHOLD dead entries pile up at the front, remove them.
        // The live window is only ~(BLOCKS_AHEAD+1) entries, so this is O(live) not O(total).
        if (headIndex >= COMPACT_THRESHOLD) {
            allBlocks.subList(0, headIndex).clear();
            currentIndex -= headIndex;
            headIndex = 0;
        }
    }

    private void recalcMinY() {
        int minY = Integer.MAX_VALUE;
        for (int i = headIndex; i < allBlocks.size(); i++) {
            int y = allBlocks.get(i).blockY();
            if (y < minY) minY = y;
        }
        cachedMinY = minY;
    }

    private int lowestVisibleBlockY() {
        return cachedMinY;
    }

    private void spawnLandingEffect() {
        Point landed = allBlocks.get(currentIndex);

        Particle particle = switch (theme) {
            case SKY -> Particle.CLOUD;
            case LAVA, NETHER -> Particle.FLAME;
            case FOREST, AUTUMN -> Particle.HAPPY_VILLAGER;
            case VOID, MONOCHROME -> Particle.WITCH;
            case CANDY -> Particle.HEART;
            case OCEAN, SNOW -> Particle.SNOWFLAKE;
            case DESERT -> Particle.WAX_ON;
            case CRYSTAL -> Particle.END_ROD;
        };

        ParticlePacket packet = new ParticlePacket(
                particle, false, false,
                landed.blockX() + 0.5, landed.blockY() + 1.1, landed.blockZ() + 0.5,
                0.25f, 0.05f, 0.25f,
                0.01f, 6
        );
        player.sendPacket(packet);

        playSound(soundPreset.landing(score));
    }

    private void appendNextBlock() {
        Point from = allBlocks.get(allBlocks.size() - 1);
        Point next = generator.next(from, score);

        instance.setBlock(next.blockX(), next.blockY(), next.blockZ(), generator.randomBlock());
        allBlocks.add(next);
        if (next.blockY() < cachedMinY) cachedMinY = next.blockY();
    }

    private Point getNextTarget() {
        int nextIdx = currentIndex + 1;
        if (nextIdx >= allBlocks.size()) return null;
        return allBlocks.get(nextIdx);
    }

    private void fail() {
        finished = true;

        long elapsedMillis = elapsedMillis();

        playSound(soundPreset.fail());

        player.sendMessage(Component.empty());
        player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text("Забег завершен.", NamedTextColor.RED)));
        player.sendMessage(ParkourService.PARKOUR_TEXT.append(Component.text(score + " очк. за " + ParkourTimeFormatter.humanReadable(elapsedMillis), LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));

        if (trainingMode) {
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(
                    Component.text("Тренировочный режим — результат не записан.", NamedTextColor.GRAY)));
            return;
        }

        if (isNewBest(elapsedMillis)) {
            playSound(soundPreset.newRecord());

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
        long currentSecond = elapsedMillis() / 1000L;
        if (score == lastHudScore && currentSecond == lastHudSecond) return;
        lastHudScore = score;
        lastHudSecond = currentSecond;

        var builder = Component.text()
                .append(HUD_SCORE_LABEL)
                .append(Component.text(score, LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                .append(HUD_SEPARATOR)
                .append(HUD_TIME_LABEL)
                .append(Component.text(ParkourTimeFormatter.compact(elapsedMillis()), LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));

        if (trainingMode) builder.append(HUD_TRAINING);

        player.sendActionBar(builder.build());
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isScored() {
        return !trainingMode;
    }

    public boolean isTrainingMode() {
        return trainingMode;
    }

    public ParkourSoundPreset getSoundPreset() {
        return soundPreset;
    }

    public void setSoundPreset(ParkourSoundPreset soundPreset) {
        this.soundPreset = soundPreset;
    }

    public int getScore() {
        return score;
    }

    public int getBestScore() {
        return previousBestScore;
    }

    public ParkourDifficulty getDifficulty() {
        return difficulty;
    }

    public ParkourTheme getTheme() {
        return theme;
    }

    public InstanceContainer getInstance() {
        return instance;
    }

    public RegistryKey<DimensionType> getCurrentDimension() {
        return currentDimension;
    }

    void setCurrentDimension(RegistryKey<DimensionType> dimension) {
        this.currentDimension = dimension;
    }

    void swapInstance(InstanceContainer newInstance, RegistryKey<DimensionType> newDimension) {
        for (int i = headIndex; i < allBlocks.size(); i++) {
            Point block = allBlocks.get(i);
            Block blockType = instance.getBlock(block);
            newInstance.setBlock(block.blockX(), block.blockY(), block.blockZ(), blockType);
        }
        this.instance = newInstance;
        this.currentDimension = newDimension;
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
        if (!surpassedPreviousBest && previousBestScore > 0 && score > previousBestScore && !trainingMode) {
            surpassedPreviousBest = true;
            playSound(soundPreset.surpass());
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

    private void playSound(Sound sound) {
        if (sound != null) player.playSound(sound);
    }

    private static final double HALF_WIDTH = 0.3;

    private boolean playerOverlapsBlock(Point block) {
        double px = player.getPosition().x();
        double py = player.getPosition().y();
        double pz = player.getPosition().z();
        int feetY = (int) Math.floor(py) - 1;
        if (feetY != block.blockY()) return false;
        int minX = (int) Math.floor(px - HALF_WIDTH);
        int maxX = (int) Math.floor(px + HALF_WIDTH);
        int minZ = (int) Math.floor(pz - HALF_WIDTH);
        int maxZ = (int) Math.floor(pz + HALF_WIDTH);
        int bx = block.blockX();
        int bz = block.blockZ();
        return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
    }
}
