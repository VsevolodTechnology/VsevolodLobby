package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Off-hand QR "Социальные сети" card config, backed by {@code config/qr-card.yml}.
 *
 * <p>ConfigLib-powered ({@code @Configuration}), hot-reloadable via {@link ConfigReload}.
 * Every player-facing string is here — nothing is hard-coded to a colour, so the card can be
 * re-themed without touching code. Text fields accept legacy {@code &} and {@code &#RRGGBB}.</p>
 *
 * <p>{@link #imageFile} / {@link #qrUrl} are baked into the map texture at server start —
 * changing them needs a restart; the chat-message strings hot-reload normally.</p>
 */
@Configuration
public final class QrCardConfig {

    private static final Path FILE = Paths.get("config", "ui", "qr-card.yml");
    private static volatile QrCardConfig instance;

    @Comment("Название предмета-карты в руке.")
    public String itemName = "<#AE3AF3><bold>Социальные сети";

    @Comment("Описание (lore) предмета-карты под названием. Пустой список = без описания.")
    public List<String> itemLore = List.of(
            " ",
            "<#C58AF0> «Информация»",
            "<dark_gray> ›<#FFF2E0> Все ссылки на наши соцсети,",
            "<dark_gray> ›<#FFF2E0> новости, анонсы и розыгрыши.",
            " ",
            "<#C58AF0>➥ ПКМ — показать ссылки"
    );

    @Comment({
            "Своя картинка QR — путь к файлу на сервере (png/jpg).",
            "Если указан и файл существует — берётся ОН (qr_url игнорируется).",
            "Если пусто — генерируется чёрно-белый QR из qrUrl.",
            "Совет: бери ч/б картинку — цветная исказится палитрой карты (143 цвета)."
    })
    public String imageFile = "";

    @Comment({
            "URL для генерации QR, если imageFile пустой.",
            "Можно указать плейсхолдер {discord}/{telegram}/{website} — подставится из socials.yml."
    })
    public String qrUrl = "{website}";

    @Comment("Кулдаун между показами сообщения одним игроком (мс).")
    public long cooldownMs = 1500L;

    @Comment({
            "Шапка сообщения. Используй <newline> для переноса строк.",
            "Базовый белый — <#FFF2E0>, акцент — <#C58AF0> / <#AE3AF3>."
    })
    public String header =
            "     <gradient:#AE3AF3:#C58AF0><bold>Наши социальные сети</bold></gradient>"
                    + "<newline>"
                    + "     <#D8CCDE>Будь с нами на связи — выбери, куда заглянуть.";

    @Comment("Подвал сообщения. Пусто = без подвала. <newline> — перенос строки.")
    public String footer = "";

    @Comment({
            "Шаблон строки ссылки. Плейсхолдеры: {color} {label} {hint} {url}.",
            "Вся строка кликабельна — ссылка открывается кликом, полный URL писать не нужно."
    })
    public String linkFormat = "   {color}◆ <underlined>{label}</underlined>  <dark_gray>—  <#FFF2E0>{hint}";

    @Comment({
            "Запасной hover, если у конкретной ссылки в socials.yml hover не задан.",
            "Базовый текст подсказки — БЕЗ URL, чтобы не показывать ссылку."
    })
    public String hoverText = "<#C58AF0>▶ <#FFF2E0>Кликни, чтобы открыть";

    public static QrCardConfig get() {
        QrCardConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized QrCardConfig load() {
        QrCardConfig c;
        try {
            c = YamlConfigurations.update(FILE, QrCardConfig.class);
        } catch (Exception e) {
            System.err.println("[QrCardConfig] Failed to load " + FILE + ": " + e.getMessage() + " — using defaults");
            c = new QrCardConfig();
        }
        instance = c;
        return c;
    }

    /** Load now and wire {@code /reload} support. Call once from bootstrap. */
    public static void init() {
        load();
        ConfigReload.register("qr-card", QrCardConfig::load);
    }
}
