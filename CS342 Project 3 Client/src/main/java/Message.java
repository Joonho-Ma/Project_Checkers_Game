import java.io.Serializable;
import java.util.ArrayList;

public class Message implements Serializable {
    private static final long serialVersionUID = 42L;

    public enum MessageType {

        setUsername,        // client -> server : username request
        usernameValid,      // server -> client : username accepted
        usernameInValid,    // server -> client : username rejected

        currentUserList,    // server -> client : current connected users

        privateMessage,     // user -> one specific user

        queueForGame,       // client -> server : enter game queue
        cancelQueue,        // client -> server : leave waiting queue
        matchFound,         // server -> client : opponent matched
        gameStateUpdate,    // server -> client : updated board / turn / status
        makeMove,           // client -> server : move request
        invalidMove,        // server -> client : move rejected
        gameOver,           // server -> client : win / loss / draw result
        playAgainResponse,  // client -> server : play again yes/no
        scoreboardUpdate,   // server -> client : updated scoreboard
        opponentLeft,       // server -> client : opponent disconnected / quit
        leaveGame,          // client -> server : quit current game

        serverMessage,      // general server notification
        errorMessage        // general error
    }

    private MessageType type;

    // Common fields
    private String sender;
    private String requestedUsername;
    private String targetUser;
    private String text;
    private ArrayList<String> currentUserList;

    // Room / game fields
    private int roomNum;
    private String opponentName;
    private String assignedColor;
    private char[][] board;
    private String currentTurn;
    private String winner;
    private String loser;
    private int fromRow;
    private int fromCol;
    private int toRow;
    private int toCol;
    private boolean playAgainAnswer;

    // Scoreboard fields
    private ArrayList<String> scoreboardLines;

    public Message(MessageType type) {
        this.type = type;
        this.currentUserList = new ArrayList<>();
        this.scoreboardLines = new ArrayList<>();
        this.roomNum = -1;
        this.fromRow = -1;
        this.fromCol = -1;
        this.toRow = -1;
        this.toCol = -1;
    }


    // [Existing methods from hw5]

    // Create a message which requests to set username
    public static Message createSetUsername(String requestedUsername) {
        Message msg = new Message(MessageType.setUsername);
        msg.setRequestedUsername(requestedUsername);
        return msg;
    }

    // Create a message that the username is valid
    public static Message createUsernameValid(String username) {
        Message msg = new Message(MessageType.usernameValid);
        msg.setRequestedUsername(username);
        msg.setText("Username accepted: " + username);
        return msg;
    }

    // Create a message that the username is invalid
    public static Message createUsernameInValid(String reason) {
        Message msg = new Message(MessageType.usernameInValid);
        msg.setText(reason);
        return msg;
    }

    // Create a message with currently connected users list
    public static Message createCurrentUserList(ArrayList<String> users) {
        Message msg = new Message(MessageType.currentUserList);
        msg.setCurrentUserList(users);
        return msg;
    }

    // Create a private message
    public static Message createPrivateMessage(String sender, String targetUser, String text) {
        Message msg = new Message(MessageType.privateMessage);
        msg.setSender(sender);
        msg.setTargetUser(targetUser);
        msg.setText(text);
        return msg;
    }

    // Create a server notification message
    public static Message createServerMessage(String text) {
        Message msg = new Message(MessageType.serverMessage);
        msg.setText(text);
        return msg;
    }

    // Create an error message
    public static Message createErrorMessage(String text) {
        Message msg = new Message(MessageType.errorMessage);
        msg.setText(text);
        return msg;
    }

// [Extra methods]

    // Create a message, which asks user to enter waiting queue
    public static Message createQueueForGame() {
        return new Message(MessageType.queueForGame);
    }

    // Create a message, which indicates the user to leave the waiting queue.
    public static Message createCancelQueue() {
        return new Message(MessageType.cancelQueue);
    }

    // Create match-found message
    public static Message createMatchFound(int roomNum, String opponentName, String assignedColor,
                                           char[][] board, String currentTurn, String text) {
        Message msg = new Message(MessageType.matchFound);
        msg.setRoomNum(roomNum);
        msg.setOpponentName(opponentName);
        msg.setAssignedColor(assignedColor);
        msg.setBoard(board);
        msg.setCurrentTurn(currentTurn);
        msg.setText(text);
        return msg;
    }

    // Create a message, which contains updated game board, current turn
    public static Message createGameStateUpdate(int roomNum, char[][] board,
                                                String currentTurn, String text) {
        Message msg = new Message(MessageType.gameStateUpdate);
        msg.setRoomNum(roomNum);
        msg.setBoard(board);
        msg.setCurrentTurn(currentTurn);
        msg.setText(text);
        return msg;
    }

    // Create a message, that has user's move request
    public static Message createMakeMove(int roomNum, int fromRow, int fromCol,
                                         int toRow, int toCol) {
        Message msg = new Message(MessageType.makeMove);
        msg.setRoomNum(roomNum);
        msg.setFromRow(fromRow);
        msg.setFromCol(fromCol);
        msg.setToRow(toRow);
        msg.setToCol(toCol);
        return msg;
    }

    // Create a message, indicating an invalid move
    public static Message createInvalidMove(String text) {
        Message msg = new Message(MessageType.invalidMove);
        msg.setText(text);
        return msg;
    }

    // Create a message, indicating that the game has ended
    public static Message createGameOver(int roomNum, String text,
                                         String winner, String loser, char[][] board) {
        Message msg = new Message(MessageType.gameOver);
        msg.setRoomNum(roomNum);
        msg.setText(text);
        msg.setWinner(winner);
        msg.setLoser(loser);
        msg.setBoard(board);
        return msg;
    }

