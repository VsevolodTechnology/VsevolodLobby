package ua.vsevolod.lobby.feature.lobby.player.compat;

/**
 * Static lookup table of client-protocol → feature support.
 * <p>Pure utility: no state, no Player references. Add new feature predicates here when needed,
 * keep each method a one-line numeric comparison so the table stays auditable.</p>
 *
 * <p>Protocol numbers reference:
 * <a href="https://minecraft.wiki/w/Protocol_version">minecraft.wiki/w/Protocol_version</a></p>
 */
public final class ClientCapabilities {

    public static final int PROTOCOL_1_8       = 47;
    public static final int PROTOCOL_1_13      = 393;
    public static final int PROTOCOL_1_19_4    = 762;
    public static final int PROTOCOL_1_20_2    = 764;
    public static final int PROTOCOL_1_20_5    = 766;
    public static final int PROTOCOL_1_21      = 767;

    private ClientCapabilities() {}

    /** {@code minecraft:text_display} entity was introduced in 1.19.4. */
    public static boolean supportsTextDisplay(int protocol) {
        return protocol >= PROTOCOL_1_19_4;
    }

    /** ArmorStand marker flag (0x10) is available since 1.8. */
    public static boolean supportsArmorStandMarker(int protocol) {
        return protocol >= PROTOCOL_1_8;
    }

    /** {@code minecraft:Transfer} clientbound packet — server-initiated server switch. */
    public static boolean supportsTransfer(int protocol) {
        return protocol >= PROTOCOL_1_20_5;
    }

    /** Login/Configuration phase split (LoginAck → CONFIGURATION). */
    public static boolean supportsConfigurationPhase(int protocol) {
        return protocol >= PROTOCOL_1_20_2;
    }
}
