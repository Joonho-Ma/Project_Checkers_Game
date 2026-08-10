# Project_Checkers_Game

A network-based two-player Checkers game developed in Java using JavaFX, Maven, and socket programming. The project is organized as two separate Maven applications: a server application and a client application.

The server manages client connections, username validation, matchmaking, active game sessions, move validation, game results, chat routing, persistent scoreboard records, and server-side activity logs. The client provides a graphical interface for logging in, entering the lobby, starting games, playing Checkers, sending messages, viewing results, and checking the scoreboard.

## Overview

This project implements Checkers as a multiplayer network application. Players connect to a running server, choose a unique username, enter the lobby, and join the matchmaking queue. When two players are available, the server creates a game session and sends synchronized game updates to both clients.

The application supports multiple simultaneous games. Each pair of players receives an independent game session, and the server keeps the board state, turn state, result state, and player records separate for each session.

## Features

### Client-Server Architecture

* Separate Maven projects for the server and client.
* Socket-based communication between clients and the server.
* Serialized `Message` objects used for communication.
* Server-side management of usernames, game rooms, turns, board states, results, and scoreboard data.
* The server is designed to run before any client connects.

### Graphical Client Application

The client application is built with JavaFX and includes multiple scenes:

* **Login Scene**
  Allows a user to enter a username and connect to the server.

* **Lobby Scene**
  Displays connected users, allows the user to enter or cancel the matchmaking queue, and provides access to the scoreboard.

* **Game Scene**
  Displays the Checkers board, current turn information, player piece assignment, game status messages, and private in-game chat.

* **Scoreboard Scene**
  Displays persistent player records including wins, losses, draws, and win percentage.

* **Result Scene**
  Shows the final game result and allows players to either request a rematch or return to the lobby.

### Server GUI and Logging

The server application includes a JavaFX GUI that displays server activity in real time.

The log records events such as:

* Client connections
* User joins and disconnects
* Username errors
* Queue activity
* Match creation
* Player moves
* Chat messages
* Game results
* Forfeits
* Scoreboard updates
* Rematch activity

### Username Management

* Each user must choose a username before entering the lobby.
* Usernames must be unique among currently connected users.
* If a duplicate username is entered, the client displays an error and prompts the user to choose another name.
* The lobby displays the list of currently connected users.

### Matchmaking

* Users can enter the matchmaking queue from the lobby.
* A waiting user can cancel the queue request before being matched.
* When two users are available, the server creates a new game session.
* Each game session has its own room number, board state, turn state, and player pairing.
* Multiple pairs of clients can play games at the same time.

### In-Game Chat

* Players in the same game can send text messages to one another.
* Messages are displayed in the chat panel of the game scene.
* Empty messages are rejected.

### Persistent Scoreboard

The server maintains a persistent scoreboard using `scoreboard.txt`.

Each recorded player entry contains:

* Wins
* Losses
* Draws
* Win percentage

The scoreboard is updated after completed games and forfeits. Records are saved to `scoreboard.txt`, allowing player results to persist after the server and clients are closed and reopened.

A username appears on the scoreboard only after completing at least one recorded game. Rankings are sorted by:

1. Higher win percentage
2. Higher number of wins if win percentage is tied
3. Reverse alphabetical username order if both win percentage and wins are tied

### Rematch and Lobby Return

After a game ends, each player can choose whether to play again with the same opponent or return to the lobby.

* If both players choose to play again, the same game session restarts with a new board.
* If either player declines, both players return to the lobby.
* If a player leaves an active game, the opponent receives the win and both players are returned to the lobby.

## Checkers Rules Implemented

### Board Setup

* The game uses an 8 × 8 checkerboard.
* Only dark squares are playable.
* Each player begins with 12 pieces.
* Red pieces are displayed as `O`.
* Black pieces are displayed as `X`.
* Kings are displayed as `K` using the player's color.
* In this implementation, the Red player moves first.

### Regular Movement

* Regular pieces move diagonally forward by one playable square.
* A move must end on an empty playable square.
* Red regular pieces move upward on the board.
* Black regular pieces move downward on the board.

### Capturing

* A capture is made by jumping diagonally over an opponent's piece into an empty square.
* Captured pieces are removed from the board.
* If a capture is available, the player must capture.
* If another capture is available after a jump, the same piece must continue jumping.

### Kings

* A piece becomes a king when it reaches the opposite end of the board.
* Kings can move diagonally in both forward and backward directions.
* Kings can capture in both forward and backward directions.

### Game End Conditions

A game ends when:

* One player captures all opposing pieces.
* One player has no legal move remaining.
* Neither player has a legal move, resulting in a draw.
* A player leaves an active game, resulting in a forfeit.

## Technologies Used

* Java
* JavaFX
* Maven
* Java socket programming
* Object serialization
* IntelliJ IDEA

## Project Structure

