package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum ParkourTheme {

    SKY("Небо", "Лёгкие облачные блоки",
            TextColor.color(0x87CEEB), Material.WHITE_WOOL, List.of(
            Block.WHITE_CONCRETE, Block.LIGHT_BLUE_CONCRETE, Block.QUARTZ_BLOCK, Block.WHITE_WOOL
    )),

    LAVA("Лава", "Раскалённые вулканические блоки",
            TextColor.color(0xE07040), Material.MAGMA_BLOCK, List.of(
            Block.RED_CONCRETE, Block.ORANGE_CONCRETE, Block.BLACKSTONE, Block.MAGMA_BLOCK
    )),

    FOREST("Лес", "Природные деревянные блоки",
            TextColor.color(0x5B8C3E), Material.OAK_LOG, List.of(
            Block.MOSS_BLOCK, Block.OAK_PLANKS, Block.OAK_LOG, Block.GREEN_CONCRETE
    )),

    VOID("Пустота", "Тёмные мистические блоки",
            TextColor.color(0x9B59B6), Material.OBSIDIAN, List.of(
            Block.OBSIDIAN, Block.PURPLE_CONCRETE, Block.AMETHYST_BLOCK, Block.BLACK_CONCRETE
    )),

    CANDY("Карамель", "Яркие сладкие блоки",
            TextColor.color(0xE87BA4), Material.PINK_WOOL, List.of(
            Block.PINK_CONCRETE, Block.YELLOW_CONCRETE, Block.LIME_CONCRETE, Block.WHITE_WOOL
    )),

    OCEAN("Океан", "Морские глубоководные блоки",
            TextColor.color(0x1E90FF), Material.PRISMARINE, List.of(
            Block.PRISMARINE, Block.DARK_PRISMARINE, Block.CYAN_CONCRETE, Block.BLUE_CONCRETE
    )),

    DESERT("Пустыня", "Песчаные пустынные блоки",
            TextColor.color(0xD2B48C), Material.SANDSTONE, List.of(
            Block.SANDSTONE, Block.SMOOTH_SANDSTONE, Block.ORANGE_TERRACOTTA, Block.YELLOW_CONCRETE
    )),

    SNOW("Снег", "Морозные ледяные блоки",
            TextColor.color(0xCCDDEE), Material.SNOWBALL, List.of(
            Block.SNOW_BLOCK, Block.PACKED_ICE, Block.BLUE_ICE, Block.WHITE_CONCRETE
    )),

    NETHER("Незер", "Адские пылающие блоки",
            TextColor.color(0x8B0000), Material.NETHER_BRICKS, List.of(
            Block.NETHER_BRICKS, Block.CRIMSON_PLANKS, Block.WARPED_PLANKS, Block.RED_NETHER_BRICKS
    )),

    CRYSTAL("Кристалл", "Блестящие кристальные блоки",
            TextColor.color(0xC8A2FF), Material.AMETHYST_SHARD, List.of(
            Block.AMETHYST_BLOCK, Block.PURPLE_STAINED_GLASS, Block.GLASS, Block.PRISMARINE_BRICKS
    )),

    AUTUMN("Осень", "Тёплые осенние блоки",
            TextColor.color(0xD2691E), Material.ORANGE_DYE, List.of(
            Block.ORANGE_CONCRETE, Block.RED_CONCRETE, Block.BROWN_CONCRETE, Block.YELLOW_CONCRETE
    )),

    MONOCHROME("Монохром", "Строгие чёрно-белые блоки",
            TextColor.color(0xBBBBBB), Material.GRAY_WOOL, List.of(
            Block.WHITE_CONCRETE, Block.LIGHT_GRAY_CONCRETE, Block.GRAY_CONCRETE, Block.BLACK_CONCRETE
    ));

    private final String displayName;
    private final String description;
    private final TextColor color;
    private final Material material;
    private final List<Block> palette;

    ParkourTheme(String displayName, String description, TextColor color, Material material, List<Block> palette) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.material = material;
        this.palette = palette;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public TextColor color() { return color; }
    public Material material() { return material; }

    public Block randomBlock() {
        return palette.get(ThreadLocalRandom.current().nextInt(palette.size()));
    }

    public static ParkourTheme randomTheme() {
        ParkourTheme[] values = values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
