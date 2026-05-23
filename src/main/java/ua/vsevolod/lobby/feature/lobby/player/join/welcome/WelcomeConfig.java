package ua.vsevolod.lobby.feature.lobby.player.join.welcome;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Welcome banner + animated-title config, backed by {@code config/welcome.yml}.
 *
 * <p>ConfigLib-powered ({@code @Configuration}) — missing keys are auto-filled with the field
 * defaults and the file is rewritten with the {@link Comment} text. Hot-reloadable: registered
 * with {@link ConfigReload} so {@code /reload} re-reads it.</p>
 *
 * <p>Text fields accept legacy {@code &} colour codes and {@code &#RRGGBB} hex. Placeholders:
 * {@code {player}}, {@code {discord}}, {@code {telegram}}, {@code {studio}} in chat lines;
 * {@code {player}}, {@code {days}}, {@code {days_phrase}} in the title/subtitle.</p>
 */
@Configuration
public final class WelcomeConfig {

    private static final Path FILE = Paths.get("config", "ui", "welcome.yml");
    private static volatile WelcomeConfig instance;

    @Comment({
            "Строки приветствия — отправляются в чат при входе на лобби.",
            "Базовый белый проекта — <#FFF2E0>.",
            "Плейсхолдеры: {player} {discord} {telegram} {studio}.",
            "URL-плейсхолдеры читаются из единого файла config/socials.yml."
    })
    public List<String> chatLines = List.of(
            "",
            "     <gradient:#AE3AF3:#C58AF0><bold>ᴏʀᴊᴜꜱ-ꜱᴛᴜᴅɪᴏ</bold></gradient>  <dark_gray>›<#FFF2E0> Привет, <#C58AF0><bold>{player}</bold><#FFF2E0>!",
            "     <#D8CCDE>Главное лобби <dark_gray>·<#D8CCDE> здесь начинается всё интересное",
            "",
            "  <#C58AF0>❖<#FFF2E0> Меню режимов  <dark_gray>—<#C58AF0> /menu <dark_gray>или <#C58AF0>компас в руке",
            "  <#C58AF0>❖<#FFF2E0> Паркур        <dark_gray>—<#C58AF0> NPC у фонтана",
            "",
            "     <#9C93B0>Будь с нами:  "
                    + "<click:open_url:'{discord}'><hover:show_text:'<#5865F2>Discord<#FFF2E0> · сообщество, поддержка, ивенты'><#5865F2>◆ <underlined>Discord</underlined></hover></click>"
                    + "  <dark_gray>·  "
                    + "<click:open_url:'{telegram}'><hover:show_text:'<#3FA7E0>Telegram<#FFF2E0> · новости и анонсы'><#3FA7E0>◆ <underlined>Telegram</underlined></hover></click>"
                    + "  <dark_gray>·  "
                    + "<click:open_url:'{studio}'><hover:show_text:'<#C58AF0>orjus.ru<#FFF2E0> · официальный сайт студии'><#C58AF0>◆ <underlined>Сайт</underlined></hover></click>",
            ""
    );

    @Comment("Показывать ли анимированный титул на входе.")
    public boolean titleEnabled = true;

    @Comment({
            "Титул / подзаголовок для НОВЫХ игроков (первый заход).",
            "Цвет букв титула задаётся градиентом ниже; цветовые теги в title-строке стираются —",
            "используй только декорации (<bold>/<italic>/<underlined>). В subtitle цвета работают как обычно."
    })
    public String firstJoinTitle = "<bold>Добро пожаловать";
    public String firstJoinSubtitle = "<#FFF2E0>✦ Впервые на сервере — приятной игры!";

    @Comment({
            "Титул / подзаголовок для ПОВТОРНЫХ заходов.",
            "{days_phrase} сам подставит форму: 'первый день' / '1 день' / 'X дня' / 'X дней'."
    })
    public String returningTitle = "<bold>С возвращением";
    public String returningSubtitle = "<#FFF2E0>Ты с нами уже <#B5D85E><bold>{days_phrase}<#FFF2E0> ☀";

    @Comment("Палитра градиента титула (HEX) — буквы переливаются между этими цветами.")
    public String gradientStartHex = "#C58AF0";
    public String gradientEndHex = "#AE3AF3";

    @Comment("Общая длительность видимости титула (тиков, 20 = 1 сек).")
    public int durationTicks = 80;

    @Comment("Интервал между кадрами анимации (тиков). 2 = 10 fps.")
    public int frameTicks = 2;

    @Comment("Скорость движения волны градиента (фаза за кадр).")
    public double waveSpeed = 0.45;

    @Comment("Период волны в \"буквах\" — меньше число = чаще полоса.")
    public double wavePeriod = 4.0;

    public static WelcomeConfig get() {
        WelcomeConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized WelcomeConfig load() {
        WelcomeConfig c;
        try {
            c = YamlConfigurations.update(FILE, WelcomeConfig.class);
        } catch (Exception e) {
            System.err.println("[WelcomeConfig] Failed to load " + FILE + ": " + e.getMessage() + " — using defaults");
            c = new WelcomeConfig();
        }
        instance = c;
        return c;
    }

    /** Load now and wire {@code /reload} support. Call once from bootstrap. */
    public static void init() {
        load();
        ConfigReload.register("welcome", WelcomeConfig::load);
    }
}