```text
Project_Checkers_Game/
├── Client/
│   ├── pom.xml
│   └── src/
│       └── main/
│           └── java/
│               ├── Client.java
│               ├── GuiClient.java
│               └── Message.java
│
├── Server/
│   ├── pom.xml
│   ├── scoreboard.txt
│   └── src/
│       └── main/
│           └── java/
│               ├── GuiServer.java
│               ├── Server.java
│               └── Message.java
│
├── .gitattributes
├── .gitignore
└── README.md
```

`Client` and `Server` are separate Maven projects. The repository root is used to organize both applications together.

## Downloading the Project

### Option 1: Clone the Repository

```bash
git clone https://github.com/Joonho-Ma/Project_Checkers_Game.git
```

Then open the cloned folder in IntelliJ IDEA.

### Option 2: Download as ZIP

1. Open the repository on GitHub.
2. Click **Code**.
3. Select **Download ZIP**.
4. Extract the ZIP file.

When downloaded as a ZIP, GitHub may create a folder named:

```text
Project_Checkers_Game-main
```

Inside that folder, confirm that both Maven projects exist:

```text
Project_Checkers_Game-main/
├── Client/
└── Server/
```

## Running the Project in IntelliJ IDEA

### Prerequisites

Before running the project, make sure the following are installed:

* IntelliJ IDEA
* Java Development Kit (JDK)
* Maven support in IntelliJ IDEA

### 1. Open the Project

Open the repository folder in IntelliJ IDEA.

If the project was downloaded as a ZIP, open:

```text
Project_Checkers_Game-main
```

### 2. Configure the Project JDK

If IntelliJ shows an error such as:

```text
Project JDK is not specified
```

configure the JDK:

1. Go to **File > Project Structure**.
2. Select **Project** under **Project Settings**.
3. Choose an installed JDK as the project SDK.
4. Set the language level to the SDK default or a compatible Java version.
5. Click **Apply** and then **OK**.

### 3. Load the Maven Projects

The `Client` and `Server` folders each contain a separate `pom.xml`.

If IntelliJ does not automatically load them as Maven projects:

1. Right-click `Client/pom.xml`.
2. Select **Add as Maven Project** or **Load Maven Project**.
3. Right-click `Server/pom.xml`.
4. Select **Add as Maven Project** or **Load Maven Project**.

### 4. Create the Server Run Configuration

1. Go to **Run > Edit Configurations**.
2. Click **+**.
3. Select **Maven**.
4. Use the following settings:

```text
Name: Run Server
Run: clean compile exec:java
Working directory: <repository folder>\Server
```

The working directory must be the `Server` folder that contains the server-side `pom.xml`.

5. Click **Apply** and then **OK**.

### 5. Create the Client Run Configuration

1. Go to **Run > Edit Configurations**.
2. Click **+**.
3. Select **Maven**.
4. Use the following settings:

```text
Name: Run Client
Run: clean compile exec:java
Working directory: <repository folder>\Client
```

The working directory must be the `Client` folder that contains the client-side `pom.xml`.

5. Click **Apply** and then **OK**.

### 6. Create a Second Client Run Configuration

A complete game requires two client windows.

If IntelliJ allows multiple instances for the same run configuration, enable that option for `Run Client`.

If multiple instances are not available, duplicate the client configuration:

1. Go to **Run > Edit Configurations**.
2. Select `Run Client`.
3. Duplicate the configuration.
4. Rename the duplicate:

```text
Run Client 2
```

5. Keep the same settings:

```text
Run: clean compile exec:java
Working directory: <repository folder>\Client
```

6. Click **Apply** and then **OK**.

## Running a Game

Run the applications in this order:

1. Start `Run Server`.
2. Confirm that the server window opens and shows server activity.
3. Start `Run Client`.
4. Start `Run Client 2`.
5. Enter a different username in each client window.
6. Click **Join** in both clients.
7. Click **Play** in both clients.
8. Once matched, play the game through the graphical board interface.

The server must stay running while clients are connected.

## Testing Checklist

The following actions can be used to verify the application after downloading and configuring the project:

* Start the server successfully.
* Open two client instances.
* Join with two different usernames.
* Confirm that duplicate active usernames are rejected.
* Enter the matchmaking queue from both clients.
* Confirm that both clients are matched into the same game.
* Move pieces and verify that both boards update.
* Try an invalid move and confirm that the client displays an error.
* Send chat messages between players.
* Finish or leave a game and confirm that a result is recorded.
* Open the scoreboard and confirm that the records are displayed.
* Restart the server and confirm that scoreboard records are loaded from `scoreboard.txt`.

## Notes

* `scoreboard.txt` stores persistent win, loss, and draw records by username.
* Usernames are checked for uniqueness among currently connected users.
* The current version does not include password authentication, so a previously used username can be entered again when that user is offline.
* The server should always be started before any client application attempts to connect.
