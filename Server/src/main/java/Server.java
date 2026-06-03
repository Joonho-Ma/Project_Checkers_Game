import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Server {
    // counting users
    int count = 1;

    // room number counter
    int nextRoomNum = 1;

    // list of connected clients
    ArrayList<ClientThread> clients = new ArrayList<ClientThread>();

    // username -> client thread
    HashMap<String, ClientThread> userMap = new HashMap<String, ClientThread>();

    // users waiting for a game
    ArrayList<ClientThread> waitingQueue = new ArrayList<ClientThread>();

    // list of active game sessions
//    ArrayList<GameSession> gameSessions = new ArrayList<GameSession>();

    // username -> [wins, losses, draws]
    HashMap<String, int[]> scoreboardMap = new HashMap<String, int[]>();

    String scoreFile = "scoreboard.txt";

    TheServer server;
    private Consumer<Serializable> callback;

    // lock for shared server data
    private final Object lock = new Object();

    Server(Consumer<Serializable> call) {
        callback = call;
        loadBoard(); // load saved scoreboard before clients connect to the server
        server = new TheServer();
        server.start();
    }

    // Add server log
    private void addLog(String text) {
        callback.accept(text);
    }

    // Trim text from the user
    private String trimText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    // Check empty text after trimming
    private boolean isEmpty(String text) {
        return trimText(text).isEmpty();
    }

    // Build sorted list of current connected users
    private ArrayList<String> getSortedCurrentUsers() {
        synchronized (lock) {
            ArrayList<String> users = new ArrayList<String>(userMap.keySet());
            Collections.sort(users);
            return users;
        }
    }

    // Push updated connected-user list to all clients
    private void updateCurrentUserListForAll() {
        Message listMessage = Message.createCurrentUserList(getSortedCurrentUsers());

        // Since multiple threads access to clients list, use synchronization
        synchronized (lock) {
            for (ClientThread client : clients) {
                client.sendMessage(listMessage);
            }
        }
    }

    // Send the message from the server to all users
    // for scoreboard updates, connected user list update
    private void sendToConnectedUsers(Message message) {
        synchronized (lock) {
            for (ClientThread client : clients) {
                if (client.username != null) { // send only to the user that already set his username
                    client.sendMessage(message);
                }
            }
        }
    }

    // Send one message to a single client
    private void sendPrivate(ClientThread client, Message message) {
        if (client != null) {
            client.sendMessage(message);
        }
    }

    // [scoreboard.txt]

    // Load scoreboard.txt during server startup
    private void loadBoard() {
        File file = new File(scoreFile);

        // check file existence (*not necessary, just for guarantee-ing file creation)
        if (!file.exists()) {
            System.err.println("Error: scoreboard.txt D.N.E");
            return;
        }

        int numLoaded = 0; // counter for number of users on the scoreboard

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(","); // ex) row 1: user1,3,0,0

                if (parts.length != 4) { // skip lines with non-appropriate format
                    continue;
                }

                String username = parts[0].trim();
                int wins = Integer.parseInt(parts[1].trim());
                int losses = Integer.parseInt(parts[2].trim());
                int draws = Integer.parseInt(parts[3].trim());

                scoreboardMap.put(username, new int[] { wins, losses, draws });
                numLoaded++;
            }
            addLog("[SCOREBOARD] Successfully loaded " + numLoaded+ " records to scoreboard");
        }
        catch (Exception e) {
            addLog("[SCOREBOARD] Failed to load scoreboard.txt");
        }
    }

    // Save scoreboard file after scoreboard is updated
    private void saveBoard() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scoreFile))) {
            ArrayList<String> usernames = new ArrayList<String>(scoreboardMap.keySet());
            Collections.sort(usernames);

            for (String username : usernames) {
                int[] record = scoreboardMap.get(username);
                writer.write(username + "," + record[0] + "," + record[1] + "," + record[2]);
                writer.newLine();
            }
        }
        catch (Exception e) {
            addLog("[SCOREBOARD] Failed to save scoreboard.txt");
        }
    }

    // Make sure user has a scoreboard entry
    private void ensureScoreEntry(String username) {
        if (username == null) {
            return;
        }

        if (!scoreboardMap.containsKey(username)) {
            scoreboardMap.put(username, new int[] { 0, 0, 0 });
        }
    }

    // Update scoreboard record for one user
    private void updateBoard(String username, int winAdd, int lossAdd, int drawAdd) {
        if (username == null) {
            return;
        }

        synchronized (lock) {
            ensureScoreEntry(username);
            int[] record = scoreboardMap.get(username);
            record[0] += winAdd;
            record[1] += lossAdd;
            record[2] += drawAdd;
        }
    }

    // Calculate win percentage
    private double getWinRate(int[] record) {
        int total = record[0] + record[1] + record[2]; // total = win + lose + draw

        if (total == 0) {
            return 0.0;
        }

        return (double) record[0] / total;
    }

    // Build sorted scoreboard lines for GUI display
    private ArrayList<String> sortBoard() {
        ArrayList<Map.Entry<String, int[]>> entries;

        synchronized (lock) {
            entries = new ArrayList<Map.Entry<String, int[]>>(scoreboardMap.entrySet());
        }

        // only users with at least one total game appear
        // (prevent users from recorded on scoreboard right after the user connects to server for the first time)
        entries.removeIf(entry -> {
            int[] record = entry.getValue();
            return (record[0] + record[1] + record[2]) < 1;
        });

        entries.sort((a, b) -> {
            // calculate the win percentage of each user
            double rateA = getWinRate(a.getValue());
            double rateB = getWinRate(b.getValue());

            // sorting rule: higher percentage comes higher
            int rateCompare = Double.compare(rateB, rateA);
            if (rateCompare != 0) {
                return rateCompare;
            }

            // sorting rule 2: in case of same win percentage, user with more win comes higher
            int winCompare = Integer.compare(b.getValue()[0], a.getValue()[0]);
            if (winCompare != 0) {
                return winCompare;
            }

            // sorting rule 3: in case of same win percentage, same wins, then sort in reverse alphabetical order
            return b.getKey().compareTo(a.getKey());
        });

        ArrayList<String> lines = new ArrayList<String>();

        for (Map.Entry<String, int[]> entry : entries) {
            String username = entry.getKey();
            int[] record = entry.getValue();
            double rate = getWinRate(record) * 100.0;

            lines.add("[" + username + "] | Win: " + record[0]
                    + " Lose: " + record[1]
                    + " Draw: " + record[2]
                    + " | Percentage: " + String.format("%.1f", rate) + "%"); // *percentage rounded to one decimal place
        }
        return lines;
    }

    // Push updated scoreboard to all connected users
    private void pushScoreboardToAllConnectedUsers() {
        Message scoreboardMessage = Message.createScoreboardUpdate(sortBoard());
        sendToConnectedUsers(scoreboardMessage);
        addLog("[SCOREBOARD] Updated and pushed scoreboard to connected users.");
    }

    // [Connection / cleanup]

    // Remove disconnected client from all server structures
    private void removeClient(ClientThread client) {
        String username = client.username;
        GameSession session;

        synchronized (lock) {

            waitingQueue.remove(client);
            clients.remove(client);

            if (username != null) {
                userMap.remove(username);
            }

            session = client.currentSession;
        }

        // if client was in a game, notify opponent and close room
        if (session != null) {
            session.handlePlayerDisconnect(client);
        }

        if (username != null) {
            addLog("[DISCONNECT] " + username + " left the server.");
            sendToConnectedUsers(Message.createServerMessage(username + " left the server."));
            updateCurrentUserListForAll();
        }
        else {
            addLog("[DISCONNECT] client " + client.count + " disconnected before setting username.");
        }
    }

    // [Setting username / private chatting]

    // Handle initial username request
    // Rules: username... cannot be empty, cannot be changed after being accepted, cannot duplicate currently connected username
    private void handleSetUsername(ClientThread client, Message message) {
        String requestedText = trimText(message.getRequestedUsername());

        // username cannot be empty
        if (isEmpty(requestedText)) {
            sendPrivate(client, Message.createUsernameInValid("Username cannot be empty."));
            addLog("[Error:username setting] empty username requested");
            return;
        }

        synchronized (lock) {
            // username cannot be changed after set
            if (client.username != null) {
                sendPrivate(client, Message.createUsernameInValid("Username cannot be changed after it is set."));
                addLog("[Error:username setting] " + client.username + " tried to change username");
                return;
            }

            // duplicate check only with connected users
            if (userMap.containsKey(requestedText)) {
                sendPrivate(client, Message.createUsernameInValid("That username is already taken."));
                addLog("[Error:JOIN] duplicate username: " + requestedText);
                return;
            }

            client.username = requestedText;
            client.inGame = false;
            userMap.put(requestedText, client);
        }

        sendPrivate(client, Message.createUsernameValid(requestedText));
        sendPrivate(client, Message.createScoreboardUpdate(sortBoard()));

        addLog("[JOIN] " + requestedText + " joined the server.");
        sendToConnectedUsers(Message.createServerMessage(requestedText + " joined the server."));
        updateCurrentUserListForAll();
    }

    // Check whether client did choose username
    private boolean checkUsername(ClientThread client) {
        if (client.username == null) {
            return false;
        }
        return true;
    }

    // Handle private message
    private void handlePrivateMessage(ClientThread client, Message message) {
        if (!checkUsername(client)) {
            return;
        }

        String targetUser = trimText(message.getTargetUser());
        String text = trimText(message.getText());

        if (isEmpty(text)) {
            sendPrivate(client, Message.createErrorMessage("Error: Message cannot be empty."));
            return;
        }

        ClientThread targetClient;

        synchronized (lock) {
            targetClient = userMap.get(targetUser);
        }

        if (targetClient == null) {
            sendPrivate(client, Message.createErrorMessage("Error: User not found: " + targetUser));
            return;
        }

        Message outgoingMsg = Message.createPrivateMessage(client.username, targetUser, text);
        sendPrivate(targetClient, outgoingMsg);

        // also show sender what was sent
        if (targetClient != client) {
            sendPrivate(client, outgoingMsg);
        }

        addLog("[PrivateMessage] " + client.username + " -> " + targetUser + " : " + text);
    }

    // [Queue / game]

    // Add client to waiting queue
    private void handleQueueForGame(ClientThread client) {
        if (!checkUsername(client)) {
            return;
        }

        synchronized (lock) {
            if (waitingQueue.contains(client)) {
                sendPrivate(client, Message.createServerMessage("Error: You are already in a queue."));
                return;
            }

            if (client.currentSession != null || client.inGame) {
                sendPrivate(client, Message.createErrorMessage("Error: You are already in a game."));
                return;
            }

            waitingQueue.add(client);
            client.inGame = false;
        }

        sendPrivate(client, Message.createServerMessage("Entered waiting queue."));
        addLog("[QUEUE] " + client.username + " entered the waiting queue.");

        tryMatchPlayers();
    }

    // Remove client from waiting queue
    private void handleCancelQueue(ClientThread client) {
        if (!checkUsername(client)) {
            return;
        }

        synchronized (lock) {
            if (!waitingQueue.contains(client)) {
                sendPrivate(client, Message.createServerMessage("Error: You are not currently in the waiting queue."));
                return;
            }

            waitingQueue.remove(client);
            client.inGame = false;
        }

        sendPrivate(client, Message.createServerMessage("You left the waiting queue."));
        addLog("[QUEUE] " + client.username + " left the waiting queue.");
    }

    // Match waiting users in pairs
    private void tryMatchPlayers() {
        while (true) {
            ClientThread firstClient;
            ClientThread secondClient;
            GameSession session;

            synchronized (lock) {
                // make sure there are at leat 2 users in the waiting queue
                if (waitingQueue.size() < 2) {
                    return;
                }

                firstClient = waitingQueue.remove(0);
                secondClient = waitingQueue.remove(0);

                session = new GameSession(nextRoomNum, firstClient, secondClient); // initiate game session
                nextRoomNum++; // increment room counter

                firstClient.currentSession = session;
                secondClient.currentSession = session;

                firstClient.inGame = true;
                secondClient.inGame = true;
            }

            addLog("[MATCH] room " + session.roomNum + " : " + firstClient.username + " vs " + secondClient.username);
            session.startGame();
        }
    }

    // Pass move request to current session
    private void handleMoveMessage(ClientThread client, Message message) {
        if (!checkUsername(client)) {
            return;
        }

        GameSession session = client.currentSession;

        if (session == null) {
            sendPrivate(client, Message.createErrorMessage("Error: You are not currently in a game."));
            return;
        }

        session.handleMove(client, message);
    }

    // Pass play-again response to current session
    private void handlePlayAgainResponse(ClientThread client, Message message) {
        if (!checkUsername(client)) {
            return;
        }

        GameSession session = client.currentSession;

        if (session == null) {
            sendPrivate(client, Message.createErrorMessage("Error: Rematch-able game session D.N.E"));
            return;
        }

        session.handleReplayResponse(client, message.getPlayAgainAnswer());
    }

    // Handle quit response during active game
    private void handleLeaveGameMessage(ClientThread client) {
        if (!checkUsername(client)) {
            return;
        }

        GameSession session = client.currentSession;

        if (session == null || !client.inGame) {
            sendPrivate(client, Message.createErrorMessage("Error: You are not currently in an active game."));
            return;
        }

        session.handlePlayerQuit(client);
    }

    // Main router for incoming Message objects
    private void handleIncomingMessage(ClientThread client, Message message) {
        if (message == null || message.getType() == null) {
            sendPrivate(client, Message.createErrorMessage("Error: Invalid message received."));
            return;
        }

        switch (message.getType()) {
            case setUsername:
                handleSetUsername(client, message);
                break;

            case privateMessage:
                handlePrivateMessage(client, message);
                break;

            case queueForGame:
                handleQueueForGame(client);
                break;

            case cancelQueue:
                handleCancelQueue(client);
                break;

            case makeMove:
                handleMoveMessage(client, message);
                break;

            case playAgainResponse:
                handlePlayAgainResponse(client, message);
                break;

            case leaveGame:
                handleLeaveGameMessage(client);
                break;

            default:
                sendPrivate(client, Message.createErrorMessage("Error: Unexpected message type"));
                break;
        }
    }

    // [Main server thread]

    public class TheServer extends Thread {
        public void run() {

            try (ServerSocket mySocket = new ServerSocket(5555)) {
                addLog("Server is waiting for a client!");

                while (true) {
                    ClientThread c = new ClientThread(mySocket.accept(), count);

                    synchronized (lock) {
                        clients.add(c);
                    }

                    addLog("[Connect Client] " + count + " connected.");
                    c.start();
                    count++;
                }
            }
            catch (Exception e) {
                addLog("[Error] Error on server socket");
            }
        }
    }

    // [ClientThread]

    class ClientThread extends Thread {

        Socket connection;
        int count;
        ObjectInputStream in;
        ObjectOutputStream out;

        String username;
        boolean inGame;
        GameSession currentSession;

        ClientThread(Socket s, int count) {
            this.connection = s;
            this.count = count;
            this.username = null;
            this.inGame = false;
            this.currentSession = null;
        }

        // Send one Message object to this client
        public void sendMessage(Message message) {
            try {
                if (out != null) {
                    synchronized (out) {
                        out.writeObject(message);
                        out.flush();
                    }
                }
            }
            catch (Exception e) {
            }
        }

        public void run() {

            try {
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();

                in = new ObjectInputStream(connection.getInputStream());
                connection.setTcpNoDelay(true);
            }
            catch (Exception e) {
                addLog("[Error] Error on I/O stream...Break");
                removeClient(this);
                return;
            }

            while (true) {
                try {
                    Message data = (Message) in.readObject();
                    handleIncomingMessage(this, data);
                }
                catch (Exception e) {
                    removeClient(this);
                    break;
                }
            }
        }
    }

    // [GameSession]

    class GameSession {

        int roomNum;
        ClientThread playerRed;
        ClientThread playerBlack;
        CheckersGame currentGame;

        Boolean redReplayResponse;
        Boolean blackReplayResponse;

        GameSession(int roomNum, ClientThread playerRed, ClientThread playerBlack) {
            this.roomNum = roomNum;
            this.playerRed = playerRed;
            this.playerBlack = playerBlack;
            this.currentGame = new CheckersGame();
            this.redReplayResponse = null;
            this.blackReplayResponse = null;
        }

        // Start a new game for this pair
        public synchronized void startGame() {
            currentGame.resetBoard();
            redReplayResponse = null;
            blackReplayResponse = null;

            playerRed.inGame = true;
            playerBlack.inGame = true;

            playerRed.currentSession = this;
            playerBlack.currentSession = this;

            sendPrivate(playerRed, Message.createMatchFound(
                    roomNum,
                    playerBlack.username,
                    "RED",
                    currentGame.copyBoard(),
                    getCurrentTurnUsername(),
                    "Matched with " + playerBlack.username + ". Game started."
            ));

            sendPrivate(playerBlack, Message.createMatchFound(
                    roomNum,
                    playerRed.username,
                    "BLACK",
                    currentGame.copyBoard(),
                    getCurrentTurnUsername(),
                    "Matched with " + playerRed.username + ". Game started."
            ));
        }

        // Convert current turn color into username
        private String getCurrentTurnUsername() {
            if (currentGame.currentTurn.equals("BLACK")) {
                return playerBlack.username;
            }
            return playerRed.username;
        }

        // Get RED/BLACK color string for one player
        private String getColorForPlayer(ClientThread player) {
            if (player == playerRed) {
                return "RED";
            }
            return "BLACK";
        }

        // Get the opponent of one player
        private ClientThread getOpponent(ClientThread player) {
            if (player == playerBlack) {
                return playerRed;
            }
            return playerBlack;
        }

        // Send updated board status to both players
        private void sendGameStateToPlayers(String text) {
            Message stateMessage = Message.createGameStateUpdate(
                    roomNum,
                    currentGame.copyBoard(),
                    getCurrentTurnUsername(),
                    text
            );

            sendPrivate(playerRed, stateMessage);
            sendPrivate(playerBlack, stateMessage);
        }

        // Handle one move request inside a game session
        public synchronized void handleMove(ClientThread player, Message message) {
            if (player != playerRed && player != playerBlack) {
                return;
            }

            if (!getColorForPlayer(player).equals(currentGame.currentTurn)) {
                sendPrivate(player, Message.createInvalidMove("It is not your turn."));
                return;
            }

            CheckersGame.MoveResult result = currentGame.makeMove(
                    getColorForPlayer(player),
                    message.getFromRow(),
                    message.getFromCol(),
                    message.getToRow(),
                    message.getToCol()
            );

            if (!result.valid) {
                sendPrivate(player, Message.createInvalidMove(result.message));
                return;
            }

            addLog("[MOVE] room " + roomNum + " " + player.username
                    + " : (" + message.getFromRow() + "," + message.getFromCol() + ") -> ("
                    + message.getToRow() + "," + message.getToCol() + ")");

            if (result.gameOver) {
                finishGame(result);
                return;
            }

            sendGameStateToPlayers(result.message);
        }

        // Finalize game result and update scoreboard
        private void finishGame(CheckersGame.MoveResult result) {
            String resultText;

            // Case: draw
            if (result.draw) {
                updateBoard(playerRed.username, 0, 0, 1);
                updateBoard(playerBlack.username, 0, 0, 1);
                currentGame.winner = null;
                currentGame.loser = null;
                resultText = "Draw";
                addLog("[GAME OVER] room " + roomNum + " ended with draw.");
            }
            else {
                String winnerUsername;
                String loserUsername;

                // red wins
                if (result.winnerColor.equals("RED")) {
                    winnerUsername = playerRed.username;
                    loserUsername = playerBlack.username;
                }
                // black wins
                else {
                    winnerUsername = playerBlack.username;
                    loserUsername = playerRed.username;
                }

                updateBoard(winnerUsername, 1, 0, 0);
                updateBoard(loserUsername, 0, 1, 0);

                currentGame.winner = winnerUsername;
                currentGame.loser = loserUsername;

                resultText = "Winner: " + winnerUsername + " , Loser: " + loserUsername;
                addLog("[GAME OVER] room " + roomNum + " winner = " + winnerUsername);
            }

            saveBoard();
            pushScoreboardToAllConnectedUsers();

            Message gameOverMessage = Message.createGameOver(
                    roomNum,
                    resultText,
                    currentGame.winner,
                    currentGame.loser,
                    currentGame.copyBoard()
            );

            sendPrivate(playerRed, gameOverMessage);
            sendPrivate(playerBlack, gameOverMessage);

            playerRed.inGame = false;
            playerBlack.inGame = false;
        }

        // Handle rematch response after game over
        public synchronized void handleReplayResponse(ClientThread player, boolean response) {
            if (player == playerRed) {
                redReplayResponse = response;
            }
            else if (player == playerBlack) {
                blackReplayResponse = response;
            }
            else {
                return;
            }

            ClientThread opponent = getOpponent(player);
            sendPrivate(player, Message.createServerMessage("Play again response saved."));
            sendPrivate(opponent, Message.createServerMessage(player.username + " responded to play again."));

            if (redReplayResponse == null || blackReplayResponse == null) {
                return;
            }

            if (redReplayResponse && blackReplayResponse) {
                addLog("[REMATCH] room " + roomNum + " restarted with same players.");
                startGame();
            }
            else {
                if (!redReplayResponse) {
                    sendPrivate(playerBlack, Message.createServerMessage(playerRed.username + " denied rematch."));
                }
                if (!blackReplayResponse) {
                    sendPrivate(playerRed, Message.createServerMessage(playerBlack.username + " denied rematch."));
                }
                returnPlayersToLobby();
            }
        }

        // Handle quit response during game
        public synchronized void handlePlayerQuit(ClientThread quittingPlayer) {
            ClientThread opponent = getOpponent(quittingPlayer);

            if (quittingPlayer == null || opponent == null) {
                return;
            }

            updateBoard(opponent.username, 1, 0, 0);
            updateBoard(quittingPlayer.username, 0, 1, 0);

            saveBoard();
            pushScoreboardToAllConnectedUsers();

            addLog("[CANCELED GAME] room " + roomNum + " " + quittingPlayer.username + " quit the game. "
                    + opponent.username + " wins.");

            quittingPlayer.currentSession = null;
            quittingPlayer.inGame = false;

            opponent.currentSession = null;
            opponent.inGame = false;

            sendPrivate(quittingPlayer, Message.createOpponentLeft("You left the game. You lost !"));
            sendPrivate(opponent, Message.createOpponentLeft("Your opponent left the game. You won !"));
        }

        // Handle unexpected disconnect during game
        public synchronized void handlePlayerDisconnect(ClientThread disconnectedPlayer) {
            ClientThread opponent = getOpponent(disconnectedPlayer);

            if (opponent != null && opponent.username != null) {
                opponent.currentSession = null;
                opponent.inGame = false;

                sendPrivate(opponent, Message.createOpponentLeft("Your opponent was disconnected. Returning to lobby."));
            }

            disconnectedPlayer.currentSession = null;
            disconnectedPlayer.inGame = false;


            addLog("[GAME ROOM CLOSED] room " + roomNum + " closed due to player disconnection.");
        }

        // Return both users from result state to lobby
        private void returnPlayersToLobby() {
            if (playerRed != null && playerRed.username != null) {
                playerRed.currentSession = null;
                playerRed.inGame = false;
                sendPrivate(playerRed, Message.createServerMessage("Returned to lobby."));
            }

            if (playerBlack != null && playerBlack.username != null) {
                playerBlack.currentSession = null;
                playerBlack.inGame = false;
                sendPrivate(playerBlack, Message.createServerMessage("Returned to lobby."));
            }
        }
    }


    // [CheckersGame]


    class CheckersGame {

        String currentTurn;
        String winner;
        String loser;
        char[][] board;

        int forcedRow;
        int forcedCol;

        CheckersGame() {
            resetBoard();
        }

        // Reset board to initial checker setup
        public void resetBoard() {
            currentTurn = "RED";

            winner = null;
            loser = null;

            forcedRow = -1;
            forcedCol = -1;

            board = new char[8][8];

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    board[row][col] = '.';
                }
            }

            // Set black pieces on the top part
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 8; col++) {
                    if ((row + col) % 2 == 1) {
                        board[row][col] = 'b';
                    }
                }
            }

            // Set red pieces on the bottom part
            for (int row = 5; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if ((row + col) % 2 == 1) {
                        board[row][col] = 'r';
                    }
                }
            }
        }

        // Copy board for safe message sending
        public char[][] copyBoard() {
            char[][] copied = new char[8][8];

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    copied[row][col] = board[row][col];
                }
            }

            return copied;
        }

        private boolean isInside(int row, int col) {
            return row >= 0 && row < 8 && col >= 0 && col < 8;
        }

        private boolean isPlayableSquare(int row, int col) {
            return (row + col) % 2 == 1;
        }

        private boolean isRedPiece(char piece) {
            return piece == 'r' || piece == 'R';
        }

        private boolean isBlackPiece(char piece) {
            return piece == 'b' || piece == 'B';
        }

        private boolean isKing(char piece) {
            return piece == 'R' || piece == 'B';
        }

        private boolean belongsToColor(char piece, String color) {
            if (color.equals("BLACK")) {
                return isBlackPiece(piece);
            }
            return isRedPiece(piece);
        }

        private boolean isOpponentPiece(char piece, String color) {
            if (piece == '.') {
                return false;
            }

            if (color.equals("RED")) {
                return isBlackPiece(piece);
            }
            return isRedPiece(piece);
        }

        private void switchTurn() {
            if (currentTurn.equals("RED")) {
                currentTurn = "BLACK";
            }
            else {
                currentTurn = "RED";
            }
        }

        private boolean canMoveSimple(char piece, int rowDiff) {
            // king piece --> can move one square diagonally
            if (isKing(piece)) {
                return Math.abs(rowDiff) == 1;
            }

            // regular red piece --> can move one square upwards
            if (piece == 'r') {
                return rowDiff == -1;
            }

            // regular black piece --> can move one square downwards
            if (piece == 'b') {
                return rowDiff == 1;
            }

            return false;
        }

        private boolean canMoveJump(char piece, int rowDiff) {
            // king piece --> can move two square diagonally
            if (isKing(piece)) {
                return Math.abs(rowDiff) == 2;
            }

            // regular red piece --> can move two square upwards
            if (piece == 'r') {
                return rowDiff == -2;
            }

            // regular black piece --> can move two square downwards
            if (piece == 'b') {
                return rowDiff == 2;
            }

            return false;
        }

        // *row 0 is top, row 7 is bottom
        private char maybeUpgraded(char piece, int row) {
            // regular 'r' piece turn to king when it reaches row 0
            if (piece == 'r' && row == 0) {
                return 'R';
            }
            // regular 'b' piece turn to king when it reaches row 7
            if (piece == 'b' && row == 7) {
                return 'B';
            }
            return piece;
        }

        // Check whether a specific piece can capture
        private boolean canPieceCapture(String color, int row, int col) {
            // cannot capture outside the board
            if (!isInside(row, col)) {
                return false;
            }

            char piece = board[row][col];

            // Distinguish whether the piece is the same team piece or not.
            if (!belongsToColor(piece, color)) {
                return false;
            }

            int[] rowSteps;
            int[] colSteps = { -2, 2 };

            if (isKing(piece)) {
                rowSteps = new int[] { -2, 2 }; // king piece can move in any diagonal direction up to twice
            }
            else if (color.equals("RED")) {
                rowSteps = new int[] { -2 }; // red piece moving upwards up to twice
            }
            else {
                rowSteps = new int[] { 2 }; // black piece moving downwards up to twice
            }

            for (int rowDiff : rowSteps) {
                for (int colDiff : colSteps) {
                    int newRow = row + rowDiff; // landing square: row
                    int newCol = col + colDiff; // landing square: col
                    int midRow = row + rowDiff / 2; // the square being jumped over: row
                    int midCol = col + colDiff / 2; // the square being jumped over: col

                    // landing square should also be inside the board
                    if (!isInside(newRow, newCol)) {
                        continue;
                    }

                    // landing square must be empty
                    if (board[newRow][newCol] != '.') {
                        continue;
                    }

                    // middle square must be an opponent piece
                    if (isOpponentPiece(board[midRow][midCol], color)) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Check whether any piece of one color can capture
        private boolean checkAnyCapture(String color) {

            // go through every square on the board
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (canPieceCapture(color, row, col)) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Check whether one color has any legal move
        // For checking whether or not to terminate the game (no any more piece to move)
        private boolean checkAnyLegalMove(String color) {
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    char piece = board[row][col];

                    // pass empty squares and opponent pieces
                    if (!belongsToColor(piece, color)) {
                        continue;
                    }

                    // if the piece can capture, it means legal move exists
                    if (canPieceCapture(color, row, col)) {
                        return true;
                    }

                    // Now, if no any capture is available, then check simple diagonal move
                    // Rules applied same as canMoveSimple()
                    int[] rowSteps;
                    int[] colSteps = { -1, 1 };

                    if (isKing(piece)) {
                        rowSteps = new int[] { -1, 1 };
                    }
                    else if (color.equals("RED")) {
                        rowSteps = new int[] { -1 };
                    }
                    else {
                        rowSteps = new int[] { 1 };
                    }

                    for (int rowDiff : rowSteps) {
                        for (int colDiff : colSteps) {
                            int newRow = row + rowDiff;
                            int newCol = col + colDiff;

                            if (isInside(newRow, newCol) && board[newRow][newCol] == '.') {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        // Count remaining pieces of one color
        // i.e. Keep checking whether one user lost all its pieces
        private int countPieces(String color) {
            int countPieces = 0;

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (belongsToColor(board[row][col], color)) {
                        countPieces++;
                    }
                }
            }

            return countPieces;
        }

        // Check whether current board state ends the game (win, lost, or draw)
        private MoveResult checkWinLossDraw(String moveText) {
            int redCount = countPieces("RED");
            int blackCount = countPieces("BLACK");

            // both players have no piece --> draw
            if (redCount == 0 && blackCount == 0) {
                return new MoveResult(true, true, true, null, "Draw game.");
            }

            // red piece D.N.E --> black wins
            if (redCount == 0) {
                return new MoveResult(true, true, false, "BLACK", moveText);
            }

            // black piece D.N.E --> red wins
            if (blackCount == 0) {
                return new MoveResult(true, true, false, "RED", moveText);
            }

            // If a player's piece cannot move anymore, then lose
            boolean redHasMove = checkAnyLegalMove("RED");
            boolean blackHasMove = checkAnyLegalMove("BLACK");

            // Both players' piecies cannot move --> Draw
            if (!redHasMove && !blackHasMove) {
                return new MoveResult(true, true, true, null, "Draw game.");
            }

            // Red cannot move --> black wins
            if (!redHasMove) {
                return new MoveResult(true, true, false, "BLACK", moveText);
            }

            // Black cannot move --> red wins
            if (!blackHasMove) {
                return new MoveResult(true, true, false, "RED", moveText);
            }

            // Valid move, but game not ended
            return new MoveResult(true, false, false, null, moveText);
        }

        // Apply one move request and return result
        public MoveResult makeMove(String color, int fromRow, int fromCol, int toRow, int toCol) {
            // Both source square and destination square must be inside the board
            if (!isInside(fromRow, fromCol) || !isInside(toRow, toCol)) {
                return new MoveResult(false, false, false, null, "Move is outside the board.");
            }

            // Plus, should be dark squares
            if (!isPlayableSquare(fromRow, fromCol) || !isPlayableSquare(toRow, toCol)) {
                return new MoveResult(false, false, false, null, "Move must stay on playable squares.");
            }

            char piece = board[fromRow][fromCol];

            // Source square must not be empty
            if (piece == '.') {
                return new MoveResult(false, false, false, null, "Choose a valid piece.");
            }

            // Selected piece must belong to its corresponding player
            if (!belongsToColor(piece, color)) {
                return new MoveResult(false, false, false, null, "That piece is not yours.");
            }

            // Destinaion must be empty
            if (board[toRow][toCol] != '.') {
                return new MoveResult(false, false, false, null, "Destination is not empty.");
            }

            // if chain capture is available, same piece must continue
            if (forcedRow != -1 || forcedCol != -1) {
                if (fromRow != forcedRow || fromCol != forcedCol) {
                    return new MoveResult(false, false, false, null,
                            "You must continue capturing with the same piece.");
                }
            }

            // Negative --> moving upwards
            // Positive --> moving downwards
            int rowDiff = toRow - fromRow;
            int colDiff = toCol - fromCol;

            // Check whether or not the move is diagonal
            if (Math.abs(colDiff) != Math.abs(rowDiff)) {
                return new MoveResult(false, false, false, null, "Checkers pieces must move diagonally.");
            }

            // capture-required rule is kept
            boolean captureRequired = (forcedRow != -1) || checkAnyCapture(color);

            // simple move (one square diagonal move)
            if (Math.abs(rowDiff) == 1) {

                // if capture available --> simple move not allowed
                if (captureRequired) {
                    return new MoveResult(false, false, false, null, "Capture is required.");
                }

                // Check the validity of a piecies direction (prevent red moving downwards, black moving upwards)
                if (!canMoveSimple(piece, rowDiff)) {
                    return new MoveResult(false, false, false, null, "That piece cannot move in that direction.");
                }

                // move the piece, upgrade if the piece reached the end
                board[toRow][toCol] = maybeUpgraded(piece, toRow);
                // clear the source square
                board[fromRow][fromCol] = '.';

                // no forced chain capture
                forcedRow = -1;
                forcedCol = -1;

                switchTurn();
                return checkWinLossDraw("Move completed.");
            }

            // capture move: two square diagonal move
            if (Math.abs(rowDiff) == 2) {

                // First, check if the piece can move in that direction
                if (!canMoveJump(piece, rowDiff)) {
                    return new MoveResult(false, false, false, null, "Cannot capture in that direction.");
                }

                int midRow = fromRow + rowDiff / 2;
                int midCol = fromCol + colDiff / 2;

                // middle square must be an opponent
                if (!isOpponentPiece(board[midRow][midCol], color)) {
                    return new MoveResult(false, false, false, null, "No opponent piece to capture.");
                }


                board[toRow][toCol] = piece; // move the current turn piece
                board[fromRow][fromCol] = '.'; // make the source square empty
                board[midRow][midCol] = '.'; // clear the middle square (captured)

                // After landing, check whether the piece should be upgraded
                char promotedPiece = maybeUpgraded(board[toRow][toCol], toRow);
                boolean promotedNow = promotedPiece != board[toRow][toCol];
                board[toRow][toCol] = promotedPiece; // store that the piece was upgraded

                String opponentColor;

                if (color.equals("RED")) {
                    opponentColor = "BLACK";
                }
                else {
                    opponentColor = "RED";
                }

                // If the opponent no piece left (all captured) --> Win!
                if (countPieces(opponentColor) == 0) {
                    return new MoveResult(true, true, false, color, "Capture completed.");
                }

                // Multi-capture rule
                // if another capture is available, same piece must continue
                if (!promotedNow && canPieceCapture(color, toRow, toCol)) {
                    forcedRow = toRow;
                    forcedCol = toCol;
                    return new MoveResult(true, false, false, null,
                            "Capture completed. Continue jumping with the same piece.");
                }

                // No more capture available, clear capture state
                forcedRow = -1;
                forcedCol = -1;

                switchTurn();
                return checkWinLossDraw("Capture completed."); // check whether the game ended
            }

            // Otherwise, not valid
            return new MoveResult(false, false, false, null, "Invalid move");
        }

        // Result object returned from makeMove
        class MoveResult {
            boolean valid;
            boolean gameOver;
            boolean draw;
            String winnerColor;
            String message;

            MoveResult(boolean valid, boolean gameOver, boolean draw, String winnerColor, String message) {
                this.valid = valid;
                this.gameOver = gameOver;
                this.draw = draw;
                this.winnerColor = winnerColor;
                this.message = message;
            }
        }
    }
}