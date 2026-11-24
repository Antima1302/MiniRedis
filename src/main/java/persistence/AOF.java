

package persistence;

import database.Database;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

public class AOF {

    private static final Path FILE =
            Path.of("appendonly.aof");

    private static final Path TEMP_FILE =
            Path.of("appendonly.aof.tmp");

    // Common lock for all AOF operations
    private static final Object AOF_LOCK = new Object();

    // Append command to AOF
    public static void append(String command)
            throws IOException {

        synchronized (AOF_LOCK) {

            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException(
                        "Command cannot be empty"
                );
            }

            byte[] data = (
                    command + System.lineSeparator()
            ).getBytes(StandardCharsets.UTF_8);

            try (FileChannel channel = FileChannel.open(
                    FILE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {

                ByteBuffer buffer = ByteBuffer.wrap(data);

                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }

                channel.force(true);
            }
        }
    }

    // Read saved commands
    public static List<String> readAll() throws IOException {

        synchronized (AOF_LOCK) {

            if (!Files.exists(FILE)) {
                return List.of();
            }

            return Files.readAllLines(
                    FILE,
                    StandardCharsets.UTF_8
            );
        }
    }

    // Compact AOF safely
    public static void compact(Database db)
            throws IOException {

        synchronized (AOF_LOCK) {

            Map<String, Database.SnapshotEntry> snapshot =
                    db.getSnapshot();

            // Write snapshot into temporary file
            try (FileChannel channel = FileChannel.open(
                    TEMP_FILE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {

                for (Map.Entry<String, Database.SnapshotEntry> entry
                        : snapshot.entrySet()) {

                    String key = entry.getKey();

                    Database.SnapshotEntry data = entry.getValue();

                    writeCommand(
                            channel,
                            "SET " + key + " " + data.getValue()
                    );

                    if (data.getExpirationTime() != -1) {

                        writeCommand(
                                channel,
                                "EXPIREAT " + key + " "
                                        + data.getExpirationTime()
                        );
                    }
                }

                // Force temporary file data to storage
                channel.force(true);
            }

            // Replace original file only after successful writing
            try {

                Files.move(
                        TEMP_FILE,
                        FILE,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (AtomicMoveNotSupportedException e) {

                // Fallback if atomic move is unavailable
                Files.move(
                        TEMP_FILE,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

    // Write command into temporary file
    private static void writeCommand(
            FileChannel channel,
            String command
    ) throws IOException {

        byte[] data = (
                command + System.lineSeparator()
        ).getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.wrap(data);

        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}


