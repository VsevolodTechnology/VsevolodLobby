package xyz.overdyn.bootstrap.module;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class WorldBytes {

    private WorldBytes() {
    }

    public static byte[] fromWorldFolder(Path worldFolder) throws IOException {
        if (!Files.exists(worldFolder) || !Files.isDirectory(worldFolder)) {
            throw new IllegalArgumentException("World folder not found: " + worldFolder);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            Files.walk(worldFolder).forEach(path -> {
                try {
                    Path relative = worldFolder.relativize(path);
                    String entryName = relative.toString().replace('\\', '/');

                    if (entryName.isEmpty()) {
                        return;
                    }

                    if (Files.isDirectory(path)) {
                        if (!entryName.endsWith("/")) {
                            entryName += "/";
                        }
                        zip.putNextEntry(new ZipEntry(entryName));
                        zip.closeEntry();
                    } else {
                        zip.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zip);
                        zip.closeEntry();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        return output.toByteArray();
    }
}