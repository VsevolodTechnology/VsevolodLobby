package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicSelectorMenu;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferences;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.SidebarToggle;
import ua.vsevolod.lobby.util.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class LobbySettingsMenu {

    public static final int HOTBAR_SLOT = 8;
    static final Tag<String> SETTINGS_TAG = Tag.String("lobby-settings");
    private static final Tag<String> ACTION_TAG = Tag.String("settings-action");

    private static final int MUSIC_SLOT = 11;
    private static final int SCOREBOARD_SLOT = 13;
    private static final int POSITION_SLOT = 15;
    private static final int MUSIC_SELECTOR_SLOT = 22;

    private static final TextColor ACCENT = TextColor.color(0xF1BB58);
    private static final TextColor INFO_BLUE = TextColor.color(0x65D1FC);
    private static final TextColor TEXT_WHITE = TextColor.color(0xFFF2E0);
    private static final TextColor GREEN = TextColor.color(0x8EB126);
    private static final TextColor RED = TextColor.color(0xFA3B3B);

    private static final ItemStack HOTBAR_ITEM = buildHotbarItem();
    private static final ItemStack MUSIC_SELECTOR_ITEM = buildMusicSelectorItem();
    private static final ItemStack MUSIC_ON = toggleItem(Material.NOTE_BLOCK, "Музыка", true, "Фоновая музыка лобби.", "music");
    private static final ItemStack MUSIC_OFF = toggleItem(Material.GRAY_DYE, "Музыка", false, "Фоновая музыка лобби.", "music");
    private static final ItemStack SCOREBOARD_ON = toggleItem(Material.PAINTING, "Скорборд", true, "Таблица очков сбоку.", "scoreboard");
    private static final ItemStack SCOREBOARD_OFF = toggleItem(Material.GRAY_DYE, "Скорборд", false, "Таблица очков сбоку.", "scoreboard");
    private static final ItemStack POSITION_ON = toggleItem(Material.RECOVERY_COMPASS, "Сохранение позиции", true, "Запоминает точку выхода.", "position");
    private static final ItemStack POSITION_OFF = toggleItem(Material.GRAY_DYE, "Сохранение позиции", false, "Запоминает точку выхода.", "position");
    private static final ItemStack DECOR = ItemStack.builder(Material.BLACK_STAINED_GLASS_PANE)
            .set(DataComponents.CUSTOM_NAME, Component.empty())
            .hideExtraTooltip()
            .build();

    private static final long CLICK_COOLDOWN_MS = 200;

    private final PlayerPreferencesService preferencesService;
    private final LobbyMusicManager musicManager;
    private final SidebarToggle sidebarToggle;
    private final LobbyMusicSelectorMenu musicSelectorMenu;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public LobbySettingsMenu(
            PlayerPreferencesService preferencesService,
            LobbyMusicManager musicManager,
            SidebarToggle sidebarToggle,
            LobbyMusicSelectorMenu musicSelectorMenu
    ) {
        this.preferencesService = preferencesService;
        this.musicManager = musicManager;
        this.sidebarToggle = sidebarToggle;
        this.musicSelectorMenu = musicSelectorMenu;
    }

    public void register(EventNode<Event> node) {
        node.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getItemStack().getTag(SETTINGS_TAG) == null) return;
            event.setCancelled(true);
            open(event.getPlayer());
        });

        node.addListener(InventoryPreClickEvent.class, event -> {
            if (!(event.getInventory() instanceof Inventory inv)) return;
            String title = inv.getTag(SETTINGS_TAG);
            if (!"settings".equals(title)) return;
            event.setCancelled(true);

            String action = event.getClickedItem().getTag(ACTION_TAG);
            if (action == null) return;

            Player player = event.getPlayer();
            long now = System.currentTimeMillis();
            Long last = lastClick.put(player.getUuid(), now);
            if (last != null && (now - last) < CLICK_COOLDOWN_MS) return;
            switch (action) {
                case "music" -> {
                    musicManager.toggle(player);
                    boolean nowEnabled = musicManager.isEnabled(player);
                    player.sendMessage(settingsMessage(
                            "Музыка",
                            nowEnabled ? "включена" : "выключена",
                            nowEnabled
                    ));
                    refreshMenu(player, inv);
                }
                case "scoreboard" -> {
                    sidebarToggle.toggle(player);
                    refreshMenu(player, inv);
                }
                case "position" -> {
                    PlayerPreferences prefs = preferencesService.get(player.getUuid());
                    boolean nowEnabled = !prefs.positionSaveEnabled();
                    preferencesService.savePositionSaveEnabled(player.getUuid(), nowEnabled);
                    player.sendMessage(settingsMessage(
                            "Сохранение позиции",
                            nowEnabled ? "включено" : "выключено",
                            nowEnabled
                    ));
                    refreshMenu(player, inv);
                }
                case "music-selector" -> {
                    player.closeInventory();
                    musicSelectorMenu.open(player);
                }
            }
        });
    }

    public void open(Player player) {
        PlayerPreferences prefs = preferencesService.get(player.getUuid());
        boolean musicOn = musicManager.isEnabled(player);
        boolean sidebarVisible = !sidebarToggle.isHidden(player);
        boolean positionSave = prefs.positionSaveEnabled();

        Inventory inv = new Inventory(InventoryType.CHEST_3_ROW, Text.c("&8Настройки"));
        inv.setTag(SETTINGS_TAG, "settings");

        for (int i = 0; i < 27; i++) {
            inv.setItemStack(i, DECOR);
        }

        inv.setItemStack(MUSIC_SLOT, musicOn ? MUSIC_ON : MUSIC_OFF);
        inv.setItemStack(SCOREBOARD_SLOT, sidebarVisible ? SCOREBOARD_ON : SCOREBOARD_OFF);
        inv.setItemStack(POSITION_SLOT, positionSave ? POSITION_ON : POSITION_OFF);
        inv.setItemStack(MUSIC_SELECTOR_SLOT, MUSIC_SELECTOR_ITEM);

        player.openInventory(inv);
    }

    private void refreshMenu(Player player, Inventory inv) {
        boolean musicOn = musicManager.isEnabled(player);
        boolean sidebarVisible = !sidebarToggle.isHidden(player);
        boolean positionSave = preferencesService.get(player.getUuid()).positionSaveEnabled();

        inv.setItemStack(MUSIC_SLOT, musicOn ? MUSIC_ON : MUSIC_OFF);
        inv.setItemStack(SCOREBOARD_SLOT, sidebarVisible ? SCOREBOARD_ON : SCOREBOARD_OFF);
        inv.setItemStack(POSITION_SLOT, positionSave ? POSITION_ON : POSITION_OFF);
    }

    // ── Hotbar item ──────────────────────────────────────────────────────────

    public static ItemStack createHotbarItem() {
        return HOTBAR_ITEM;
    }

    private static ItemStack buildHotbarItem() {
        Component name = Component.text()
                .append(Text.c("&#F1BB58&lН&#F1B858&lа&#F1B558&lс&#F1B258&lт&#F1AF58&lр&#F1AC58&lо&#F1A958&lй&#F1A658&lк&#F1A358&lи"))
                .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                .append(Component.text("Открыть", GREEN))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false)
                .build();

        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.text(" «Информация»", INFO_BLUE),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Музыка, скорборд,", TEXT_WHITE)),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("сохранение позиции.", TEXT_WHITE)),
                Component.space(),
                Component.text("➥ ПКМ — открыть настройки", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(Material.COMPARATOR)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(SETTINGS_TAG, "item")
                .hideExtraTooltip()
                .build();
    }

    // ── Menu items ───────────────────────────────────────────────────────────

    private static ItemStack buildMusicSelectorItem() {
        Component name = Component.text()
                .append(Text.c("&#65D1FC&lВ&#62CEF9&lы&#5FCBF6&lб&#5CC8F3&lо&#59C5F0&lр &#53BFEA&lм&#50BCE7&lу&#4DB9E4&lз&#4AB6E1&lы&#47B3DE&lк&#44B0DB&lи"))
                .decoration(TextDecoration.ITALIC, false)
                .build();

        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Выбрать конкретный трек", TEXT_WHITE)),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("или случайный режим.", TEXT_WHITE)),
                Component.space(),
                Component.text("➥ Нажмите, чтобы открыть", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(Material.JUKEBOX)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(ACTION_TAG, "music-selector")
                .hideExtraTooltip()
                .build();
    }

    private static ItemStack toggleItem(Material material, String label, boolean enabled, String description, String action) {
        Component status = enabled
                ? Component.text("Включено", NamedTextColor.GREEN)
                : Component.text("Выключено", NamedTextColor.RED);

        Component name = Component.text()
                .append(Component.text(label + " ", ACCENT))
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(enabled
                        ? Component.text("Вкл", GREEN)
                        : Component.text("Выкл", RED))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false)
                .build();

        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.text(" «Информация»", INFO_BLUE),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Статус: ", TEXT_WHITE))
                        .append(status),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(description, TEXT_WHITE)),
                Component.space(),
                Component.text("➥ Нажмите, чтобы переключить", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(ACTION_TAG, action)
                .hideExtraTooltip()
                .build();
    }

    private static Component settingsMessage(String setting, String state, boolean enabled) {
        Component stateComponent = enabled
                ? Component.text(state, TextColor.color(0x81E366))
                : Component.text(state, TextColor.color(0xE36666));

        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Text.c("&#F1BB58&lНастройки"))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text(setting + ": ", TEXT_WHITE))
                .append(stateComponent);
    }
}
