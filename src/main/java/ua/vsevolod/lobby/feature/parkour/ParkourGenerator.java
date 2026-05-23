package ua.vsevolod.lobby.feature.parkour;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Procedural parkour-path generator. Every emitted block is guaranteed to be reachable from
 * the previous one with a vanilla sprint-jump, lives in its own column (no stacking), and is
 * not crammed against any other live platform.
 *
 * <h3>Why up-jumps need a relaxed floor</h3>
 * Vanilla sprint-jump reach drops to ≈3 blocks when going {@code dy=+1}. Higher tiers
 * configure {@code distMin ≥ 3.0} for big flat hops — applying that floor blindly would make
 * up-jumps unsamplable, and the path would degenerate into "flat or down" forever. Up-jumps
 * therefore use a per-tier {@code upDistFloor} that scales with the difficulty so up-jumps
 * stay representative without becoming impossible to sample.
 */
public final class ParkourGenerator {

    private static final int MIN_BLOCK_Y = 64;
    private static final int MAX_BLOCK_Y = 200;
    private static final int ZONE_RADIUS = 29;
    private static final int EDGE_BAND = 8;
    private static final int MAX_ATTEMPTS = 48;

    // Sprint-jump horizontal reach by dy (block-centre distance, vanilla max).
    // LEVEL=5.0 corresponds to the well-known 4-block-gap sprint-jump — the literal ceiling.
    private static final double REACH_UP     = 3.0;
    private static final double REACH_LEVEL  = 5.0;
    private static final double REACH_DOWN_1 = 5.5;
    private static final double REACH_DROP   = 6.0;

    private record TierParams(
            double distMin,
            double distMax,
            int maxDown,
            double levelW,
            double upW,
            double downW,
            double dropDoubleChance,
            double upDistFloor,            // per-tier floor for dy=+1 samples
            double smallJitterDeg,
            int sharpTurnMin,
            int sharpTurnMax,
            double sharpTurnDegMin,
            double sharpTurnDegMax
    ) {}

    private static TierParams paramsFor(ParkourDifficulty d) {
        return switch (d) {
            // dMin dMax mD  lvl   up    dn    dropDbl upFlr jit sMin sMax sDegMin sDegMax
            case NORMAL ->      new TierParams(2.0, 3.0, 1, 0.45, 0.25, 0.30, 0.00, 2.0, 10.0, 5, 10, 60.0, 100.0);
            case HARD ->        new TierParams(2.5, 4.0, 2, 0.35, 0.30, 0.35, 0.30, 2.0, 15.0, 4,  7, 70.0, 110.0);
            case EXTREME ->     new TierParams(3.0, 4.5, 2, 0.30, 0.30, 0.40, 0.45, 2.3, 20.0, 3,  5, 80.0, 130.0);
            case COMPETITIVE -> new TierParams(3.5, 5.0, 2, 0.30, 0.25, 0.45, 0.55, 2.5, 22.0, 2,  3, 90.0, 140.0);
        };
    }

    private final ParkourTheme theme;
    private final int centerX;
    private final int centerZ;
    private final TierParams params;
    private double angleRad;
    private int stepsToNextSharpTurn;

    public ParkourGenerator(ParkourTheme theme, ParkourDifficulty difficulty, Point spawnBlock) {
        this.theme = theme;
        this.centerX = spawnBlock.blockX();
        this.centerZ = spawnBlock.blockZ();
        this.params = paramsFor(difficulty);
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        this.angleRad = rand.nextDouble() * 2 * Math.PI;
        this.stepsToNextSharpTurn = rand.nextInt(params.sharpTurnMin, params.sharpTurnMax + 1);
    }

    public Point next(Point from, List<Point> recentBlocks) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // 1. small per-step drift for natural curve
        double smallJ = Math.toRadians(params.smallJitterDeg);
        angleRad += (rand.nextDouble() - 0.5) * 2 * smallJ;

        // 2. periodic sharp turn — produces "вперёд-вперёд-влево" variety
        if (--stepsToNextSharpTurn <= 0) {
            double sign = rand.nextBoolean() ? 1.0 : -1.0;
            double mag = Math.toRadians(params.sharpTurnDegMin
                    + rand.nextDouble() * (params.sharpTurnDegMax - params.sharpTurnDegMin));
            angleRad += sign * mag;
            stepsToNextSharpTurn = rand.nextInt(params.sharpTurnMin, params.sharpTurnMax + 1);
        }

