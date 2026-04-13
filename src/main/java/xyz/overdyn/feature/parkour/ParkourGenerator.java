package xyz.overdyn.feature.parkour;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ParkourGenerator {

    private static final List<JumpPattern> EASY = List.of(
            new JumpPattern(0, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern(1, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern(-1, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern(0, 1, 1, ParkourDifficulty.EASY),
            new JumpPattern(0, 0, 3, ParkourDifficulty.EASY)
    );

    private static final List<JumpPattern> MEDIUM = List.of(
            new JumpPattern(1, 0, 3, ParkourDifficulty.MEDIUM),
            new JumpPattern(-1, 0, 3, ParkourDifficulty.MEDIUM),
            new JumpPattern(2, 0, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern(-2, 0, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern(0, 1, 2, ParkourDifficulty.MEDIUM)
    );

    private static final List<JumpPattern> HARD = List.of(
            new JumpPattern(1, 1, 3, ParkourDifficulty.HARD),
            new JumpPattern(-1, 1, 3, ParkourDifficulty.HARD),
            new JumpPattern(2, 0, 3, ParkourDifficulty.HARD),
            new JumpPattern(-2, 0, 3, ParkourDifficulty.HARD)
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

    private JumpPattern pickPattern(int score) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<JumpPattern> pool = new ArrayList<>();

        // Волны сложности
        int cycle = score % 7;

        if (score < 5) {
            pool.addAll(EASY);
        } else if (score < 15) {
            if (cycle <= 3) {
                pool.addAll(EASY);
            } else {
                pool.addAll(MEDIUM);
            }
        } else if (score < 30) {
            if (cycle == 0 || cycle == 4) {
                pool.addAll(EASY);
            } else {
                pool.addAll(MEDIUM);
            }
        } else {
            if (cycle == 0 || cycle == 5) {
                pool.addAll(EASY);
            } else if (cycle == 3) {
                pool.addAll(HARD);
            } else {
                pool.addAll(MEDIUM);
            }
        }

        return pool.get(random.nextInt(pool.size()));
    }
}
