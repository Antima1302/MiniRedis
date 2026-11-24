
import database.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    private Database db;

    @BeforeEach
    void setUp() {
        db = new Database();
    }

    @Test
    void testSetAndGet() {
        db.set("name", "Antima");

        assertEquals("Antima", db.get("name"));
    }

    @Test
    void testGetMissingKey() {
        assertNull(db.get("missing"));
    }

    @Test
    void testDelete() {
        db.set("name", "Antima");

        assertTrue(db.delete("name"));
        assertFalse(db.exists("name"));
    }

    @Test
    void testDeleteMissingKey() {
        assertFalse(db.delete("missing"));
    }

    @Test
    void testExists() {
        db.set("language", "Java");

        assertTrue(db.exists("language"));
        assertFalse(db.exists("missing"));
    }

    @Test
    void testIncrement() {
        assertEquals(1L, db.increment("counter"));
        assertEquals(2L, db.increment("counter"));
    }

    @Test
    void testDecrement() {
        assertEquals(-1L, db.decrement("counter"));
        assertEquals(-2L, db.decrement("counter"));
    }

    @Test
    void testIncrementOverflow() {
        db.set("counter", String.valueOf(Long.MAX_VALUE));

        assertThrows(
            ArithmeticException.class,
            () -> db.increment("counter")
        );

        assertEquals(
            String.valueOf(Long.MAX_VALUE),
            db.get("counter")
        );
    }

    @Test
    void testDecrementUnderflow() {
        db.set("counter", String.valueOf(Long.MIN_VALUE));

        assertThrows(
            ArithmeticException.class,
            () -> db.decrement("counter")
        );

        assertEquals(
            String.valueOf(Long.MIN_VALUE),
            db.get("counter")
        );
    }

    @Test
    void testExpireAndTTL() {
        db.set("session", "active");

        assertTrue(db.expire("session", 60));
        assertTrue(db.ttl("session") > 0);
    }

    @Test
    void testPersist() {
        db.set("session", "active");
        db.expire("session", 60);

        assertTrue(db.persist("session"));
        assertEquals(-1L, db.ttl("session"));
    }

    @Test
    void testMissingKeyTTL() {
        assertEquals(-2L, db.ttl("missing"));
    }

    @Test
    void testSnapshot() {
        db.set("user", "Antima");

        var snapshot = db.getSnapshot();

        assertTrue(snapshot.containsKey("user"));
        assertEquals(
            "Antima",
            snapshot.get("user").getValue()
        );
    }
}
