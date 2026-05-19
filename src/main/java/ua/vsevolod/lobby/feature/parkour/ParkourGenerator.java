package ua.vsevolod.lobby.feature.parkour;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;

import java.util.concurrent.ThreadLocalRandom;

public final class ParkourGenerator {

    private static final int CHUNK_SAFE_MIN = 2;
    private static final int CHUNK_SAFE_MAX = 13;
    private static final int MAX_BLOCK_Y = 200;
    private static final int MIN_BLOCK_Y = 64;

    private final ParkourTheme theme;
    private final ParkourDifficulty difficulty;
    private int facing = 0;
    private boolean ascending = true;

    public ParkourGenerator(ParkourTheme theme, ParkourDifficulty difficulty) {
        this.theme = theme;
        this.difficulty = difficulty;
    }

    public Point next(Point from, int score) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int dist = difficulty.forwardDistance();

        int chunkBaseX = Math.floorDiv(from.blockX(), 16) * 16;
        int chunkBaseZ = Math.floorDiv(from.blockZ(), 16) * 16;

        for (int attempt = 0; attempt < 4; attempt++) {
            int[] fwd = forwardDelta(facing, dist);
            if (inBounds(from.blockX() + fwd[0] - chunkBaseX,
                         from.blockZ() + fwd[2] - chunkBaseZ)) break;
            facing = (facing + 1) % 4;
        }

        int[] fwd = forwardDelta(facing, dist);

        int currentY = from.blockY();
        if (currentY >= MAX_BLOCK_Y) ascending = false;
        else if (currentY <= MIN_BLOCK_Y) ascending = true;

        int maxH = difficulty.maxHeightDelta();
        int dy;
        if (maxH == 0) {
            dy = 0;
        } else {
            if (ascending) {
                dy = rand.nextBoolean() ? 0 : 1;
            } else {
                dy = rand.nextBoolean() ? 0 : -1;
            }
        }
        int ny = currentY + dy;

        int maxSide = difficulty.maxSideOffset();
        int sideDelta = maxSide == 0 ? 0 : rand.nextInt(2 * maxSide + 1) - maxSide;
        int nx = from.blockX() + fwd[0] + sideDelta * sideX(facing);
        int nz = from.blockZ() + fwd[2] + sideDelta * sideZ(facing);

        if (!inBounds(nx - chunkBaseX, nz - chunkBaseZ)) {
            sideDelta = -sideDelta;
            nx = from.blockX() + fwd[0] + sideDelta * sideX(facing);
            nz = from.blockZ() + fwd[2] + sideDelta * sideZ(facing);
            if (!inBounds(nx - chunkBaseX, nz - chunkBaseZ)) {
                nx = from.blockX() + fwd[0];
                nz = from.blockZ() + fwd[2];
            }
        }

        return new Pos(nx, ny, nz);
    }

    public Block randomBlock() {
        return theme.randomBlock();
    }

    public ParkourTheme theme() {
        return theme;
    }

    private static int[] forwardDelta(int facing, int dist) {
        return switch (facing) {
            case 0 -> new int[]{ 0, 0,  dist};
            case 1 -> new int[]{ dist, 0,  0};
            case 2 -> new int[]{ 0, 0, -dist};
            case 3 -> new int[]{-dist, 0,  0};
            default -> new int[]{ 0, 0,  dist};
        };
    }

    private static int sideX(int facing) {
        return (facing == 0 || facing == 2) ? 1 : 0;
    }

    private static int sideZ(int facing) {
        return (facing == 1 || facing == 3) ? 1 : 0;
    }

    private static boolean inBounds(int localX, int localZ) {
        return localX >= CHUNK_SAFE_MIN && localX <= CHUNK_SAFE_MAX
                && localZ >= CHUNK_SAFE_MIN && localZ <= CHUNK_SAFE_MAX;
    }
}
