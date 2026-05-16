package ua.vsevolod.lobby.feature.parkour;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks the next parkour block. Every pattern in here is reachable with vanilla movement —
 * no slabs, no fence-corners, no slow-falling, no 2-up jumps.
 *
 * <h3>Vanilla jump reference (the box we must stay inside)</h3>
 * <ul>
 *   <li>Sprint flat: up to <b>4 blocks</b> forward (sqrt(dx²+dz²) ≤ 4.0)</li>
 *   <li>Sprint +1 up: up to <b>3 blocks</b> forward (sqrt(dx²+dz²) ≤ 3.0 — strict)</li>
 *   <li>+2 up: <b>impossible</b> from a single-block jump</li>
 *   <li>Drops (negative dy): extend horizontal reach by ~1 per block dropped</li>
 *   <li>Diagonals (|dx| = 2) work flat or with -1 dy; <b>impossible</b> with +1 dy</li>
 *   <li>|dx| ≥ 3 needs the full 4-block straight budget; only safe with dz ≤ 2</li>
 * </ul>
 *
 * <h3>Why this matters</h3>
 * <p>Prior pattern lists contained 2-up stair jumps and 4-forward + 1-up diagonals — both
 * physically impossible without sprint+sneak edge tricks the average player doesn't know.
 * Players hit those and the run silently ended. Every pattern below was checked against
 * the vanilla budget.</p>
 */
public final class ParkourGenerator {

    /** Warm-up. Short flat hops, no height change. */
    private static final List<JumpPattern> EASY = List.of(
            new JumpPattern( 0, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern( 1, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern(-1, 0, 2, ParkourDifficulty.EASY),
            new JumpPattern( 0, 0, 3, ParkourDifficulty.EASY),
            new JumpPattern( 1, 0, 3, ParkourDifficulty.EASY),
            new JumpPattern(-1, 0, 3, ParkourDifficulty.EASY)
    );

    /** Sprint distances and gentle +1 / -1 stairs. */
    private static final List<JumpPattern> MEDIUM = List.of(
            new JumpPattern( 0, 1, 2, ParkourDifficulty.MEDIUM),   // up 1
            new JumpPattern( 1, 1, 2, ParkourDifficulty.MEDIUM),   // up 1, side 1
            new JumpPattern(-1, 1, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern( 0, 1, 3, ParkourDifficulty.MEDIUM),   // sprint+1 up; |dx²+dz²|=9, edge of allowed
            new JumpPattern( 2, 0, 2, ParkourDifficulty.MEDIUM),   // diagonal flat (√8)
            new JumpPattern(-2, 0, 2, ParkourDifficulty.MEDIUM),
            new JumpPattern( 0, -1, 3, ParkourDifficulty.MEDIUM),  // drop 1
            new JumpPattern( 1, -1, 3, ParkourDifficulty.MEDIUM),
            new JumpPattern(-1, -1, 3, ParkourDifficulty.MEDIUM)
    );

    /** Long sprint jumps. Still inside vanilla. */
    private static final List<JumpPattern> HARD = List.of(
            new JumpPattern( 0, 0, 4, ParkourDifficulty.HARD),     // sprint flat max
            new JumpPattern( 1, 0, 3, ParkourDifficulty.HARD),     // diagonal flat (√10)
            new JumpPattern(-1, 0, 3, ParkourDifficulty.HARD),
            new JumpPattern( 2, 0, 3, ParkourDifficulty.HARD),     // diagonal long flat (√13)
            new JumpPattern(-2, 0, 3, ParkourDifficulty.HARD),
            new JumpPattern( 0, -1, 4, ParkourDifficulty.HARD),    // drop 1 + sprint
            new JumpPattern( 1, -1, 4, ParkourDifficulty.HARD),
            new JumpPattern(-1, -1, 4, ParkourDifficulty.HARD),
            new JumpPattern( 2, -1, 3, ParkourDifficulty.HARD),    // drop 1 + diagonal long
            new JumpPattern(-2, -1, 3, ParkourDifficulty.HARD)
    );

    /** Drops, max-reach diagonals — possible but unforgiving. */
    private static final List<JumpPattern> EXTREME = List.of(
            new JumpPattern( 0, -2, 4, ParkourDifficulty.EXTREME), // big drop + sprint
            new JumpPattern( 1, -2, 4, ParkourDifficulty.EXTREME),
            new JumpPattern(-1, -2, 4, ParkourDifficulty.EXTREME),
            new JumpPattern( 2, -2, 3, ParkourDifficulty.EXTREME), // big drop + diagonal long
            new JumpPattern(-2, -2, 3, ParkourDifficulty.EXTREME),
            new JumpPattern( 3, 0, 1, ParkourDifficulty.EXTREME),  // wide side step (√10), no fwd height
            new JumpPattern(-3, 0, 1, ParkourDifficulty.EXTREME)
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
        List<JumpPattern> pool = poolFor(score, random);
        return pool.get(random.nextInt(pool.size()));
    }

    private static List<JumpPattern> poolFor(int score, ThreadLocalRandom random) {
        if (score <= 2) return EASY;
        if (score <= 7)  return random.nextInt(10) < 6 ? EASY : MEDIUM;
        if (score <= 15) {
            int r = random.nextInt(10);
            if (r < 2) return EASY;
            if (r < 8) return MEDIUM;
            return HARD;
        }
        if (score <= 25) {
            int r = random.nextInt(10);
            if (r < 1) return EASY;
            if (r < 6) return MEDIUM;
            return HARD;
        }
        // score >= 26
        int r = random.nextInt(10);
        if (r < 4) return MEDIUM;
        if (r < 8) return HARD;
        return EXTREME;
    }
}
