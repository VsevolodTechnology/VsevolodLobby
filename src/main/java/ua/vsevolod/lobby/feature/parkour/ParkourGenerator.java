package ua.vsevolod.lobby.feature.parkour;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ParkourGenerator {

    /** Простые прыжки — стартовая зона. dz≤3, без подъёма. */
    private static final List<JumpPattern> EASY = List.of(
            new JumpPattern(0, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern(1, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern(-1, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern(0, 0, 3, ParkourDifficulty.EASY),
            new JumpPattern(1, 0, 3, ParkourDifficulty.EASY),
            new JumpPattern(-1, 0, 3, ParkourDifficulty.EASY)
    );

    /** Средние — sprint-длинные, боковые на 2 блока, лёгкие подъёмы. */
    private static final List<JumpPattern> MEDIUM = List.of(
            new JumpPattern(2, 0, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern(-2, 0, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern(2, 0, 3, ParkourDifficulty.MEDIUM),
            new JumpPattern(-2, 0, 3, ParkourDifficulty.MEDIUM),
            new JumpPattern(0, 1, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern(1, 1, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern(-1, 1, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern(0, -1, 3, ParkourDifficulty.MEDIUM),
            new JumpPattern(1, -1, 3, ParkourDifficulty.MEDIUM)
    );

    /** Сложные — длинные диагональные, подъём с боком, 4-блочные. */
    private static final List<JumpPattern> HARD = List.of(
            new JumpPattern(1, 1, 3, ParkourDifficulty.HARD),
            new JumpPattern(-1, 1, 3, ParkourDifficulty.HARD),
            new JumpPattern(2, 0, 3, ParkourDifficulty.HARD),
            new JumpPattern(-2, 0, 3, ParkourDifficulty.HARD),
            new JumpPattern(0, 0, 4, ParkourDifficulty.HARD),     // sprint-momentum (4-block flat)
            new JumpPattern(1, -1, 4, ParkourDifficulty.HARD),    // drop-long
            new JumpPattern(-1, -1, 4, ParkourDifficulty.HARD),
            new JumpPattern(2, 1, 2, ParkourDifficulty.HARD),     // sprint side+up
            new JumpPattern(-2, 1, 2, ParkourDifficulty.HARD)
    );

    /** Экстрим — выпрыжки наверх, длинные ladder-up, тройные. */
    private static final List<JumpPattern> EXTREME = List.of(
            new JumpPattern(1, 1, 4, ParkourDifficulty.EXTREME),    // long diag-up
            new JumpPattern(-1, 1, 4, ParkourDifficulty.EXTREME),
            new JumpPattern(0, 2, 2, ParkourDifficulty.EXTREME),    // 2-block stair-up
            new JumpPattern(1, 2, 2, ParkourDifficulty.EXTREME),
            new JumpPattern(-1, 2, 2, ParkourDifficulty.EXTREME),
            new JumpPattern(2, 1, 3, ParkourDifficulty.EXTREME),    // sprint diagonal up
            new JumpPattern(-2, 1, 3, ParkourDifficulty.EXTREME),
            new JumpPattern(3, 0, 2, ParkourDifficulty.EXTREME),    // wide side jump
            new JumpPattern(-3, 0, 2, ParkourDifficulty.EXTREME)
    );

    private final ParkourTheme theme;

    public ParkourGenerator(ParkourTheme theme) {
        this.theme = theme;
    }

    public Point next(Point from, int score) {
        JumpPattern pattern = pickPattern(score);

        return new Pos(
                from.blockX() + pattern.dx(),
                Math.max(64, from.blockY() + pattern.dy()),
                from.blockZ() + pattern.dz()
        );
    }

    public Block randomBlock() {
        return theme.randomBlock();
    }

    public ParkourTheme theme() {
        return theme;
    }

    /**
     * Selects a jump pool based on score so the run ramps up:
     *
     * <pre>
     *   score 0-2  : 100% EASY                     — warm-up
     *   score 3-7  : EASY + MEDIUM (60/40)         — start mixing
     *   score 8-15 : MEDIUM majority + EASY + HARD — ramp
     *   score 16-25: MEDIUM + HARD                 — real difficulty
     *   score 26+  : HARD majority + EXTREME       — endgame
     * </pre>
     */
    private JumpPattern pickPattern(int score) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<JumpPattern> pool = poolFor(score, random);
        return pool.get(random.nextInt(pool.size()));
    }

    private static List<JumpPattern> poolFor(int score, ThreadLocalRandom random) {
        if (score <= 2) {
            return EASY;
        }
        if (score <= 7) {
            return random.nextInt(10) < 6 ? EASY : MEDIUM;
        }
        if (score <= 15) {
            int r = random.nextInt(10);
            if (r < 2) return EASY;
            if (r < 8) return MEDIUM;
            return HARD;
        }
        if (score <= 25) {
            int r = random.nextInt(10);
            if (r < 1) return EASY;       // tiny "breather"
            if (r < 6) return MEDIUM;
            return HARD;
        }
        // score >= 26 — endgame
        int r = random.nextInt(10);
        if (r < 4) return MEDIUM;        // breather still possible
        if (r < 8) return HARD;
        return EXTREME;
    }
}