        // 3. ease back toward centre if near edge
        double edgePull = edgePullStrength(from);
        if (edgePull > 0.0) {
            angleRad = blendAngle(angleRad, angleToCenter(from), edgePull);
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int dy = pickDy(rand);
            double reachMax = reachMaxFor(dy);
            double dMax = Math.min(params.distMax, reachMax);
            double dMin = (dy >= 1) ? Math.min(params.upDistFloor, params.distMin) : params.distMin;
            if (dMin > dMax) continue;

            double dist = dMin + rand.nextDouble() * (dMax - dMin);

            double extra = Math.toRadians(Math.min(60.0, attempt * 6.0));
            double tryAngle = angleRad + (rand.nextDouble() - 0.5) * 2 * extra;

            int dx = (int) Math.round(Math.cos(tryAngle) * dist);
            int dz = (int) Math.round(Math.sin(tryAngle) * dist);
            if (dx == 0 && dz == 0) continue;

            int nx = from.blockX() + dx;
            int ny = from.blockY() + dy;
            int nz = from.blockZ() + dz;

            if (ny < MIN_BLOCK_Y || ny > MAX_BLOCK_Y) continue;
            if (!inZone(nx, nz)) continue;

            double actualDist = Math.hypot(dx, dz);
            if (!isReachable(actualDist, dy)) continue;

            Point candidate = new Pos(nx, ny, nz);
            if (collides(candidate, recentBlocks)) continue;

            angleRad = tryAngle;
            return candidate;
        }

        return fallback(from, recentBlocks);
    }

    private int pickDy(ThreadLocalRandom rand) {
        double total = params.levelW + params.upW + params.downW;
        double r = rand.nextDouble() * total;
        if (r < params.upW) return 1;
        r -= params.upW;
        if (r < params.levelW) return 0;
        if (params.maxDown >= 2 && rand.nextDouble() < params.dropDoubleChance) return -2;
        return -1;
    }

    private static double reachMaxFor(int dy) {
        if (dy ==  1) return REACH_UP;
        if (dy ==  0) return REACH_LEVEL;
        if (dy == -1) return REACH_DOWN_1;
        return REACH_DROP;
    }

    private static boolean isReachable(double dist, int dy) {
        return dist >= 1.0 && dist <= reachMaxFor(dy);
    }

    private boolean collides(Point cand, List<Point> recentBlocks) {
        int lastIdx = recentBlocks.size() - 1;
        for (int i = 0; i < recentBlocks.size(); i++) {
            Point b = recentBlocks.get(i);
            int dx = cand.blockX() - b.blockX();
            int dy = cand.blockY() - b.blockY();
            int dz = cand.blockZ() - b.blockZ();

            if (dx == 0 && dz == 0 && Math.abs(dy) <= 2) return true;

            if (i != lastIdx && Math.abs(dy) <= 1) {
                int horizSq = dx * dx + dz * dz;
                if (horizSq < 4) return true;
            }
        }
        return false;
    }

    private boolean inZone(int x, int z) {
        return Math.abs(x - centerX) <= ZONE_RADIUS
                && Math.abs(z - centerZ) <= ZONE_RADIUS;
    }

    private double edgePullStrength(Point from) {
        int outer = Math.max(Math.abs(from.blockX() - centerX), Math.abs(from.blockZ() - centerZ));
        int distToEdge = ZONE_RADIUS - outer;
        if (distToEdge >= EDGE_BAND) return 0.0;
        return 0.5 * (1.0 - Math.max(0, distToEdge) / (double) EDGE_BAND);
    }

    private double angleToCenter(Point from) {
        return Math.atan2(centerZ - from.blockZ(), centerX - from.blockX());
    }

    private static double blendAngle(double from, double to, double t) {
        double diff = Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + diff * t;
    }

    private Point fallback(Point from, List<Point> recentBlocks) {
        double[] angles = {angleRad, angleRad + Math.PI / 2, angleRad - Math.PI / 2, angleRad + Math.PI};
        int[] dys = {0, -1, 1};
        for (double a : angles) {
            for (int dy : dys) {
                int dx = (int) Math.round(Math.cos(a) * 2);
                int dz = (int) Math.round(Math.sin(a) * 2);
                if (dx == 0 && dz == 0) continue;
                int nx = from.blockX() + dx;
                int ny = from.blockY() + dy;
                int nz = from.blockZ() + dz;
                if (ny < MIN_BLOCK_Y || ny > MAX_BLOCK_Y) continue;
                if (!inZone(nx, nz)) continue;
                Point cand = new Pos(nx, ny, nz);
                if (collides(cand, recentBlocks)) continue;
                angleRad = a;
                return cand;
            }
        }
        return new Pos(from.blockX() + 2, Math.max(MIN_BLOCK_Y, from.blockY()), from.blockZ());
    }

    public Block randomBlock() {
        return theme.randomBlock();
    }

    public ParkourTheme theme() {
        return theme;
    }
}
