

package server;

import database.Database;
import persistence.AOF;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisServer {

    private static final Database db = new Database();

    private static final ExecutorService threadPool =
            Executors.newFixedThreadPool(10);

    // Recover saved commands when server starts
    private static void replayAOF() {

        try {
            System.out.println("Loading saved data...");

            for (String command : AOF.readAll()) {

                String response = executeCommand(command);

                if (response.startsWith("Error:")) {
                    System.out.println(
                            "AOF replay error: " + command
                    );
                }
            }

            System.out.println("Saved data loaded!");

        } catch (IOException e) {
            System.out.println("AOF Error: " + e.getMessage());
        }
    }

    // Start server
    public static void start() {

        replayAOF();

        try (ServerSocket serverSocket = new ServerSocket(6380)) {

            System.out.println("MiniRedis Server Started!");
            System.out.println("Listening on port 6380...");

            while (true) {

                Socket clientSocket = serverSocket.accept();

                System.out.println("Client Connected!");

                threadPool.execute(
                        () -> handleClient(clientSocket)
                );
            }

        } catch (IOException e) {
            System.out.println("Server Error: " + e.getMessage());
        }
    }

    // Handle each client separately
    private static void handleClient(Socket socket) {

        try (
                Socket clientSocket = socket;

                BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                clientSocket.getInputStream()
                        )
                );

                PrintWriter output = new PrintWriter(
                        clientSocket.getOutputStream(), true
                )
        ) {

            String command;

            while ((command = input.readLine()) != null) {

                String response = processCommand(command);

                output.println(response);

                if (command.trim().equalsIgnoreCase("EXIT")) {
                    break;
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Client connection error: " + e.getMessage()
            );
        }

        System.out.println("Client Disconnected!");
    }

    // WAL: validate, save to AOF, then update database
    private static synchronized String processCommand(String input) {

        if (input == null || input.isBlank()) {
            return "Error: Empty command";
        }

        String[] parts = input.trim().split("\\s+", 3);

        String command = parts[0].toUpperCase();

        String aofCommand = null;
        String commandToExecute = input.trim();

        try {

            switch (command) {

                case "SET":

                    if (parts.length < 3) {
                        return "Error: SET key value";
                    }

                    aofCommand = input.trim();
                    break;

                case "INCR":
                case "DECR":

                    if (parts.length != 2) {
                        return "Error: " + command + " key";
                    }

                    String currentValue = db.get(parts[1]);

                    long numericValue = 0L;

                    if (currentValue != null) {
                        numericValue = Long.parseLong(currentValue);
                    }

                    // Prevent INCR overflow
                    if (command.equals("INCR")
                            && numericValue == Long.MAX_VALUE) {

                        return "Error: INCR would overflow";
                    }

                    // Prevent DECR underflow
                    if (command.equals("DECR")
                            && numericValue == Long.MIN_VALUE) {

                        return "Error: DECR would underflow";
                    }

                    aofCommand = input.trim();
                    break;

                case "DEL":

                    if (parts.length != 2) {
                        return "Error: DEL key";
                    }

                    if (db.exists(parts[1])) {
                        aofCommand = input.trim();
                    }

                    break;

                case "PERSIST":

                    if (parts.length != 2) {
                        return "Error: PERSIST key";
                    }

                    if (db.getExpirationTime(parts[1]) != -1) {
                        aofCommand = input.trim();
                    }

                    break;

                case "EXPIRE":

                    if (parts.length != 3) {
                        return "Error: EXPIRE key seconds";
                    }

                    long seconds = Long.parseLong(parts[2]);

                    if (db.exists(parts[1])) {

                        if (seconds <= 0) {

                            aofCommand = "DEL " + parts[1];

                        } else {

                            long milliseconds =
                                    Math.multiplyExact(seconds, 1000L);

                            long expiryTime = Math.addExact(
                                    System.currentTimeMillis(),
                                    milliseconds
                            );

                            aofCommand = "EXPIREAT "
                                    + parts[1] + " " + expiryTime;

                            commandToExecute = aofCommand;
                        }
                    }

                    break;

                case "EXPIREAT":

                    if (parts.length != 3) {
                        return "Error: EXPIREAT key timestamp";
                    }

                    long expiryTime = Long.parseLong(parts[2]);

                    if (db.exists(parts[1])) {

                        if (expiryTime <= System.currentTimeMillis()) {

                            aofCommand = "DEL " + parts[1];

                        } else {

                            aofCommand = input.trim();
                        }
                    }

                    break;

                case "COMPACT":

                    if (parts.length != 1) {
                        return "Error: COMPACT takes no arguments";
                    }

                    try {

                        AOF.compact(db);

                        System.out.println(
                                "AOF Compaction Completed!"
                        );

                        return "OK";

                    } catch (IOException e) {

                        System.out.println(
                                "AOF Compaction Error: " + e.getMessage()
                        );

                        return "Error: AOF compaction failed";
                    }

                case "GET":
                case "EXISTS":
                case "TTL":
                case "KEYS":
                case "EXIT":

                    // Read-only commands do not need AOF logging
                    return executeCommand(input);

                default:

                    return "Error: Unknown command";
            }

        } catch (NumberFormatException e) {

            return "Error: Value must be a valid integer";

        } catch (ArithmeticException e) {

            return "Error: Expiration time is too large";
        }

        // Write to AOF BEFORE changing database
        if (aofCommand != null) {

            try {

                AOF.append(aofCommand);

            } catch (IOException e) {

                System.out.println(
                        "AOF write error: " + e.getMessage()
                );

                return "Error: Could not save command to AOF";
            }
        }

        // Update database only after successful AOF append
        return executeCommand(commandToExecute);
    }

    // Execute database commands
    private static String executeCommand(String input) {

        if (input == null || input.isBlank()) {
            return "Error: Empty command";
        }

        String[] parts = input.trim().split("\\s+", 3);

        String command = parts[0].toUpperCase();

        try {

            switch (command) {

                case "SET":

                    if (parts.length < 3) {
                        return "Error: SET key value";
                    }

                    db.set(parts[1], parts[2]);

                    return "OK";

                case "GET":

                    if (parts.length != 2) {
                        return "Error: GET key";
                    }

                    String value = db.get(parts[1]);

                    return value == null ? "(nil)" : value;

                case "DEL":

                    if (parts.length != 2) {
                        return "Error: DEL key";
                    }

                    return db.delete(parts[1]) ? "1" : "0";

                case "EXISTS":

                    if (parts.length != 2) {
                        return "Error: EXISTS key";
                    }

                    return db.exists(parts[1]) ? "1" : "0";

                case "INCR":

                    if (parts.length != 2) {
                        return "Error: INCR key";
                    }

                    return String.valueOf(
                            db.increment(parts[1])
                    );

                case "DECR":

                    if (parts.length != 2) {
                        return "Error: DECR key";
                    }

                    return String.valueOf(
                            db.decrement(parts[1])
                    );

                case "EXPIRE":

                    if (parts.length != 3) {
                        return "Error: EXPIRE key seconds";
                    }

                    long seconds = Long.parseLong(parts[2]);

                    return db.expire(parts[1], seconds) ? "1" : "0";

                case "EXPIREAT":

                    if (parts.length != 3) {
                        return "Error: EXPIREAT key timestamp";
                    }

                    long expiryTime = Long.parseLong(parts[2]);

                    return db.expireAt(
                            parts[1], expiryTime
                    ) ? "1" : "0";

                case "TTL":

                    if (parts.length != 2) {
                        return "Error: TTL key";
                    }

                    return String.valueOf(db.ttl(parts[1]));

                case "PERSIST":

                    if (parts.length != 2) {
                        return "Error: PERSIST key";
                    }

                    return db.persist(parts[1]) ? "1" : "0";

                case "KEYS":

                    if (parts.length != 1) {
                        return "Error: KEYS takes no arguments";
                    }

                    return db.getAllKeys().toString();

                case "EXIT":

                    return "Goodbye!";

                default:

                    return "Error: Unknown command";
            }

        } catch (NumberFormatException e) {

            return "Error: Value must be a valid integer";

        } catch (ArithmeticException e) {

            return "Error: Integer overflow";
        }
    }
}





