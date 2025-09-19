
package co.remi.asciiframe;

import java.nio.file.*;
import java.io.IOException;

public class AtomicFiles {
    public static void writeBytes(java.nio.file.Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        var tmp = target.resolveSibling(target.getFileName().toString() + ".tmp." + java.util.UUID.randomUUID());
        Files.write(tmp, content);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void writeString(java.nio.file.Path target, String content) throws IOException {
        writeBytes(target, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
