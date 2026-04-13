package xyz.overdyn.feature.parkour;

import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum ParkourTheme {

    SKY("Небо", List.of(
            Block.WHITE_CONCRETE,
            Block.LIGHT_BLUE_CONCRETE,
            Block.QUARTZ_BLOCK,
            Block.WHITE_WOOL
    )),

    LAVA("Лава", List.of(
            Block.RED_CONCRETE,
            Block.ORANGE_CONCRETE,
            Block.BLACKSTONE,
            Block.MAGMA_BLOCK
    )),

    FOREST("Лес", List.of(
            Block.MOSS_BLOCK,
            Block.OAK_PLANKS,
            Block.OAK_LOG,
            Block.GREEN_CONCRETE
    )),

    VOID("Пустота", List.of(
            Block.OBSIDIAN,
            Block.PURPLE_CONCRETE,
            Block.AMETHYST_BLOCK,
            Block.BLACK_CONCRETE
    )),

    CANDY("Карамель", List.of(
            Block.PINK_CONCRETE,
            Block.YELLOW_CONCRETE,
            Block.LIME_CONCRETE,
            Block.WHITE_WOOL
    ));

    private final String displayName;
    private final List<Block> palette;

    ParkourTheme(String displayName, List<Block> palette) {
        this.displayName = displayName;
        this.palette = palette;
    }

    public String displayName() {
        return displayName;
    }

    public Block randomBlock() {
        return palette.get(ThreadLocalRandom.current().nextInt(palette.size()));
    }

    public static ParkourTheme randomTheme() {
        ParkourTheme[] values = values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
