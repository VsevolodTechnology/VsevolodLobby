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
import ua.vsevolod.lobby.feature.lobby.player.time.PlayerTimeZoneService;
import ua.vsevolod.lobby.feature.lobby.player.protocol.LobbyProtocolWarningService;
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
    private static final int VERSION_WARNING_SLOT = 17;
    private static final int TIME_SLOT = 20;
    private static final int MUSIC_SELECTOR_SLOT = 22;

    private static final TextColor ACCENT = TextColor.color(0xAE3AF3);
    private static final TextColor INFO_BLUE = TextColor.color(0x65D1FC);
    private static final TextColor TEXT_WHITE = TextColor.color(0xFFF2E0);
    private static final TextColor GREEN = TextColor.color(0x8EB126);
    private static final TextColor RED = TextColor.color(0xFA3B3B);

    private static final Component MSG_MUSIC_ON       = buildSettingsMsg("Музыка", "включена", true);
    private static final Component MSG_MUSIC_OFF      = buildSettingsMsg("Музыка", "выключена", false);
    private static final Component MSG_POSITION_ON    = buildSettingsMsg("Сохранение позиции", "включено", true);
    private static final Component MSG_POSITION_OFF   = buildSettingsMsg("Сохранение позиции", "выключено", false);
    private static final Component MSG_VERSION_WARNING_ON  = buildSettingsMsg("Подсказка о версии", "включена", true);
    private static final Component MSG_VERSION_WARNING_OFF = buildSettingsMsg("Подсказка о версии", "выключена", false);
    private static final Component MSG_TIME_ON  = buildSettingsMsg("Время по IP", "включено", true);
    private static final Component MSG_TIME_OFF = buildSettingsMsg("Время по IP", "выключено", false);

    private static final ItemStack HOTBAR_ITEM = buildHotbarItem();
    private static final ItemStack MUSIC_SELECTOR_ITEM = buildMusicSelectorItem();
    private static final ItemStack MUSIC_ON = toggleItem(Material.NOTE_BLOCK, "Музыка", true, "Фоновая музыка лобби.", "music");
    private static final ItemStack MUSIC_OFF = toggleItem(Material.GRAY_DYE, "Музыка", false, "Фоновая музыка лобби.", "music");
    private static final ItemStack SCOREBOARD_ON = toggleItem(Material.PAINTING, "Скорборд", true, "Таблица очков сбоку.", "scoreboard");
    private static final ItemStack SCOREBOARD_OFF = toggleItem(Material.GRAY_DYE, "Скорборд", false, "Таблица очков сбоку.", "scoreboard");
    private static final ItemStack POSITION_ON = toggleItem(Material.RECOVERY_COMPASS, "Сохранение позиции", true, "Запоминает точку выхода.", "position");
    private static final ItemStack POSITION_OFF = toggleItem(Material.GRAY_DYE, "Сохранение позиции", false, "Запоминает точку выхода.", "position");
    private static final ItemStack VERSION_WARNING_ON  = toggleItem(Material.WRITABLE_BOOK, "Подсказка о версии", true, "BossBar с рекомендацией обновить клиент.", "version-warning");
    private static final ItemStack VERSION_WARNING_OFF = toggleItem(Material.GRAY_DYE, "Подсказка о версии", false, "BossBar с рекомендацией обновить клиент.", "version-warning");
    private static final ItemStack TIME_ON  = timeToggleItem(true);
    private static final ItemStack TIME_OFF = timeToggleItem(false);
    private static final ItemStack DECOR = ItemStack.builder(Material.BLACK_STAINED_GLASS_PANE)
            .set(DataComponents.CUSTOM_NAME, Component.empty())
            .hideExtraTooltip()
            .build();

    private static final long CLICK_COOLDOWN_MS = 200;

    private final PlayerPreferencesService preferencesService;
    private final LobbyMusicManager musicManager;
    private final SidebarToggle sidebarToggle;
    private final LobbyMusicSelectorMenu musicSelectorMenu;
    private final LobbyProtocolWarningService protocolWarningService;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public LobbySettingsMenu(
            PlayerPreferencesService preferencesService,
            LobbyMusicManager musicManager,
            SidebarToggle sidebarToggle,
            LobbyMusicSelectorMenu musicSelectorMenu,
            LobbyProtocolWarningService protocolWarningService
    ) {
        this.preferencesService = preferencesService;
        this.musicManager = musicManager;
        this.sidebarToggle = sidebarToggle;
        this.musicSelectorMenu = musicSelectorMenu;
        this.protocolWarningService = protocolWarningService;
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
                    player.sendMessage(nowEnabled ? MSG_MUSIC_ON : MSG_MUSIC_OFF);
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
                    player.sendMessage(nowEnabled ? MSG_POSITION_ON : MSG_POSITION_OFF);
                    refreshMenu(player, inv);
                }
                case "version-warning" -> {
                    PlayerPreferences prefs = preferencesService.get(player.getUuid());
                    boolean nowEnabled = !prefs.protocolWarningEnabled();
                    preferencesService.saveProtocolWarningEnabled(player.getUuid(), nowEnabled);
                    if (!nowEnabled) protocolWarningService.hideFor(player);
                    player.sendMessage(nowEnabled ? MSG_VERSION_WARNING_ON : MSG_VERSION_WARNING_OFF);
                    refreshMenu(player, inv);
                }
                case "time-by-ip" -> {
                    PlayerTimeZoneService tz = PlayerTimeZoneService.get();
                    if (tz != null) {
                        boolean nowEnabled = !tz.isIpMode(player.getUuid());
                        tz.setIpMode(player.getUuid(), nowEnabled);
                        player.sendMessage(nowEnabled ? MSG_TIME_ON : MSG_TIME_OFF);
                    }
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
        boolean versionWarning = prefs.protocolWarningEnabled();

        Inventory inv = new Inventory(InventoryType.CHEST_3_ROW, Text.c("<dark_gray>Настройки"));
        inv.setTag(SETTINGS_TAG, "settings");

        for (int i = 0; i < 27; i++) {
            inv.setItemStack(i, DECOR);
        }

        inv.setItemStack(MUSIC_SLOT, musicOn ? MUSIC_ON : MUSIC_OFF);
        inv.setItemStack(SCOREBOARD_SLOT, sidebarVisible ? SCOREBOARD_ON : SCOREBOARD_OFF);
        inv.setItemStack(POSITION_SLOT, positionSave ? POSITION_ON : POSITION_OFF);
        inv.setItemStack(VERSION_WARNING_SLOT, versionWarning ? VERSION_WARNING_ON : VERSION_WARNING_OFF);
        inv.setItemStack(TIME_SLOT, isTimeByIp(player) ? TIME_ON : TIME_OFF);
        inv.setItemStack(MUSIC_SELECTOR_SLOT, MUSIC_SELECTOR_ITEM);

        player.openInventory(inv);
    }

    private void refreshMenu(Player player, Inventory inv) {
        PlayerPreferences prefs = preferencesService.get(player.getUuid());
        boolean musicOn = musicManager.isEnabled(player);
        boolean sidebarVisible = !sidebarToggle.isHidden(player);
        boolean positionSave = prefs.positionSaveEnabled();
        boolean versionWarning = prefs.protocolWarningEnabled();

        inv.setItemStack(MUSIC_SLOT, musicOn ? MUSIC_ON : MUSIC_OFF);
        inv.setItemStack(SCOREBOARD_SLOT, sidebarVisible ? SCOREBOARD_ON : SCOREBOARD_OFF);
        inv.setItemStack(POSITION_SLOT, positionSave ? POSITION_ON : POSITION_OFF);
        inv.setItemStack(VERSION_WARNING_SLOT, versionWarning ? VERSION_WARNING_ON : VERSION_WARNING_OFF);
        inv.setItemStack(TIME_SLOT, isTimeByIp(player) ? TIME_ON : TIME_OFF);
    }

    private static boolean isTimeByIp(Player player) {
        PlayerTimeZoneService tz = PlayerTimeZoneService.get();
        return tz != null && tz.isIpMode(player.getUuid());
    }

    // ── Hotbar item ──────────────────────────────────────────────────────────

    public static ItemStack createHotbarItem() {
        return HOTBAR_ITEM;
    }

    private static ItemStack buildHotbarItem() {
        Component name = Component.text()
                .append(Text.c("<gradient:#AE3AF3:#985DBC><bold>Настройки</bold></gradient>"))
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
                Component.text("➥ ПКМ — открыть настройки", ACCENT)
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
                .append(Text.c("<gradient:#65D1FC:#59C5F0><bold>Выбор</bold></gradient> <gradient:#53BFEA:#44B0DB><bold>музыки</bold></gradient>"))
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
                Component.text("➥ Нажмите, чтобы открыть", ACCENT)
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
                Component.text("➥ Нажмите, чтобы переключить", ACCENT)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(ACTION_TAG, action)
                .hideExtraTooltip()
                .build();
    }

    private static ItemStack timeToggleItem(boolean enabled) {
        Component status = enabled
                ? Component.text("Включено", NamedTextColor.GREEN)
                : Component.text("Выключено", NamedTextColor.RED);

        Component name = Component.text()
                .append(Component.text("Время по IP ", ACCENT))
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(enabled ? Component.text("Вкл", GREEN) : Component.text("Выкл", RED))
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
                        .append(Component.text("Время в TAB по твоему", TEXT_WHITE)),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("часовому поясу.", TEXT_WHITE)),
                Component.space(),
                Component.empty()
                        .append(Component.text(" ⚠ ", TextColor.color(0xD9A6F0)))
                        .append(Component.text("Пояс определяется по IP.", TEXT_WHITE)),
                Component.empty()
                        .append(Component.text("   ", NamedTextColor.GRAY))
                        .append(Component.text("С VPN время может быть неточным.", NamedTextColor.GRAY)),
                Component.space(),
                Component.text("➥ Нажмите, чтобы переключить", ACCENT)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(enabled ? Material.CLOCK : Material.GRAY_DYE)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(ACTION_TAG, "time-by-ip")
                .hideExtraTooltip()
                .build();
    }

    private static Component buildSettingsMsg(String setting, String state, boolean enabled) {
        Component stateComponent = enabled
                ? Component.text(state, TextColor.color(0x81E366))
                : Component.text(state, TextColor.color(0xE36666));

        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Text.c("<#AE3AF3><bold>Настройки"))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text(setting + ": ", TEXT_WHITE))
                .append(stateComponent);
    }
}
