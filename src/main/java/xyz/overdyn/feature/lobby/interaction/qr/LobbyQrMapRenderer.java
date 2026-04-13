package xyz.overdyn.feature.lobby.interaction.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import net.minestom.server.map.framebuffers.Graphics2DFramebuffer;
import net.minestom.server.network.packet.server.play.MapDataPacket;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class LobbyQrMapRenderer {

    private static final int MAP_SIZE = 128;

    private LobbyQrMapRenderer() {
    }

    public static MapDataPacket createPacket(int mapId, String text) {
        BufferedImage image = renderPrettyQr(text, MAP_SIZE, MAP_SIZE, false);
        Graphics2DFramebuffer framebuffer = new Graphics2DFramebuffer();
        Graphics2D g = framebuffer.getRenderer();

        g.drawImage(image, 0, 0, MAP_SIZE, MAP_SIZE, null);
        g.dispose();

        return framebuffer.preparePacket(mapId);
    }

    public static BufferedImage renderPrettyQr(String text, int width, int height, boolean drawCenterLogo) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2); // quiet zone
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            int matrixWidth = matrix.getWidth();
            int matrixHeight = matrix.getHeight();

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Color bg = new Color(0xFF, 0xF8, 0xE7);      // warm white
            Color fg = new Color(0x1E, 0x29, 0x3B);      // dark slate
            Color eyeOuter = new Color(0xF5, 0x9E, 0x0B); // amber
            Color eyeInner = new Color(0x1E, 0x29, 0x3B);

            g.setColor(bg);
            g.fillRect(0, 0, width, height);

            double cellW = (double) width / matrixWidth;
            double cellH = (double) height / matrixHeight;
            double dotScale = 0.82;

            // Сначала обычные модули, кроме finder patterns
            for (int y = 0; y < matrixHeight; y++) {
                for (int x = 0; x < matrixWidth; x++) {
                    if (!matrix.get(x, y)) continue;
                    if (isInsideFinder(x, y, matrixWidth, matrixHeight)) continue;

                    double px = x * cellW;
                    double py = y * cellH;
                    double dw = cellW * dotScale;
                    double dh = cellH * dotScale;
                    double ox = px + (cellW - dw) / 2.0;
                    double oy = py + (cellH - dh) / 2.0;

                    g.setColor(fg);
                    g.fill(new RoundRectangle2D.Double(
                            ox, oy, dw, dh,
                            Math.min(dw, dh) * 0.55,
                            Math.min(dw, dh) * 0.55
                    ));
                }
            }

            // Красивые finder patterns
            drawFinder(g, 0, 0, cellW, cellH, eyeOuter, eyeInner, bg);
            drawFinder(g, matrixWidth - 7, 0, cellW, cellH, eyeOuter, eyeInner, bg);
            drawFinder(g, 0, matrixHeight - 7, cellW, cellH, eyeOuter, eyeInner, bg);

            // Маленький центр-бейдж. Для карты лучше выключить.
            if (drawCenterLogo) {
                int badgeSize = 14; // больше уже рискованно для 128x128
                int cx = width / 2 - badgeSize / 2;
                int cy = height / 2 - badgeSize / 2;

                g.setColor(Color.WHITE);
                g.fillRoundRect(cx - 2, cy - 2, badgeSize + 4, badgeSize + 4, 6, 6);

                g.setColor(new Color(0xF5, 0x9E, 0x0B));
                g.fillRoundRect(cx, cy, badgeSize, badgeSize, 5, 5);

                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                FontMetrics fm = g.getFontMetrics();
                String s = "HW";
                int tx = cx + (badgeSize - fm.stringWidth(s)) / 2;
                int ty = cy + ((badgeSize - fm.getHeight()) / 2) + fm.getAscent();
                g.drawString(s, tx, ty);
            }

            g.dispose();
            return image;
        } catch (Exception e) {
            throw new RuntimeException("Failed to render QR", e);
        }
    }

    private static boolean isInsideFinder(int x, int y, int w, int h) {
        return inBox(x, y, 0, 0, 7, 7)
                || inBox(x, y, w - 7, 0, 7, 7)
                || inBox(x, y, 0, h - 7, 7, 7);
    }

    private static boolean inBox(int x, int y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    private static void drawFinder(Graphics2D g, int mx, int my, double cellW, double cellH,
                                   Color outer, Color inner, Color bg) {
        double x = mx * cellW;
        double y = my * cellH;
        double w = 7 * cellW;
        double h = 7 * cellH;

        // outer
        g.setColor(outer);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, cellW * 2.2, cellH * 2.2));

        // ring gap
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Double(
                x + cellW, y + cellH,
                w - cellW * 2, h - cellH * 2,
                cellW * 1.6, cellH * 1.6
        ));

        // inner ring
        g.setColor(outer);
        g.fill(new RoundRectangle2D.Double(
                x + cellW * 1.5, y + cellH * 1.5,
                w - cellW * 3, h - cellH * 3,
                cellW * 1.2, cellH * 1.2
        ));

        // center dot
        g.setColor(inner);
        g.fill(new RoundRectangle2D.Double(
                x + cellW * 2.5, y + cellH * 2.5,
                cellW * 2, cellH * 2,
                cellW, cellH
        ));
    }
}
