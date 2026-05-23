package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import net.minestom.server.map.framebuffers.Graphics2DFramebuffer;
import net.minestom.server.network.packet.server.play.MapDataPacket;
import ua.vsevolod.lobby.util.ServerLogger;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Renders the off-hand socials map.
 *
 * <p>Two sources, in priority order:</p>
 * <ol>
 *   <li><b>Custom image</b> — when {@code qr-card.yml: image_file} points at an existing file
 *       on disk, that image is loaded and pasted onto the map. Admins can swap in their own
 *       branded QR without rebuilding the jar.</li>
 *   <li><b>Generated QR</b> — otherwise a plain black-on-white QR is generated from the
 *       configured {@code qr_url} straight off the zxing bit-matrix.</li>
 * </ol>
 *
 * <p>A Minecraft map only stores indices into a fixed 143-colour palette, so any colour that
 * isn't a palette colour is mis-quantized on render and looks distorted. Pure black/white
 * (the generated QR) is lossless; a custom <i>colour</i> image will still shift toward the
 * palette — for a clean result a custom image should also be black/white.</p>
 */
public final class LobbyQrMapRenderer {

    private static final int MAP_SIZE = 128;

    private LobbyQrMapRenderer() {
    }

    /**
     * @param mapId     map id to bind the texture to
     * @param text      URL to encode when no custom image is used
     * @param imageFile path to a custom image, or blank/null to generate a QR from {@code text}
     */
    public static MapDataPacket createPacket(int mapId, String text, String imageFile) {
        BufferedImage image = loadCustomImage(imageFile);
        if (image == null) {
            image = renderQr(text, MAP_SIZE, MAP_SIZE);
        }

        Graphics2DFramebuffer framebuffer = new Graphics2DFramebuffer();
        Graphics2D g = framebuffer.getRenderer();
        // 1:1 pixel copy — nearest-neighbour, no AA. Any interpolation here would invent
        // grey in-between pixels that the map palette then mis-quantizes.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.drawImage(image, 0, 0, MAP_SIZE, MAP_SIZE, null);
        g.dispose();
        return framebuffer.preparePacket(mapId);
    }

    /**
     * Load an admin-supplied image from disk; null if not configured or unreadable.
     *
     * <p>The configured path is tried as-is (relative to the server working directory), then
     * under {@code config/} — so an admin can drop the file next to {@code server.jar} or
     * into the config folder and it just works.</p>
     */
    private static BufferedImage loadCustomImage(String imageFile) {
        if (imageFile == null || imageFile.isBlank()) return null;

        Path resolved = null;
        for (Path candidate : new Path[]{Path.of(imageFile), Path.of("config").resolve(imageFile)}) {
            if (Files.isRegularFile(candidate)) {
                resolved = candidate;
                break;
            }
        }
        if (resolved == null) {
            ServerLogger.get().warn("QR card image_file '" + imageFile
                    + "' not found (looked in server root and config/) — falling back to generated QR.");
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(resolved.toFile());
            if (img == null) {
                ServerLogger.get().warn("QR card image_file '" + resolved
                        + "' is not a readable image — falling back to generated QR.");
            } else {
                ServerLogger.get().info("QR card using custom image: " + resolved);
            }
            return img;
        } catch (Exception e) {
            ServerLogger.get().warn("Failed to read QR card image_file '" + resolved
                    + "': " + e.getMessage() + " — falling back to generated QR.");
            return null;
        }
    }

    /** Plain black-on-white QR straight from the bit-matrix — no styling, no colour choices. */
    public static BufferedImage renderQr(String text, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return image;
        } catch (Exception e) {
            throw new RuntimeException("Failed to render QR", e);
        }
    }
}
