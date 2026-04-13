package xyz.overdyn.feature.lobby.ui.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.config.server.ServerInfo;
import xyz.overdyn.config.server.ServerRegistry;
import xyz.overdyn.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class LobbyModeSelectorMenu {

    private static final int GRIEF_SLOT = 22;

    private static final int[] ORANGE_DECOR_SLOTS = {
            1, 3, 5, 7,
            9, 11, 15, 17,
            18, 19, 25, 26,
            27, 29, 33, 35,
            37, 39, 41, 43
    };

    private static final int[] BLACK_DECOR_SLOTS = {
            0, 2, 4, 6, 8,
            10, 12, 13, 14, 16,
            20, 24,
            28, 30, 31, 32, 34,
            36, 38, 40, 42, 44
    };

    private static final int[] GRAY_DECOR_SLOTS = {
            21, 23
    };

    private final Inventory menu;
    private final LobbyModeSelectionService selectionService;

    public LobbyModeSelectorMenu(GlobalEventHandler eventHandler) {
        this.selectionService = new LobbyModeSelectionService();
        this.menu = createMainMenu();

        eventHandler.addListener(InventoryPreClickEvent.class, event -> {
            if (event.getInventory() == null || !event.getInventory().equals(menu)) {
                return;
            }

            event.setCancelled(true);

            if (event.getSlot() == GRIEF_SLOT) {
                ServerRegistry.findById("grief.1.16x")
                        .ifPresent(server -> selectionService.selectServer(event.getPlayer(), server));
            }
        });
    }

    public Inventory getMenu() {
        return menu;
    }

    private Inventory createMainMenu() {
        Inventory inventory = new Inventory(InventoryType.CHEST_5_ROW, Text.c("&8Выбор режима"));

        fillDecoration(inventory, ORANGE_DECOR_SLOTS, Material.ORANGE_STAINED_GLASS_PANE);
        fillDecoration(inventory, BLACK_DECOR_SLOTS, Material.BLACK_STAINED_GLASS_PANE);
        fillDecoration(inventory, GRAY_DECOR_SLOTS, Material.GRAY_STAINED_GLASS_PANE);

        inventory.setItemStack(GRIEF_SLOT, createServerItem(
                ServerRegistry.findById("grief.1.16x").orElseThrow()
        ));

        return inventory;
    }

    private void fillDecoration(Inventory inventory, int[] slots, Material material) {
        ItemStack item = ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, Text.c(""))
                .hideExtraTooltip()
                .build();

        for (int slot : slots) {
            inventory.setItemStack(slot, item);
        }
    }

    private static final String COLOR_TITLE = "&#C86E6E";
    private static final String COLOR_LABEL = "&8› " + LobbyConfig.Project.WHITE_COLOR_ORIGINAL;
    private static final String COLOR_VALUE = "&#E39966";
    private static final String COLOR_MUTED = "&8";
    private static final String COLOR_BAR_BORDER = "&8";
    private static final String COLOR_BAR_FILLED_LOW = "&#C86E6E";
    private static final String COLOR_BAR_FILLED_MEDIUM = "&#E39966";
    private static final String COLOR_BAR_FILLED_HIGH = "&#8FAE8B";
    private static final String COLOR_BAR_EMPTY = "&#4A4A4A";
    private static final String COLOR_LINK = "&#D8B07A";

    private ItemStack createServerItem(
            ServerInfo server
    ) {
        List<Component> lore = new ArrayList<>();

        lore.add(line("  " + COLOR_TITLE + "«Сервер»"));
        lore.add(line("   " + COLOR_LABEL + "Мир: " + COLOR_VALUE + server.worldName()));
        lore.add(line("   " + COLOR_LABEL + "Состояние: " + server.getStatusName()));
        lore.add(line("   " + COLOR_LABEL + "Онлайн: " + COLOR_VALUE + server.online() + "/" + server.maxOnline() + " " + buildOnlineBar(server.online(), server.maxOnline(), 5)));

        lore.add(line(""));
        lore.add(line("  " + COLOR_TITLE + "«Информация»"));
        lore.add(line("   " + COLOR_LABEL + "Ядро: " + COLOR_VALUE + server.versionCore()));

        List<String> tagLines = wrapTags(Arrays.asList(server.tagsServer()), 3, 28);
        if (!tagLines.isEmpty()) {
            lore.add(line(""));
            lore.add(line("  " + COLOR_TITLE + "«Теги»"));
            for (String tagLine : tagLines) {
                lore.add(line("   " + tagLine));
            }
        }

        lore.add(line(""));
        lore.add(line("  " + COLOR_TITLE + "«Соц.сети»"));
        lore.add(line("   " + COLOR_LABEL + "Сайт: " + COLOR_LINK + LobbyConfig.Project.SOCIAL_LINKS.website()));
        lore.add(line("   " + COLOR_LABEL + "TG: " + COLOR_LINK + LobbyConfig.Project.SOCIAL_LINKS.telegram()));
        lore.add(line("   " + COLOR_LABEL + "DS: " + COLOR_LINK + LobbyConfig.Project.SOCIAL_LINKS.discord()));

        lore.add(line(""));
        lore.add(line("  " + "&#E39966> " + LobbyConfig.Project.WHITE_COLOR_ORIGINAL + "Нажми, чтобы подключиться"));

        return ItemStack.builder(server.material())
                .set(DataComponents.CUSTOM_NAME, Text.c(""))
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip()
                .build();
    }

    private Component line(String raw) {
        return Text.c(raw).decoration(TextDecoration.ITALIC, false);
    }

    private String buildOnlineBar(int online, int maxOnline, int totalBars) {
        if (totalBars <= 0) {
            totalBars = 5;
        }

        if (maxOnline <= 0) {
            return COLOR_BAR_BORDER + "[" + COLOR_BAR_EMPTY + "□".repeat(totalBars) + COLOR_BAR_BORDER + "]";
        }

        double percent = Math.max(0D, Math.min(1D, (double) online / maxOnline));
        int filledBars = (int) Math.round(percent * totalBars);

        if (online > 0 && filledBars == 0) {
            filledBars = 1;
        }

        if (filledBars > totalBars) {
            filledBars = totalBars;
        }

        String fillColor = getOnlineFillColor(percent);

        StringBuilder builder = new StringBuilder();
        builder.append(COLOR_BAR_BORDER).append("[");

        for (int i = 0; i < filledBars; i++) {
            builder.append(fillColor).append("■");
        }

        for (int i = filledBars; i < totalBars; i++) {
            builder.append(COLOR_BAR_EMPTY).append("□");
        }

        builder.append(COLOR_BAR_BORDER).append("]");
        return builder.toString();
    }

    private String getOnlineFillColor(double percent) {
        if (percent >= 0.75D) {
            return COLOR_BAR_FILLED_HIGH;
        }
        if (percent >= 0.35D) {
            return COLOR_BAR_FILLED_MEDIUM;
        }
        return COLOR_BAR_FILLED_LOW;
    }

    private List<String> wrapTags(List<String> tags, int maxLines, int maxVisibleLength) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        List<String> normalized = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .map(tag -> tag.startsWith("#") ? tag : "#" + tag)
                .toList();

        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String tag : normalized) {
            String coloredTag = COLOR_VALUE + tag;
            String separator = current.isEmpty() ? "" : " " + COLOR_MUTED + "• ";
            String candidate = current + separator + coloredTag;

            if (!current.isEmpty() && visibleLength(candidate) > maxVisibleLength) {
                lines.add(current.toString());
                if (lines.size() >= maxLines) {
                    return lines;
                }
                current = new StringBuilder(coloredTag);
                continue;
            }

            current = new StringBuilder(candidate);
        }

        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current.toString());
        }

        return lines;
    }

    private int visibleLength(String input) {
        return input
                .replaceAll("&#[A-Fa-f0-9]{6}", "")
                .replaceAll("&[0-9A-Fa-fk-orK-OR]", "")
                .length();
    }
}