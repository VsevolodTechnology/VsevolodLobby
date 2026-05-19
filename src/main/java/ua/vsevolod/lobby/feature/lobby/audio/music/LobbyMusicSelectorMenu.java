package ua.vsevolod.lobby.feature.lobby.audio.music;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.util.Text;

import java.util.List;
import java.util.stream.Stream;

/**
 * 6-row chest menu letting the player pick a specific music track or
 * random shuffle mode. Opened via LMB on the music-disc hotbar item.
 */
public final class LobbyMusicSelectorMenu implements LobbyEventRegistration {

    private static final int RANDOM_SLOT = 4;
    private static final Tag<String> TRACK_KEY_TAG = Tag.String("music-sel-key");
    private static final Tag<String> TRACK_NAME_TAG = Tag.String("music-sel-name");
    private static final String RANDOM_MARKER = "__random__";

    private final LobbyMusicManager musicManager;
    private final Inventory menu;

    public LobbyMusicSelectorMenu(LobbyMusicManager musicManager) {
        this.musicManager = musicManager;
        this.menu = buildMenu();
    }

    public void open(Player player) {
        player.openInventory(menu);
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(InventoryPreClickEvent.class, event -> {
            if (event.getInventory() == null || !event.getInventory().equals(menu)) return;
            event.setCancelled(true);

            ItemStack clicked = event.getClickedItem();
            String key = clicked.getTag(TRACK_KEY_TAG);
            if (key == null) return;

            Player player = event.getPlayer();
            player.closeInventory();

            if (RANDOM_MARKER.equals(key)) {
                musicManager.setEnabled(player, true);
                player.sendMessage(buildPrefix()
                        .append(Component.text("Включён случайный режим", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
            } else {
                String trackName = clicked.getTag(TRACK_NAME_TAG);
                musicManager.playSpecific(player, key);
                player.sendMessage(buildPrefix()
                        .append(Component.text("Сейчас играет: ", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                        .append(Component.text(trackName != null ? trackName : key, TextColor.color(0xF1BB58))));
            }
        });
    }

    // ── Menu construction ───────────────────────────────────────────────────

    private Inventory buildMenu() {
        Inventory inv = new Inventory(InventoryType.CHEST_6_ROW, Text.c("&8Выбор музыки"));

        // Row 0: decoration + random in center
        ItemStack glass = ItemStack.builder(Material.BLACK_STAINED_GLASS_PANE)
                .set(DataComponents.CUSTOM_NAME, Text.c(""))
                .hideExtraTooltip()
                .build();
        for (int i = 0; i < 9; i++) {
            if (i != RANDOM_SLOT) inv.setItemStack(i, glass);
        }

        inv.setItemStack(RANDOM_SLOT, buildRandomItem());

        // Rows 1-5: tracks (45 slots)
        int slot = 9;
        for (MenuEntry entry : TRACKS) {
            inv.setItemStack(slot++, buildTrackItem(entry));
        }

        return inv;
    }

    private static ItemStack buildRandomItem() {
        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Включает случайное", TextColor.color(0xFFF2E0))),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("воспроизведение треков.", TextColor.color(0xFFF2E0))),
                Component.space(),
                Component.text("➥ Нажмите, чтобы включить", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(Material.JUKEBOX)
                .set(DataComponents.CUSTOM_NAME,
                        Text.c("&#F1BB58&lС&#F1B858&lл&#F1B558&lу&#F1B258&lч&#F1AF58&lа&#F1AC58&lй&#F1A958&lн&#F1A658&lа&#F1A358&lя")
                                .decoration(TextDecoration.ITALIC, false))
                .set(DataComponents.LORE, lore)
                .set(TRACK_KEY_TAG, RANDOM_MARKER)
                .hideExtraTooltip()
                .build();
    }

    private static ItemStack buildTrackItem(MenuEntry entry) {
        Component name = Text.c("&#F1BB58" + entry.name).decoration(TextDecoration.ITALIC, false);

        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(entry.category, TextColor.color(0x65D1FC))),
                Component.space(),
                Component.text("➥ Нажмите, чтобы включить", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(entry.material)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(TRACK_KEY_TAG, entry.key)
                .set(TRACK_NAME_TAG, entry.name)
                .hideExtraTooltip()
                .build();
    }

    private static Component buildPrefix() {
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(LobbyMusicManager.MUSIC_TEXT)
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space());
    }

    // ── Track catalogue ─────────────────────────────────────────────────────

    private record MenuEntry(String key, String name, String category, Material material) {}

    private static final List<MenuEntry> TRACKS = List.of(
            // ── Общая музыка ──
            t("minecraft:music.menu",     "Музыка меню",      "Общая",  Material.NOTE_BLOCK),
            t("minecraft:music.creative", "Творческий режим",  "Общая",  Material.NOTE_BLOCK),
            t("minecraft:music.game",     "Игровая музыка",   "Общая",  Material.NOTE_BLOCK),
            t("minecraft:music.end",      "Музыка Края",      "Общая",  Material.END_STONE),
            t("minecraft:music.credits",  "Титры",            "Общая",  Material.NOTE_BLOCK),
            t("minecraft:music.dragon",   "Дракон Края",      "Общая",  Material.DRAGON_HEAD),

            // ── Незер ──
            t("minecraft:music.nether.basalt_deltas",   "Базальтовые дельты", "Незер", Material.BASALT),
            t("minecraft:music.nether.crimson_forest",  "Багровый лес",       "Незер", Material.CRIMSON_NYLIUM),
            t("minecraft:music.nether.nether_wastes",   "Пустоши Незера",     "Незер", Material.NETHERRACK),
            t("minecraft:music.nether.soul_sand_valley","Долина песка душ",    "Незер", Material.SOUL_SAND),
            t("minecraft:music.nether.warped_forest",   "Искажённый лес",     "Незер", Material.WARPED_NYLIUM),

            // ── Верхний мир ──
            t("minecraft:music.overworld.badlands",       "Пустошь",            "Верхний мир", Material.RED_SAND),
            t("minecraft:music.overworld.bamboo_jungle",  "Бамбуковые джунгли", "Верхний мир", Material.BAMBOO),
            t("minecraft:music.overworld.cherry_grove",   "Вишнёвая роща",      "Верхний мир", Material.CHERRY_LEAVES),
            t("minecraft:music.overworld.deep_dark",      "Глубокая тьма",      "Верхний мир", Material.SCULK),
            t("minecraft:music.overworld.desert",         "Пустыня",            "Верхний мир", Material.SAND),
            t("minecraft:music.overworld.flower_forest",  "Цветочный лес",      "Верхний мир", Material.ALLIUM),
            t("minecraft:music.overworld.forest",         "Лес",                "Верхний мир", Material.OAK_LOG),
            t("minecraft:music.overworld.jungle",         "Джунгли",            "Верхний мир", Material.JUNGLE_LOG),
            t("minecraft:music.overworld.lush_caves",     "Пышные пещеры",      "Верхний мир", Material.MOSS_BLOCK),
            t("minecraft:music.overworld.meadow",         "Луг",                "Верхний мир", Material.GRASS_BLOCK),
            t("minecraft:music.overworld.old_growth_taiga","Старая тайга",      "Верхний мир", Material.SPRUCE_LOG),
            t("minecraft:music.overworld.swamp",          "Болото",             "Верхний мир", Material.LILY_PAD),

            // ── Подводная ──
            t("minecraft:music.under_water", "Подводная музыка", "Подводная", Material.PRISMARINE),

            // ── Пластинки ──
            t("minecraft:music_disc.11",               "Пластинка — 11",              "Пластинка", Material.MUSIC_DISC_11),
            t("minecraft:music_disc.13",               "Пластинка — 13",              "Пластинка", Material.MUSIC_DISC_13),
            t("minecraft:music_disc.5",                "Пластинка — 5",               "Пластинка", Material.MUSIC_DISC_5),
            t("minecraft:music_disc.blocks",           "Пластинка — Blocks",          "Пластинка", Material.MUSIC_DISC_BLOCKS),
            t("minecraft:music_disc.cat",              "Пластинка — Cat",             "Пластинка", Material.MUSIC_DISC_CAT),
            t("minecraft:music_disc.chirp",            "Пластинка — Chirp",           "Пластинка", Material.MUSIC_DISC_CHIRP),
            t("minecraft:music_disc.creator",          "Пластинка — Creator",         "Пластинка", Material.MUSIC_DISC_CREATOR),
            t("minecraft:music_disc.creator_music_box","Пластинка — Creator Music Box","Пластинка", Material.MUSIC_DISC_CREATOR_MUSIC_BOX),
            t("minecraft:music_disc.far",              "Пластинка — Far",             "Пластинка", Material.MUSIC_DISC_FAR),
            t("minecraft:music_disc.mall",             "Пластинка — Mall",            "Пластинка", Material.MUSIC_DISC_MALL),
            t("minecraft:music_disc.mellohi",          "Пластинка — Mellohi",         "Пластинка", Material.MUSIC_DISC_MELLOHI),
            t("minecraft:music_disc.otherside",        "Пластинка — Otherside",       "Пластинка", Material.MUSIC_DISC_OTHERSIDE),
            t("minecraft:music_disc.pigstep",          "Пластинка — Pigstep",         "Пластинка", Material.MUSIC_DISC_PIGSTEP),
            t("minecraft:music_disc.precipice",        "Пластинка — Precipice",       "Пластинка", Material.MUSIC_DISC_PRECIPICE),
            t("minecraft:music_disc.relic",            "Пластинка — Relic",           "Пластинка", Material.MUSIC_DISC_RELIC),
            t("minecraft:music_disc.stal",             "Пластинка — Stal",            "Пластинка", Material.MUSIC_DISC_STAL),
            t("minecraft:music_disc.strad",            "Пластинка — Strad",           "Пластинка", Material.MUSIC_DISC_STRAD),
            t("minecraft:music_disc.wait",             "Пластинка — Wait",            "Пластинка", Material.MUSIC_DISC_WAIT),
            t("minecraft:music_disc.ward",             "Пластинка — Ward",            "Пластинка", Material.MUSIC_DISC_WARD)
    );

    private static MenuEntry t(String key, String name, String category, Material material) {
        return new MenuEntry(key, name, category, material);
    }
}
