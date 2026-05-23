package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.DimensionType;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.ServerLogger;
import ua.vsevolod.lobby.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ParkourSettingsMenu {

    public static final int ITEM_SLOT = 4;
    public static final int LEAVE_SLOT = 8;
    private static final Tag<String> SETTINGS_ITEM_TAG = Tag.String("parkour-settings");
    private static final Tag<String> LEAVE_ITEM_TAG    = Tag.String("parkour-leave");
    private static final Tag<String> MENU_TAG          = Tag.String("parkour-settings-menu");
    private static final Tag<String> THEME_MENU_TAG    = Tag.String("parkour-theme-menu");

    // Slot layout — CHEST_5_ROW (45 slots)
    // Row 0: regular difficulties — evenly spaced on positions 1 / 4 / 7
    private static final int DIFF_NORMAL_SLOT  = 1;
    private static final int DIFF_HARD_SLOT    = 4;
    private static final int DIFF_EXTREME_SLOT = 7;
    // Row 1: competitive (centred, its own row)
    private static final int DIFF_COMPETITIVE_SLOT = 13;
    // Row 2: dimensions
    private static final int DIM_OVERWORLD_SLOT = 20;
    private static final int DIM_NETHER_SLOT    = 22;
    private static final int DIM_END_SLOT       = 24;
    // Row 3: other settings
    private static final int THEME_BUTTON_SLOT = 28;
    private static final int SOUND_SLOT        = 30;
    private static final int TRAINING_SLOT     = 32;
    private static final int MUSIC_SLOT        = 34;
    private static final int MUSIC_SELECT_SLOT = 35;
    // Row 4: apply
    private static final int APPLY_SLOT = 40;

    // Theme sub-menu (CHEST_3_ROW)
    private static final int THEME_BACK_SLOT = 22;

    // Shared background items — created once, reused across all invocations
    private static final ItemStack BG_MAIN = ItemStack.builder(Material.BLACK_STAINED_GLASS_PANE)
            .set(DataComponents.CUSTOM_NAME, Component.empty())
            .hideExtraTooltip().build();

    private static final TextColor LORE_WHITE = LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL;
    private static final TextColor HINT_COLOR = TextColor.color(0xC8A060);
    private static final TextColor ACCENT = TextColor.color(0xAE3AF3);
    private static final TextColor GREEN  = TextColor.color(0x55E068);
    private static final TextColor RED    = TextColor.color(0xE05555);

    // Per-player cooldown to prevent rapid-fire clicks from allocating work
    private static final long CLICK_COOLDOWN_MS = 150;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private final ParkourService parkourService;
    private final Consumer<Player> onMusicToggle;
    private final Consumer<Player> onMusicSelect;
    private final Consumer<Player> onLeave;
    private final Predicate<Player> isMusicEnabled;
    private final ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService prefs;

    private record SelectedSettings(
            ParkourDifficulty difficulty,
            ParkourTheme theme,
            RegistryKey<DimensionType> dimension,
            boolean training,
            ParkourSoundPreset sound
    ) {
        SelectedSettings withDifficulty(ParkourDifficulty d) { return new SelectedSettings(d, theme, dimension, training, sound); }
        SelectedSettings withTheme(ParkourTheme t)           { return new SelectedSettings(difficulty, t, dimension, training, sound); }
        SelectedSettings withDimension(RegistryKey<DimensionType> d) { return new SelectedSettings(difficulty, theme, d, training, sound); }
        SelectedSettings withTraining(boolean t)             { return new SelectedSettings(difficulty, theme, dimension, t, sound); }
        SelectedSettings withSound(ParkourSoundPreset s)     { return new SelectedSettings(difficulty, theme, dimension, training, s); }
    }

    // Defaults: Nether dimension + Competitive mode. The player can change both in the menu.
    private static final SelectedSettings DEFAULT_SETTINGS = new SelectedSettings(
            ParkourDifficulty.COMPETITIVE, null, DimensionType.THE_NETHER, false, ParkourSoundPreset.STANDARD);

    private final Map<UUID, SelectedSettings> selections  = new ConcurrentHashMap<>();
    /** Per-player cached Inventory for the main settings menu. Reused across clicks. */
    private final Map<UUID, Inventory>        mainMenus   = new ConcurrentHashMap<>();
    /** Per-player cached Inventory for the theme sub-menu. Reused across clicks. */
    private final Map<UUID, Inventory>        themeMenus  = new ConcurrentHashMap<>();

    public ParkourSettingsMenu(ParkourService parkourService,
                               Consumer<Player> onMusicToggle,
                               Consumer<Player> onMusicSelect,
                               Consumer<Player> onLeave,
                               Predicate<Player> isMusicEnabled,
                               ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService prefs) {
        this.parkourService = parkourService;
        this.onMusicToggle = onMusicToggle;
        this.onMusicSelect = onMusicSelect;
        this.onLeave = onLeave;
        this.isMusicEnabled = isMusicEnabled;
        this.prefs = prefs;
    }

    // ── Settings accessors ──────────────────────────────────────────────────

    private SelectedSettings getSettings(UUID uuid) {
        return selections.computeIfAbsent(uuid, k -> {
            SelectedSettings saved = loadFromPrefs(k);
            if (saved != null) return saved;
            // Persist the initial random theme so the same one survives the next relog.
            SelectedSettings initial = DEFAULT_SETTINGS.withTheme(ParkourTheme.randomTheme());
            persist(k, initial);
            return initial;
        });
    }

    // ── Persistence (via PlayerPreferencesService → player_data/*.properties) ────

    /** Sets a player's settings and persists them so they survive a relog. */
    private void putSettings(UUID uuid, SelectedSettings settings) {
        selections.put(uuid, settings);
        persist(uuid, settings);
    }

    private void persist(UUID uuid, SelectedSettings s) {
        String themeName = s.theme() == null ? null : s.theme().name();
        prefs.saveParkour(
                uuid,
                s.difficulty().name(),
                themeName,
                dimToken(s.dimension()),
                s.training(),
                s.sound().name());
    }

    private SelectedSettings loadFromPrefs(UUID uuid) {
        var p = prefs.get(uuid);
        if (!p.hasParkour()) return null;
        try {
            ParkourTheme theme;
            if (p.parkourTheme() != null && !p.parkourTheme().isBlank()) {
                try { theme = ParkourTheme.valueOf(p.parkourTheme()); }
                catch (IllegalArgumentException e) { theme = ParkourTheme.randomTheme(); }
            } else {
                theme = ParkourTheme.randomTheme();
            }
            return new SelectedSettings(
                    ParkourDifficulty.valueOf(p.parkourDifficulty()),
                    theme,
                    tokenToDim(p.parkourDimension() == null ? "NETHER" : p.parkourDimension()),
                    p.parkourTraining(),
                    p.parkourSound() == null
                            ? ParkourSoundPreset.STANDARD
                            : ParkourSoundPreset.valueOf(p.parkourSound()));
        } catch (Exception e) {
            return null; // malformed value — fall back to defaults
        }
    }

    private static String dimToken(RegistryKey<DimensionType> dim) {
        if (dim == DimensionType.THE_NETHER) return "NETHER";
        if (dim == DimensionType.OVERWORLD) return "OVERWORLD";
        return "END";
    }

    private static RegistryKey<DimensionType> tokenToDim(String token) {
        return switch (token) {
            case "NETHER" -> DimensionType.THE_NETHER;
            case "OVERWORLD" -> DimensionType.OVERWORLD;
            default -> DimensionType.THE_END;
        };
    }

    public ParkourDifficulty getDifficulty(UUID uuid)           { return getSettings(uuid).difficulty(); }
    public ParkourTheme      getTheme(UUID uuid)                { return getSettings(uuid).theme(); }
    public RegistryKey<DimensionType> getDimension(UUID uuid)   { return getSettings(uuid).dimension(); }
    public boolean           isTrainingMode(UUID uuid)          { return getSettings(uuid).training(); }
    public ParkourSoundPreset getSoundPreset(UUID uuid)         { return getSettings(uuid).sound(); }

    public void evict(UUID uuid) {
        selections.remove(uuid);
        mainMenus.remove(uuid);
        themeMenus.remove(uuid);
        lastClick.remove(uuid);
    }

    // ── Event registration ──────────────────────────────────────────────────

    public void register(EventNode<Event> node) {
        node.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getItemStack().getTag(LEAVE_ITEM_TAG) != null) {
                event.setCancelled(true);
                onLeave.accept(event.getPlayer());
                return;
            }
            if (event.getItemStack().getTag(SETTINGS_ITEM_TAG) == null) return;
            event.setCancelled(true);
            if (parkourService.isInParkour(event.getPlayer())) {
                openMainMenu(event.getPlayer());
            }
        });

        node.addListener(InventoryPreClickEvent.class, event -> {
            if (parkourService.isInParkour(event.getPlayer())) {
                event.setCancelled(true);
            }

            if (!(event.getInventory() instanceof Inventory inv)) return;

            if (inv.getTag(MENU_TAG) != null) {
                handleMainMenuClick(event.getPlayer(), event.getSlot());
            } else if (inv.getTag(THEME_MENU_TAG) != null) {
                handleThemeMenuClick(event.getPlayer(), event.getSlot());
            }
        });

        node.addListener(ItemDropEvent.class, event -> {
            if (parkourService.isInParkour(event.getPlayer())) event.setCancelled(true);
        });

        node.addListener(PlayerSwapItemEvent.class, event -> {
            if (parkourService.isInParkour(event.getPlayer())) event.setCancelled(true);
        });
    }

    // ── Open / refresh ──────────────────────────────────────────────────────

    /** Opens (or re-opens) the main menu for the player, syncing state from the active session. */
    private void openMainMenu(Player player) {
        syncFromSession(player);
        Inventory inv = getOrCreateMainMenu(player.getUuid());
        fillMainMenuSlots(player, inv);
        player.openInventory(inv);
    }

    /** Returns the cached main-menu Inventory, creating and templating it once per player. */
    private Inventory getOrCreateMainMenu(UUID uuid) {
        return mainMenus.computeIfAbsent(uuid, k -> {
            Inventory inv = new Inventory(InventoryType.CHEST_5_ROW, Text.c("<#AE3AF3><bold>Настройки паркура"));
            inv.setTag(MENU_TAG, "1");
            for (int i = 0; i < 45; i++) inv.setItemStack(i, BG_MAIN);
            return inv;
        });
    }

    /** Updates only the dynamic slots in the existing main-menu Inventory — no new Inventory object. */
    private void fillMainMenuSlots(Player player, Inventory inv) {
        UUID uuid = player.getUuid();
        ParkourDifficulty diff = getDifficulty(uuid);
        ParkourTheme      theme = getTheme(uuid);
        RegistryKey<DimensionType> dim = getDimension(uuid);
        boolean training = isTrainingMode(uuid);
        ParkourSoundPreset sound = getSoundPreset(uuid);

        inv.setItemStack(DIFF_NORMAL_SLOT,       difficultyItem(ParkourDifficulty.NORMAL,      Material.IRON_INGOT,      diff));
        inv.setItemStack(DIFF_HARD_SLOT,         difficultyItem(ParkourDifficulty.HARD,        Material.BLAZE_POWDER,    diff));
        inv.setItemStack(DIFF_EXTREME_SLOT,      difficultyItem(ParkourDifficulty.EXTREME,     Material.DRAGON_BREATH,   diff));
        inv.setItemStack(DIFF_COMPETITIVE_SLOT,  difficultyItem(ParkourDifficulty.COMPETITIVE, Material.NETHER_STAR,     diff));

        inv.setItemStack(DIM_OVERWORLD_SLOT, dimensionItem(Material.GRASS_BLOCK, "Обычный мир", "Дневное небо с облаками",   TextColor.color(0x8FAE8B), dim == DimensionType.OVERWORLD));
        inv.setItemStack(DIM_NETHER_SLOT,    dimensionItem(Material.NETHERRACK,  "Нижний мир",  "Тёмно-красное небо Незера", TextColor.color(0xC86E6E), dim == DimensionType.THE_NETHER));
        inv.setItemStack(DIM_END_SLOT,       dimensionItem(Material.END_STONE,   "Край",        "Тёмное звёздное небо Энда", TextColor.color(0xB48EDC), dim == DimensionType.THE_END));

        inv.setItemStack(THEME_BUTTON_SLOT, themeButton(theme));
        inv.setItemStack(SOUND_SLOT,        soundItem(sound));
        inv.setItemStack(TRAINING_SLOT,     trainingItem(training));
        inv.setItemStack(MUSIC_SLOT,        musicItem(isMusicEnabled.test(player)));
        inv.setItemStack(MUSIC_SELECT_SLOT, musicSelectItem());

        ParkourSession session = parkourService.getSession(player);
        inv.setItemStack(APPLY_SLOT, applyItem(session, uuid));
    }

    /** Returns the cached theme sub-menu Inventory, creating and templating it once per player. */
    private Inventory getOrCreateThemeMenu(UUID uuid) {
        return themeMenus.computeIfAbsent(uuid, k -> {
            Inventory inv = new Inventory(InventoryType.CHEST_3_ROW, Text.c("<#AE3AF3><bold>Выбор темы блоков"));
            inv.setTag(THEME_MENU_TAG, "1");
            for (int i = 0; i < 27; i++) inv.setItemStack(i, BG_MAIN);
            return inv;
        });
    }

    /** Updates only the dynamic slots in the existing theme-menu Inventory. */
    private void fillThemeMenuSlots(UUID uuid, Inventory inv) {
        ParkourTheme current = getTheme(uuid);
        ParkourTheme[] themes = ParkourTheme.values();
        for (int i = 0; i < themes.length; i++) {
            inv.setItemStack(i, themeSubItem(themes[i], current));
        }
        inv.setItemStack(THEME_BACK_SLOT, backButton());
    }

    // ── Click handlers ──────────────────────────────────────────────────────

    private boolean checkCooldown(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(uuid, now);
        return last != null && (now - last) < CLICK_COOLDOWN_MS;
    }

    private void handleMainMenuClick(Player player, int slot) {
        if (checkCooldown(player.getUuid())) return;

        UUID uuid = player.getUuid();
        Inventory inv = mainMenus.get(uuid);
        if (inv == null) return;

        ParkourDifficulty clickedDiff = difficultyForSlot(slot);
        if (clickedDiff != null) {
            putSettings(uuid, getSettings(uuid).withDifficulty(clickedDiff));
            fillMainMenuSlots(player, inv);
            return;
        }

        RegistryKey<DimensionType> clickedDim = dimensionForSlot(slot);
        if (clickedDim != null) {
            putSettings(uuid, getSettings(uuid).withDimension(clickedDim));
            fillMainMenuSlots(player, inv);
            return;
        }

        if (slot == THEME_BUTTON_SLOT) {
            Inventory themeInv = getOrCreateThemeMenu(uuid);
            fillThemeMenuSlots(uuid, themeInv);
            player.openInventory(themeInv);
            return;
        }

        if (slot == SOUND_SLOT) {
            putSettings(uuid, getSettings(uuid).withSound(getSoundPreset(uuid).next()));
            fillMainMenuSlots(player, inv);
            return;
        }

        if (slot == TRAINING_SLOT) {
            putSettings(uuid, getSettings(uuid).withTraining(!isTrainingMode(uuid)));
            fillMainMenuSlots(player, inv);
            return;
        }

        if (slot == MUSIC_SLOT) {
            onMusicToggle.accept(player);
            fillMainMenuSlots(player, inv);
            return;
        }

        if (slot == MUSIC_SELECT_SLOT) {
            onMusicSelect.accept(player);
            return;
        }

        if (slot == APPLY_SLOT) {
            player.closeInventory();
            applySettings(player);
        }
    }

    private void handleThemeMenuClick(Player player, int slot) {
        if (checkCooldown(player.getUuid())) return;

        UUID uuid = player.getUuid();

        if (slot == THEME_BACK_SLOT) {
            Inventory mainInv = getOrCreateMainMenu(uuid);
            fillMainMenuSlots(player, mainInv);
            player.openInventory(mainInv);
            return;
        }

        ParkourTheme[] themes = ParkourTheme.values();
        if (slot >= 0 && slot < themes.length) {
            putSettings(uuid, getSettings(uuid).withTheme(themes[slot]));
            // Update theme menu in-place (highlight changes) then switch back to main menu
            Inventory mainInv = getOrCreateMainMenu(uuid);
            fillMainMenuSlots(player, mainInv);
            player.openInventory(mainInv);
        }
    }

    // ── Session sync ────────────────────────────────────────────────────────

    private void syncFromSession(Player player) {
        ParkourSession session = parkourService.getSession(player);
        if (session == null) return;
        putSettings(player.getUuid(), new SelectedSettings(
                session.getDifficulty(), session.getTheme(), session.getCurrentDimension(),
                session.isTrainingMode(), session.getSoundPreset()));
    }

    // ── Smart apply ─────────────────────────────────────────────────────────

    private void applySettings(Player player) {
        ParkourSession session = parkourService.getSession(player);
        if (session == null) return;
        UUID uuid = player.getUuid();

        ParkourDifficulty newDiff    = getDifficulty(uuid);
        ParkourTheme      newTheme   = getTheme(uuid);
        RegistryKey<DimensionType> newDim = getDimension(uuid);
        boolean           newTraining = isTrainingMode(uuid);
        ParkourSoundPreset newSound  = getSoundPreset(uuid);

        boolean diffChanged     = newDiff    != session.getDifficulty();
        boolean themeChanged    = newTheme   != session.getTheme();
        boolean dimChanged      = newDim     != session.getCurrentDimension();
        boolean trainingChanged = newTraining != session.isTrainingMode();
        boolean soundChanged    = newSound   != session.getSoundPreset();

        if (!diffChanged && !themeChanged && !dimChanged && !trainingChanged && !soundChanged) return;

        if (soundChanged && !diffChanged && !trainingChanged) {
            session.setSoundPreset(newSound);
        }

        boolean needsRestart = diffChanged || trainingChanged;

        if (diffChanged) {
            String oldMode = session.getDifficulty().displayName();
            String newMode = newDiff.displayName();
            ServerLogger.get().playerModeChange(player.getUsername(), oldMode, newMode);
        } else if (session == null) {
            ServerLogger.get().playerMode(player.getUsername(), newDiff.displayName());
        }

        if (needsRestart) {
            parkourService.restart(player, newDiff, newTheme, newDim, newTraining, newSound);
            player.sendMessage(ParkourService.PARKOUR_TEXT.append(
                    Component.text("Забег перезапущен с новыми настройками.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
        } else {
            if (themeChanged) {
                parkourService.changeTheme(player, newTheme);
                player.sendMessage(ParkourService.PARKOUR_TEXT.append(
                        Component.text("Тема: ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(newTheme.displayName(), newTheme.color()))));
            }
            if (dimChanged) {
                parkourService.changeDimension(player, newDim);
            }
        }
    }

    // ── Slot mappings ───────────────────────────────────────────────────────

    private static ParkourDifficulty difficultyForSlot(int slot) {
        return switch (slot) {
            case DIFF_NORMAL_SLOT       -> ParkourDifficulty.NORMAL;
            case DIFF_HARD_SLOT         -> ParkourDifficulty.HARD;
            case DIFF_EXTREME_SLOT      -> ParkourDifficulty.EXTREME;
            case DIFF_COMPETITIVE_SLOT  -> ParkourDifficulty.COMPETITIVE;
            default -> null;
        };
    }

    private static RegistryKey<DimensionType> dimensionForSlot(int slot) {
        return switch (slot) {
            case DIM_OVERWORLD_SLOT -> DimensionType.OVERWORLD;
            case DIM_NETHER_SLOT    -> DimensionType.THE_NETHER;
            case DIM_END_SLOT       -> DimensionType.THE_END;
            default -> null;
        };
    }

    // ── Item builders ───────────────────────────────────────────────────────

    private static ItemStack difficultyItem(ParkourDifficulty diff, Material material, ParkourDifficulty current) {
        boolean selected = diff == current;
        Component name = Component.text(diff.displayName(), diff.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        TextColor statsColor = diff.countsForStats() ? TextColor.color(0x55E068) : TextColor.color(0xB0A89E);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreText(diff.description()));
        lore.add(Component.text(diff.statsNote(), statsColor).decoration(TextDecoration.ITALIC, false));
        if (selected) {
            lore.add(Component.empty());
            lore.add(selectedLabel());
        }

        var builder = ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip();
        if (selected) builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return builder.build();
    }

    private static ItemStack themeButton(ParkourTheme current) {
        Component name = Component.text("Тема блоков ", ACCENT)
                .append(Component.text("▸", NamedTextColor.GRAY))
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = List.of(
                Component.empty(),
                loreText("Сейчас: " + current.displayName()),
                Component.empty(),
                hintText("Нажми для выбора")
        );

        return ItemStack.builder(current.material())
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip().build();
    }

    private static ItemStack themeSubItem(ParkourTheme theme, ParkourTheme current) {
        boolean selected = theme == current;
        Component name = Component.text(theme.displayName(), theme.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreText(theme.description()));
        if (selected) {
            lore.add(Component.empty());
            lore.add(selectedLabel());
        }

        var builder = ItemStack.builder(theme.material())
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip();
        if (selected) builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return builder.build();
    }

    private static ItemStack dimensionItem(Material material, String name, String desc,
                                           TextColor color, boolean selected) {
        Component displayName = Component.text(name, color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreText(desc));
        if (selected) {
            lore.add(Component.empty());
            lore.add(selectedLabel());
        }

        var builder = ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, displayName)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip();
        if (selected) builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return builder.build();
    }

    private static ItemStack soundItem(ParkourSoundPreset preset) {
        Component name = Component.text("Звуки: ", ACCENT)
                .append(Component.text(preset.displayName(), preset.color()))
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = List.of(
                Component.empty(),
                loreText(preset.description()),
                Component.empty(),
                hintText("Нажми для переключения")
        );

        return ItemStack.builder(preset.material())
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip().build();
    }

    private static ItemStack trainingItem(boolean enabled) {
        Material  material   = enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        TextColor stateColor = enabled ? GREEN : RED;
        String    stateText  = enabled ? "Включена" : "Выключена";

        Component name = Component.text("Тренировка: ", ACCENT)
                .append(Component.text(stateText, stateColor))
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (enabled) {
            lore.add(loreText("Возврат при падении."));
            lore.add(loreText("Очки не записываются."));
        } else {
            lore.add(loreText("Падение завершает забег."));
            lore.add(loreText("Очки записываются."));
        }
        lore.add(Component.empty());
        lore.add(hintText("Нажми для переключения"));

        var builder = ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip();
        if (enabled) builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return builder.build();
    }

    private static ItemStack musicItem(boolean enabled) {
        Material  material   = enabled ? Material.MUSIC_DISC_CAT : Material.MUSIC_DISC_BLOCKS;
        TextColor stateColor = enabled ? GREEN : RED;
        String    stateText  = enabled ? "Включена" : "Выключена";

        Component name = Component.text("Музыка: ", ACCENT)
                .append(Component.text(stateText, stateColor))
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = List.of(
                Component.empty(),
                loreText("Фоновая музыка лобби."),
                Component.empty(),
                hintText("Нажми для переключения")
        );

        return ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip().build();
    }

    private ItemStack applyItem(ParkourSession session, UUID uuid) {
        if (session == null) return APPLY_NEW;

        boolean diffChanged     = getDifficulty(uuid)   != session.getDifficulty();
        boolean themeChanged    = getTheme(uuid)        != session.getTheme();
        boolean dimChanged      = getDimension(uuid)    != session.getCurrentDimension();
        boolean trainingChanged = isTrainingMode(uuid)  != session.isTrainingMode();
        boolean soundChanged    = getSoundPreset(uuid)  != session.getSoundPreset();

        if (!diffChanged && !themeChanged && !dimChanged && !trainingChanged && !soundChanged) return APPLY_SAME;
        if (diffChanged || trainingChanged) return APPLY_RESTART;
        return APPLY_CHANGE;
    }

    private static ItemStack applyButton(String title, TextColor color, String desc, Material material) {
        Component name = Component.text(title, color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = List.of(
                Component.empty(),
                loreText(desc)
        );

        return ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip().build();
    }

    private static Component loreText(String text) {
        return Component.text(text, LORE_WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private static Component hintText(String text) {
        return Component.text(text, HINT_COLOR).decoration(TextDecoration.ITALIC, false);
    }

    private static final Component SELECTED_LABEL =
            Component.text("✔ Выбрано", GREEN).decoration(TextDecoration.ITALIC, false);

    private static Component selectedLabel() {
        return SELECTED_LABEL;
    }

    // ── Pre-built apply variants ─────────────────────────────────────────────

    private static final ItemStack APPLY_NEW     = applyButton("▶ Применить",           GREEN,                        "Начнёт новый забег.",                  Material.EMERALD);
    private static final ItemStack APPLY_SAME    = applyButton("— Без изменений",        TextColor.color(0xB0A89E),    "Настройки совпадают.",                 Material.GRAY_DYE);
    private static final ItemStack APPLY_RESTART = applyButton("⟳ Перезапустить забег", ACCENT,                       "Сменит настройки и начнёт заново.",    Material.FIRE_CHARGE);
    private static final ItemStack APPLY_CHANGE  = applyButton("✔ Применить",            GREEN,                        "Изменения без перезапуска.",           Material.EMERALD);

    // ── Pre-built static sub-menu items ─────────────────────────────────────

    private static final ItemStack MUSIC_SELECT_ITEM_STATIC = buildMusicSelectItem();
    private static final ItemStack BACK_BUTTON_ITEM = buildBackButton();

    private static ItemStack musicSelectItem() {
        return MUSIC_SELECT_ITEM_STATIC;
    }

    private static ItemStack backButton() {
        return BACK_BUTTON_ITEM;
    }

    private static ItemStack buildMusicSelectItem() {
        Component name = Component.text("Выбрать трек ", ACCENT)
                .append(Component.text("▸", NamedTextColor.GRAY))
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = List.of(
                Component.empty(),
                loreText("Открывает список треков"),
                loreText("для выбора конкретной музыки."),
                Component.empty(),
                hintText("Нажми для выбора")
        );

        return ItemStack.builder(Material.JUKEBOX)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip().build();
    }

    private static ItemStack buildBackButton() {
        Component name = Component.text("◂ Назад", ua.vsevolod.lobby.config.LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        return ItemStack.builder(Material.ARROW)
                .set(DataComponents.CUSTOM_NAME, name)
                .hideExtraTooltip().build();
    }

    // ── Hotbar items ────────────────────────────────────────────────────────

    static final ItemStack LEAVE_ITEM = buildLeaveItem();
    static final ItemStack SETTINGS_ITEM = buildSettingsItem();

    static ItemStack createLeaveItem() { return LEAVE_ITEM; }
    static ItemStack createItem()      { return SETTINGS_ITEM; }

    private static ItemStack buildLeaveItem() {
        Component name = Component.text("Покинуть паркур", RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = List.of(
                Component.empty(),
                loreText("Вернуться в лобби.")
        );

        return ItemStack.builder(Material.BARRIER)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(LEAVE_ITEM_TAG, "leave")
                .hideExtraTooltip().build();
    }

    private static ItemStack buildSettingsItem() {
        Component name = Component.text()
                .append(Text.c("<gradient:#AE3AF3:#985DBC><bold>Настройки</bold></gradient>"))
                .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                .append(Component.text("ПКМ", TextColor.color(0x8EB126)))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false)
                .build();

        List<Component> lore = List.of(
                Component.empty(),
                loreText("Сложность, тема, мир, звуки"),
                loreText("и перезапуск забега.")
        );

        return ItemStack.builder(Material.NETHER_STAR)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(SETTINGS_ITEM_TAG, "parkour")
                .hideExtraTooltip().build();
    }
}