    // Create a message, regarding player's response whether to play again or not
    public static Message createPlayAgainResponse(boolean playAgainAnswer) {
        Message msg = new Message(MessageType.playAgainResponse);
        msg.setPlayAgainAnswer(playAgainAnswer);
        return msg;
    }

    // Create a message, which has updated scoreboard
    public static Message createScoreboardUpdate(ArrayList<String> scoreboardLines) {
        Message msg = new Message(MessageType.scoreboardUpdate);
        msg.setScoreboardLines(scoreboardLines);
        return msg;
    }

    // Create a message, which indicates that the opponent has left
    public static Message createOpponentLeft(String text) {
        Message msg = new Message(MessageType.opponentLeft);
        msg.setText(text);
        return msg;
    }

    // Create a message to leave the game
    public static Message createLeaveGame() {
        return new Message(MessageType.leaveGame);
    }

    // [Getters / Setters]

    // return message type
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRequestedUsername() {
        return requestedUsername;
    }

    public void setRequestedUsername(String requestedUsername) {
        this.requestedUsername = requestedUsername;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ArrayList<String> getCurrentUserList() {
        return new ArrayList<>(currentUserList);
    }

    public void setCurrentUserList(ArrayList<String> users) {
        if (users == null) {
            this.currentUserList = new ArrayList<>();
        }
        else {
            this.currentUserList = new ArrayList<>(users);
        }
    }

    public int getRoomNum() {
        return roomNum;
    }

    public void setRoomNum(int roomNum) {
        this.roomNum = roomNum;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName;
    }

    public String getAssignedColor() {
        return assignedColor;
    }

    public void setAssignedColor(String assignedColor) {
        this.assignedColor = assignedColor;
    }

    public char[][] getBoard() {
        return copyBoard(board);
    }

    public void setBoard(char[][] board) {
        this.board = copyBoard(board);
    }

    public String getCurrentTurn() {
        return currentTurn;
    }

    public void setCurrentTurn(String currentTurn) {
        this.currentTurn = currentTurn;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public String getLoser() {
        return loser;
    }

    public void setLoser(String loser) {
        this.loser = loser;
    }

    public int getFromRow() {
        return fromRow;
    }

    public void setFromRow(int fromRow) {
        this.fromRow = fromRow;
    }

    public int getFromCol() {
        return fromCol;
    }

    public void setFromCol(int fromCol) {
        this.fromCol = fromCol;
    }

    public int getToRow() {
        return toRow;
    }

    public void setToRow(int toRow) {
        this.toRow = toRow;
    }

    public int getToCol() {
        return toCol;
    }

    public void setToCol(int toCol) {
        this.toCol = toCol;
    }

    public boolean getPlayAgainAnswer() {
        return playAgainAnswer;
    }

    public void setPlayAgainAnswer(boolean playAgainAnswer) {
        this.playAgainAnswer = playAgainAnswer;
    }

    public ArrayList<String> getScoreboardLines() {
        return new ArrayList<>(scoreboardLines);
    }

    public void setScoreboardLines(ArrayList<String> scoreboardLines) {
        if (scoreboardLines == null) {
            this.scoreboardLines = new ArrayList<>();
        }
        else {
            this.scoreboardLines = new ArrayList<>(scoreboardLines);
        }
    }


    // Helper to create a copy of the board array
    private static char[][] copyBoard(char[][] original) {
        if (original == null) {
            return null; // if the board d.n.e., return null
        }

        char[][] copied = new char[original.length][]; // temporary empty 2-dimensional array
        for (int i = 0; i < original.length; i++) {
            if (original[i] == null) {
                copied[i] = null;
            }
            else {
                copied[i] = new char[original[i].length]; // create a new row with same length
                System.arraycopy(original[i], 0, copied[i], 0, original[i].length); // copy row values
            }
        }
        return copied;
    }


    // [Setting Server Log string]
    @Override
    public String toString() {
        switch (type) {
            case setUsername:
                return "[setUsername] " + requestedUsername;

            case usernameValid:
                return "[usernameValid] " + requestedUsername;

            case usernameInValid:
                return "[usernameInValid] " + text;

            case currentUserList:
                return "[currentUserList] " + currentUserList;

            case privateMessage:
                return "[privateMessage] " + sender + " -> " + targetUser + ": " + text;

            case queueForGame:
                return "[queueForGame]";

            case cancelQueue:
                return "[cancelQueue]";

            case matchFound:
                return "[matchFound] room " + roomNum + " opponent=" + opponentName + " color=" + assignedColor;

            case gameStateUpdate:
                return "[gameStateUpdate] room " + roomNum + " turn=" + currentTurn;

            case makeMove:
                return "[makeMove] room " + roomNum + " (" + fromRow + "," + fromCol + ") -> ("
                        + toRow + "," + toCol + ")";

            case invalidMove:
                return "[invalidMove] " + text;

            case gameOver:
                return "[gameOver] room " + roomNum + " " + text;

            case playAgainResponse:
                return "[playAgainResponse] " + playAgainAnswer;

            case scoreboardUpdate:
                return "[scoreboardUpdate] " + scoreboardLines;

            case opponentLeft:
                return "[opponentLeft] " + text;

            case leaveGame:
                return "[leaveGame]";

            case serverMessage:
                return "[serverMessage] " + text;

            case errorMessage:
                return "[errorMessage] " + text;

            default:
                return "[unknown message]";
        }
    }
}