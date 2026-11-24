
import database.Database;
import persistence.AOF;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AOFTest {

    private static final Path AOF_FILE =
            Path.of("appendonly.aof");

    @TempDir
    Path tempDir;

    private Path backupFile;
    private boolean originalFileExisted;

    // Backup original AOF and start with a clean file
    @BeforeEach
    void backupOriginalAOF() throws IOException {

        backupFile = tempDir.resolve("original.aof");

        originalFileExisted = Files.exists(AOF_FILE);

        if (originalFileExisted) {
            Files.copy(AOF_FILE, backupFile);
        }

        // Each test starts with an empty AOF
        Files.deleteIfExists(AOF_FILE);
    }

    // Restore original AOF after every test
    @AfterEach
    void restoreOriginalAOF() throws IOException {

        Files.deleteIfExists(AOF_FILE);

        if (originalFileExisted) {
            Files.copy(backupFile, AOF_FILE);
        }
    }

    // Test 1: Append commands and read them
    @Test
    void testAppendAndRead() throws IOException {

        AOF.append("SET name Antima");
        AOF.append("SET language Java");

        List<String> commands = AOF.readAll();

        assertEquals(2, commands.size());
        assertEquals("SET name Antima", commands.get(0));
        assertEquals("SET language Java", commands.get(1));
    }

    // Test 2: Reject empty commands
    @Test
    void testRejectEmptyCommand() {

        assertThrows(
                IllegalArgumentException.class,
                () -> AOF.append("")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> AOF.append("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> AOF.append(null)
        );
    }

    // Test 3: Compact AOF using database snapshot
    @Test
    void testCompact() throws IOException {

        Database db = new Database();

        db.set("user", "Antima");
        db.set("language", "Java");

        assertTrue(db.expire("user", 120));

        AOF.append("SET old_key old_value");

        AOF.compact(db);

        List<String> commands = AOF.readAll();

        assertTrue(commands.contains("SET user Antima"));
        assertTrue(commands.contains("SET language Java"));

        // Expiration must also be preserved
        assertTrue(
                commands.stream().anyMatch(
                        command -> command.startsWith("EXPIREAT user ")
                )
        );

        // Old command should not remain after compaction
        assertFalse(
                commands.contains("SET old_key old_value")
        );
    }
}




