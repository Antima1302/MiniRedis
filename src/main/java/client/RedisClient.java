package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class RedisClient {

    public static void main(String[] args) {

        try (
            Socket socket = new Socket("localhost", 6380);

            BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            PrintWriter output = new PrintWriter(
                    socket.getOutputStream(), true);

            Scanner scanner = new Scanner(System.in)
        ) {

            System.out.println("Connected to MiniRedis!");
            System.out.println("Type EXIT to disconnect.");

            while (true) {

                System.out.print("MiniRedis> ");

                String command = scanner.nextLine();

                output.println(command);

                String response = input.readLine();

                System.out.println(response);

                if (command.equalsIgnoreCase("EXIT")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("Connection Error: " + e.getMessage());
        }
    }
}