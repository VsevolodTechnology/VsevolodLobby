package xyz.overdyn.util;

public final class MinecraftFontMetrics {

    private MinecraftFontMetrics() {
    }

    public static int width(String text) {
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            width += charWidth(text.charAt(index));
        }
        return width;
    }

    public static String trimToWidth(String text, int maxWidth, String suffix) {
        if (width(text) <= maxWidth) {
            return text;
        }

        int suffixWidth = width(suffix);
        int currentWidth = 0;
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < text.length(); index++) {
            char currentChar = text.charAt(index);
            int charWidth = charWidth(currentChar);
            if (currentWidth + charWidth + suffixWidth > maxWidth) {
                break;
            }

            builder.append(currentChar);
            currentWidth += charWidth;
        }

        return builder.append(suffix).toString();
    }

    public static String padStartToWidth(String text, int targetWidth) {
        int currentWidth = width(text);
        if (currentWidth >= targetWidth) {
            return text;
        }

        StringBuilder builder = new StringBuilder();
        int spaceWidth = charWidth(' ');
        while (currentWidth + spaceWidth <= targetWidth) {
            builder.append(' ');
            currentWidth += spaceWidth;
        }

        return builder.append(text).toString();
    }

    public static String dotsToColumn(int currentWidth, int targetWidth, int minDots) {
        StringBuilder builder = new StringBuilder();
        int dotWidth = charWidth('.');
        int filledWidth = currentWidth;

        while (builder.length() < minDots || filledWidth < targetWidth) {
            builder.append('.');
            filledWidth += dotWidth;
        }

        return builder.toString();
    }

    private static int charWidth(char currentChar) {
        return switch (currentChar) {
            case ' ' -> 4;
            case '.', ',', ':', ';', '!', '|', '\'', '`' -> 2;
            case 'i', 'l' -> 3;
            case 'I', '[', ']', 't', '(', ')', '{', '}', '"' -> 4;
            case 'f', 'k', '<', '>', '*', '/', '\\' -> 5;
            case 'm', 'w', 'M', 'W', '@', '%', '&' -> 6;
            default -> 5;
        };
    }
}
