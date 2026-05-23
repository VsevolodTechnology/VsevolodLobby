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

    private static final Path FILE = Paths.get("config", "qr-card.yml");
    private static volatile QrCardConfig instance;

    @Comment("Название предмета-карты в руке.")
    public String itemName = "<#AE3AF3><bold>Социальные сети";

    @Comment("Описание (lore) предмета-карты под названием. Пустой список = без описания.")
    public List<String> itemLore = List.of(
            " ",
            "<#65D1FC> «Информация»",
            "<gray> - <#FFF2E0>Все ссылки на наши соцсети,",
            "<gray> - <#FFF2E0>новости, анонсы и розыгрыши.",
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

    @Comment("URL для генерации QR, если imageFile пустой.")
    public String qrUrl = "https://studio.orjus.ru/";

    @Comment("Кулдаун между показами сообщения одним игроком (мс).")
    public long cooldownMs = 1500L;

    @Comment("Первая строка сообщения в чате.")
    public String header = "  <gradient:#AE3AF3:#C58AF0><bold>Наши социальные сети</bold></gradient>";

    @Comment({
            "Шаблон строки ссылки. Плейсхолдеры: {color} {label} {hint} {url}.",
            "Вся строка кликабельна — ссылка открывается кликом, полный URL писать не нужно."
    })
    public String linkFormat = "   {color}<underlined>{label}</underlined> <dark_gray>— <#9C93B0>{hint}";

    @Comment("Подсказка при наведении на ссылку.")
    public String hoverText = "<#C58AF0>▶ <#FFF2E0>Открыть в браузере";

    @Comment({
            "Список соцсетей. url — чистая ссылка (по ней переход);",
            "color — цвет иконки (MiniMessage-тег); hint — короткое описание."
    })
    public List<SocialLink> links = List.of(
            new SocialLink("Discord",  "https://discord.com/invite/BNCXWbHRsC", "<#5865F2>", "наше сообщество"),
            new SocialLink("Telegram", "https://t.me/OrjusTg",                  "<#3FA7E0>", "новости и анонсы"),
            new SocialLink("Сайт",     "https://studio.orjus.ru/",              "<#C58AF0>", "наш сайт")
    );

    /** One social entry: label, the bare URL it opens, an icon colour and a short hint. */
    public record SocialLink(String label, String url, String color, String hint) {
        public SocialLink {
            if (color == null) color = "<#C58AF0>";
            if (hint == null) hint = "";
        }
    }

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
