# MiniRedis – Redis-Inspired Key-Value Database in Java

MiniRedis is a lightweight, Redis-inspired in-memory key-value database built using Java. It supports TCP client-server communication, data persistence, key expiration, and concurrent client connections.

This project demonstrates core concepts of database systems, networking, file handling, multithreading, and software testing.

## Features

- In-memory key-value storage using HashMap
- TCP-based client-server architecture
- Multiple client connections
- Commands: SET, GET, DEL, EXISTS, INCR, DECR, KEYS
- Key expiration: EXPIRE, EXPIREAT, TTL, PERSIST
- Append-Only File (AOF) persistence
- Automatic data recovery after server restart
- AOF compaction using snapshot data
- Integer overflow and underflow protection
- Automated unit and integration tests using JUnit 5

## Technology Stack

- Language: Java
- Build Tool: Apache Maven
- Testing: JUnit 5
- Networking: Java Sockets
- Data Storage: HashMap
- Persistence: File I/O and AOF

## Project Structure

```text
MiniRedis/
├── pom.xml
├── appendonly.aof
└── src/
    ├── main/java/
    │   ├── Main.java
    │   ├── client/
    │   ├── database/
    │   ├── persistence/
    │   └── server/
    └── test/java/
        ├── DatabaseTest.java
        ├── AOFTest.java
        └── RedisServerIntegrationTest.java
```

## Getting Started

### Requirements
- Java JDK 25
- Apache Maven 3.10.1 or compatible version

### Build and Test

```bash
mvn clean test package
```

### Run the Server

```bash
java -cp target/MiniRedis.jar Main
```

The server listens on port 6380.

### Run the Client

Open another terminal and run:

```bash
java -cp target/MiniRedis.jar client.RedisClient
```

## Example Commands

```text
SET name Antima
GET name
EXISTS name
INCR counter
DECR counter
EXPIRE name 60
TTL name
PERSIST name
KEYS
DEL name
```

## Testing

The project includes 19 automated tests covering database operations, persistence, and TCP server integration.

Run tests using:

```bash
mvn test
```

## Learning Outcomes

- Java object-oriented programming
- TCP socket programming
- Multithreading and synchronization
- Database storage concepts
- File-based persistence and recovery
- Unit and integration testing
- Maven project management

## Project Status

MiniRedis is an educational Redis-inspired database implementation. It is not a complete replacement for Redis.

**Author:** Antima
