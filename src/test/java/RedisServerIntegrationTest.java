
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RedisServerIntegrationTest {

    private static final int PORT = 6380;

    @TempDir
    Path tempDir;

    private Process serverProcess;
    private Path serverLog;

    private String javaExecutable() {

        String executable = System.getProperty("os.name")
                .toLowerCase().contains("win")
                ? "java.exe"
                : "java";

        return Path.of(
                System.getProperty("java.home"),
                "bin",
                executable
        ).toString();
    }

    private void startServer() throws Exception {

        Path classesDirectory =
                Path.of("target", "classes").toAbsolutePath();

        serverLog = tempDir.resolve("server.log");

        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                classesDirectory.toString(),
                "Main"
        );

        builder.directory(tempDir.toFile());

        builder.redirectErrorStream(true);
        builder.redirectOutput(serverLog.toFile());

        serverProcess = builder.start();

        waitForServer();
    }

    private void waitForServer() throws Exception {

        long deadline = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < deadline) {

            if (!serverProcess.isAlive()) {
                fail(
                        "RedisServer stopped unexpectedly:\n"
                                + readServerLog()
                );
            }

            try (Socket socket = new Socket()) {

                socket.connect(
                        new InetSocketAddress("127.0.0.1", PORT),
                        200
                );

                return;

            } catch (IOException e) {

                Thread.sleep(100);
            }
        }

        fail("RedisServer startup timed out:\n" + readServerLog());
    }

    private String readServerLog() throws IOException {

        if (!Files.exists(serverLog)) {
            return "Server log not found";
        }

        return Files.readString(serverLog);
    }

    private Socket connectClient() throws IOException {

        Socket socket = new Socket();

        socket.connect(
                new InetSocketAddress("127.0.0.1", PORT),
                3000
        );

        socket.setSoTimeout(3000);

        return socket;
    }

    private String sendCommand(
            Socket socket,
            String command
    ) throws IOException {

        PrintWriter output = new PrintWriter(
                socket.getOutputStream(),
                true
        );

        BufferedReader input = new BufferedReader(
                new java.io.InputStreamReader(
                        socket.getInputStream()
                )
        );

        output.println(command);

        return input.readLine();
    }

    private void stopServer() throws InterruptedException {

        if (serverProcess != null && serverProcess.isAlive()) {

            serverProcess.destroy();

            if (!serverProcess.waitFor(2, TimeUnit.SECONDS)) {
                serverProcess.destroyForcibly();
                serverProcess.waitFor();
            }
        }
    }

    @BeforeEach
    void startRedisServer() throws Exception {

        startServer();
    }

    @AfterEach
    void stopRedisServer() throws InterruptedException {

        stopServer();
    }

    @Test
    void testSetAndGetThroughClient() throws Exception {

        try (Socket client = connectClient()) {

            assertEquals(
                    "OK",
                    sendCommand(client, "SET name Antima")
            );

            assertEquals(
                    "Antima",
                    sendCommand(client, "GET name")
            );
        }
    }

    @Test
    void testMultipleClientsShareDatabase() throws Exception {

        try (
                Socket client1 = connectClient();
                Socket client2 = connectClient()
        ) {

            assertEquals(
                    "OK",
                    sendCommand(client1, "SET shared hello")
            );

            assertEquals(
                    "hello",
                    sendCommand(client2, "GET shared")
            );
        }
    }

    @Test
    void testAOFRecoveryAfterRestart() throws Exception {

        try (Socket client = connectClient()) {

            assertEquals(
                    "OK",
                    sendCommand(client, "SET recovery_test after_restart")
            );
        }

        // Stop the first server
        stopServer();

        // Start another server using the same temporary directory
        startServer();

        try (Socket client = connectClient()) {

            assertEquals(
                    "after_restart",
                    sendCommand(client, "GET recovery_test")
            );
        }
    }
}

