import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import javafx.scene.control.ButtonBar; // added for alert when "Back to Lobby" pressed
import javafx.scene.control.ButtonType; // added for alert when "Back to Lobby" pressed

public class GuiClient extends Application {

	// Login scene
	TextField usernameField;
	Button joinButton;
	Label loginStatusLabel;

	// Lobby scene
	Label lobbyTitleLabel;
	Label lobbyStatusLabel;
	Button playButton;
	Button scoreboardButton;
	ListView<String> connectedUsersList;

	// Scoreboard scene
	ListView<String> scoreboardList;
	Button scoreboardBackButton;

	// Game Session scene
	Label gameTitleLabel;
	Label myPieceYou;      // "You: "
	Label myPieceOorX;     // "O" or "X"
	// HBox myPieceHBox;
	Label turnLabel;
	Label gameStatusLabel;
	GridPane boardPane;
	Button[][] boardButtons;
	ListView<String> gameMessageList;
	TextField gameMessageField;
	Button gameSendButton;
	Button leaveGameButton;

	// Result scene
	Label resultTitleLabel;
	Label resultStatusLabel;
	Button playAgainButton;
	Button quitToLobbyButton;

	HashMap<String, Scene> sceneMap;
	Client clientConnection;

	String myUsername;
	String currentOpponent;
	String currentTurnUsername;
	String myAssignedColor;

	int currentRoomNum = -1;
	int selectedRow = -1;
	int selectedCol = -1;

	boolean joined = false;
	boolean waitingForGame = false;

	char[][] currentBoard;
	ArrayList<String> latestScoreboardLines = new ArrayList<String>();

	Stage mainStage;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		mainStage = primaryStage;

		sceneMap = new HashMap<String, Scene>();

		connectedUsersList = new ListView<String>();
		scoreboardList = new ListView<String>();
		gameMessageList = new ListView<String>();
		boardButtons = new Button[8][8];

		// Login scene
		usernameField = new TextField();
		joinButton = new Button("Join");
		loginStatusLabel = new Label("Enter a username and click Join.");
		loginStatusLabel.setStyle("-fx-background-color: #e0d22f;");


		// Lobby scene
		lobbyTitleLabel = new Label("Lobby");
		lobbyStatusLabel = new Label("Waiting for server response.");
		playButton = new Button("Play");
		scoreboardButton = new Button("Scoreboard");

		// Scoreboard scene
		scoreboardBackButton = new Button("Back");

		// Game Sessionscene
		gameTitleLabel = new Label("Checkers Game");

		myPieceYou = new Label("You: ");
		myPieceOorX = new Label();
		// myPieceHBox = new HBox(4, myPieceYou, myPieceOorX);

		turnLabel = new Label("Current turn: ");
		turnLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");
		gameStatusLabel = new Label("Waiting for game start.");
		gameMessageField = new TextField();
		gameSendButton = new Button("Send");
		leaveGameButton = new Button("Back to Lobby");

		// Result scene
		resultTitleLabel = new Label("Game Result");
		resultStatusLabel = new Label("Game finished.");
		playAgainButton = new Button("Play Again");
		quitToLobbyButton = new Button("Quit to Lobby");

		// Network client
		clientConnection = new Client(data -> {
			Platform.runLater(() -> {
				handleIncomingData(data);
			});
		});

		// Create all scenes one, and store them in sceneMap
		sceneMap.put("login", createLoginScene());
		sceneMap.put("lobby", createLobbyScene());
		sceneMap.put("scoreboard", createScoreboardScene());
		sceneMap.put("game", createGameScene());
		sceneMap.put("result", createResultScene());

		clientConnection.start();

		primaryStage.setOnCloseRequest((WindowEvent t) -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(sceneMap.get("login"));
		primaryStage.setTitle("Client");
		primaryStage.show();
	}

	// Build login scene
	public Scene createLoginScene() {
		usernameField.setPromptText("Enter username");

		joinButton.setOnAction(e -> {
			String username = usernameField.getText().trim();

			if (username.isEmpty()) { // Case when user press Join Button with an empty username
				showErrorStatus("Username cannot be empty.");
				return;
			}

			clientConnection.sendUsername(username);
		});

		Label title = new Label("Checkers Login");
		title.setStyle("-fx-font-weight: bold;");

		VBox root = new VBox(12, title, usernameField, joinButton, loginStatusLabel);
		root.setAlignment(Pos.CENTER);
		root.setPadding(new Insets(25));
		root.setStyle("-fx-background-color: lightblue; -fx-font-family: 'serif';");

		return new Scene(root, 600, 450);
	}

	// Build lobby scene
	public Scene createLobbyScene() {
		lobbyTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");
		lobbyStatusLabel.setStyle("-fx-background-color: #fff4cc; -fx-padding: 6;");

		playButton.setPrefWidth(160);
		scoreboardButton.setPrefWidth(160);

		// Play button also works as queue cancel button
		playButton.setOnAction(e -> {
			if (!waitingForGame) {
				clientConnection.sendQueueForGame();
			}
			else {
				clientConnection.sendCancelQueue();
			}
		});

		scoreboardButton.setOnAction(e -> {
			showScoreboardScene();	// move to scoreboard scene
		});

		Label connectedUsersTitle = new Label("[Connected Users]");
		connectedUsersTitle.setStyle("-fx-font-weight: bold;");

		VBox leftBox = new VBox(12, lobbyTitleLabel, playButton, scoreboardButton, lobbyStatusLabel);
		leftBox.setPadding(new Insets(20));
		leftBox.setAlignment(Pos.TOP_CENTER);
		leftBox.setPrefWidth(220);

		VBox rightBox = new VBox(8, connectedUsersTitle, connectedUsersList);
		rightBox.setPadding(new Insets(20));

		BorderPane root = new BorderPane();
		root.setLeft(leftBox);
		root.setCenter(rightBox);
		root.setStyle("-fx-background-color: lightblue; -fx-font-family: 'serif';");

		return new Scene(root, 700, 500);
	}

	// Build scoreboard scene
	public Scene createScoreboardScene() {
		Label title = new Label("[Scoreboard]");
		title.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");

		scoreboardBackButton.setOnAction(e -> {
			showLobbyScene();
		});

		VBox root = new VBox(12, title, scoreboardList, scoreboardBackButton);
		root.setPadding(new Insets(20));
		root.setAlignment(Pos.CENTER);
		root.setStyle("-fx-background-color: lightblue; -fx-font-family: 'serif';");

		scoreboardList.setPrefHeight(360);
		scoreboardBackButton.setPrefWidth(120);

		return new Scene(root, 700, 500);
	}

	// Create game session scene
	public Scene createGameScene() {
		Label chatTitle = new Label("[Chatting]");
		chatTitle.setStyle("-fx-font-weight: bold;");

		gameStatusLabel.setStyle("-fx-background-color: #fff4cc; -fx-padding: 6;");
		gameMessageField.setPromptText("Type message here");

		gameSendButton.setOnAction(e -> {
			sendGameMessageAction();
		});

		leaveGameButton.setOnAction(e -> {
			showLeaveGamePopup();
		});

		initializeBoardButtons();

		HBox titleRow = new HBox(12, gameTitleLabel, myPieceYou, myPieceOorX, leaveGameButton);
		titleRow.setAlignment(Pos.BASELINE_LEFT);

		VBox topBox = new VBox(8, titleRow, turnLabel, gameStatusLabel);
		topBox.setPadding(new Insets(12));

		HBox chatSendBox = new HBox(8, gameMessageField, gameSendButton);

		VBox rightBox = new VBox(8, chatTitle, gameMessageList, chatSendBox);
		rightBox.setPadding(new Insets(12));
		rightBox.setPrefWidth(260);

		BorderPane root = new BorderPane();
		root.setTop(topBox);
		root.setCenter(boardPane);
		root.setRight(rightBox);
		root.setStyle("-fx-background-color: lightblue; -fx-font-family: 'serif';");

		return new Scene(root, 980, 620);
	}

	// Create result scene
	public Scene createResultScene() {
		resultTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");
		resultStatusLabel.setStyle("-fx-background-color: #fff4cc; -fx-padding: 6;");

		playAgainButton.setPrefWidth(140);
		quitToLobbyButton.setPrefWidth(140);

		playAgainButton.setOnAction(e -> {
			playAgainButton.setDisable(true);
			quitToLobbyButton.setDisable(true);
			showNeutralStatus("Waiting for opponent's response.");
			clientConnection.sendPlayAgainResponse(true);
		});

		quitToLobbyButton.setOnAction(e -> {
			playAgainButton.setDisable(true);
			quitToLobbyButton.setDisable(true);
			showNeutralStatus("Returning to lobby.");
			clientConnection.sendPlayAgainResponse(false);
		});

		HBox buttonBox = new HBox(12, playAgainButton, quitToLobbyButton);
		buttonBox.setAlignment(Pos.CENTER);

		VBox root = new VBox(15, resultTitleLabel, resultStatusLabel, buttonBox);
		root.setPadding(new Insets(25));
		root.setAlignment(Pos.CENTER);
		root.setStyle("-fx-background-color: lightblue; -fx-font-family: 'serif';");

		return new Scene(root, 600, 450);
	}

	// Switch to login scene
	private void showLoginScene() {
		mainStage.setScene(sceneMap.get("login"));
	}

	// Switch to lobby scene
	private void showLobbyScene() {
		mainStage.setScene(sceneMap.get("lobby"));
	}

	// Switch to scoreboard scene
	private void showScoreboardScene() {
		updateScoreboardView(latestScoreboardLines); // before showing the scoreboard, update the scoreboard
		mainStage.setScene(sceneMap.get("scoreboard"));
	}

	// Switch to game scene
	private void showGameScene() {
		mainStage.setScene(sceneMap.get("game"));
	}

	// Switch to result scene
	private void showResultScene() {
		mainStage.setScene(sceneMap.get("result"));
	}

	// Create 8x8 checker board buttons
	private void initializeBoardButtons() {
		boardPane = new GridPane();
		boardPane.setPadding(new Insets(15));
		boardPane.setHgap(0);
		boardPane.setVgap(0);
		boardPane.setAlignment(Pos.CENTER);

		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				Button button = new Button(); // each block is created as a button
				button.setMinSize(60, 60);

				int currentRow = row;
				int currentCol = col;

				// send the position of clicked button to the click handler
				button.setOnAction(e -> {
					handleBoardCellClick(currentRow, currentCol);
				});

				boardButtons[row][col] = button;
				boardPane.add(button, col, row);
			}
		}

		// initiate empty board
		updateBoardUI(null);
	}

	// Handle board click for selection and move request
	private void handleBoardCellClick(int row, int col) {
		if (currentRoomNum < 0 || currentBoard == null) {
			return;
		}

		// ignore light squares
		if ((row + col) % 2 == 0) {
			return;
		}

		char clickedPiece = currentBoard[row][col];

		// first click: select a square
		if (selectedRow == -1 || selectedCol == -1) {
			if (clickedPiece == '.') {
				return;
			}

			selectedRow = row;
			selectedCol = col;
			updateBoardUI(currentBoard);
			return;
		}

		// click same square again: clear selection
		if (selectedRow == row && selectedCol == col) {
			clearSelection();
			updateBoardUI(currentBoard);
			return;
		}

		// click another occupied square: move selection
		if (clickedPiece != '.') {
			selectedRow = row;
			selectedCol = col;
			updateBoardUI(currentBoard);
			return;
		}

		// click empty destination: send move request
		clientConnection.sendMove(currentRoomNum, selectedRow, selectedCol, row, col);
		clearSelection();
		updateBoardUI(currentBoard);
	}

	// Clear selected board position
	private void clearSelection() {
		selectedRow = -1;
		selectedCol = -1;
	}

	// Update checker board UI from board array
	private void updateBoardUI(char[][] board) {
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				Button button = boardButtons[row][col];

				String style;
				String text = "";

				if ((row + col) % 2 == 0) {
					style = "-fx-background-color: #cfc1ab;";
					button.setDisable(true); // pieces cannot move to light-colored squares
				}
				else {
					style = "-fx-background-color: #b58863; -fx-font-size: 24; -fx-font-weight: bold;";
					button.setDisable(false);

					if (board != null) {
						char piece = board[row][col];

						switch (piece) {
							case 'r':
								text = "O";
								style += "-fx-text-fill: red;";
								break;
							case 'b':
								text = "X";
								style += "-fx-text-fill: black;";
								break;
							case 'R':   // red king (Marked as red-colored K)
								text = "K";
								style += "-fx-text-fill: red;";
								break;

							case 'B':   // black king (Marked as black-colored K)
								text = "K";
								style += "-fx-text-fill: black;";
								break;

							default:
								text = "";
								style += "-fx-text-fill: black;";
								break;
						}
					}
				}

				// Highlight the currently selected square
				if (row == selectedRow && col == selectedCol) {
					style += "-fx-border-color: yellow; -fx-border-width: 3;";
				}

				button.setText(text);
				button.setStyle(style);
			}
		}
	}

	// Update scoreboard list
	private void updateScoreboardView(ArrayList<String> lines) {
		if (lines == null || lines.isEmpty()) {
			scoreboardList.setItems(FXCollections.observableArrayList("No games recorded yet."));
			return;
		}

		scoreboardList.setItems(FXCollections.observableArrayList(lines));
	}

	// Show error status message
	private void showErrorStatus(String text) {
		if (loginStatusLabel != null) {
			loginStatusLabel.setText(text);
			loginStatusLabel.setStyle("-fx-background-color: #c42349; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (lobbyStatusLabel != null) {
			lobbyStatusLabel.setText(text);
			lobbyStatusLabel.setStyle("-fx-background-color: #c42349; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (gameStatusLabel != null) {
			gameStatusLabel.setText(text);
			gameStatusLabel.setStyle("-fx-background-color: #c42349; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (resultStatusLabel != null) {
			resultStatusLabel.setText(text);
			resultStatusLabel.setStyle("-fx-background-color: #c42349; -fx-text-fill: black; -fx-padding: 6;");
		}
	}

	// Show success status message
	private void showSuccessStatus(String text) {
		if (loginStatusLabel != null) {
			loginStatusLabel.setText(text);
			loginStatusLabel.setStyle("-fx-background-color: lightgreen; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (lobbyStatusLabel != null) {
			lobbyStatusLabel.setText(text);
			lobbyStatusLabel.setStyle("-fx-background-color: lightgreen; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (gameStatusLabel != null) {
			gameStatusLabel.setText(text);
			gameStatusLabel.setStyle("-fx-background-color: lightgreen; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (resultStatusLabel != null) {
			resultStatusLabel.setText(text);
			resultStatusLabel.setStyle("-fx-background-color: lightgreen; -fx-text-fill: black; -fx-padding: 6;");
		}
	}

	// Show neutral status message (default status)
	private void showNeutralStatus(String text) {
		if (loginStatusLabel != null) {
			loginStatusLabel.setText(text);
			loginStatusLabel.setStyle("-fx-background-color: #e0d22f; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (lobbyStatusLabel != null) {
			lobbyStatusLabel.setText(text);
			lobbyStatusLabel.setStyle("-fx-background-color: #e0d22f; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (gameStatusLabel != null) {
			gameStatusLabel.setText(text);
			gameStatusLabel.setStyle("-fx-background-color: #e0d22f; -fx-text-fill: black; -fx-padding: 6;");
		}

		if (resultStatusLabel != null) {
			resultStatusLabel.setText(text);
			resultStatusLabel.setStyle("-fx-background-color: #e0d22f; -fx-text-fill: black; -fx-padding: 6;");
		}
	}

	// Send chat message to current opponent
	private void sendGameMessageAction() {

		// prevent sending private message when there's no opponent
		if (currentOpponent == null || currentOpponent.isEmpty()) {
			showErrorStatus("No opponent available.");
			return;
		}

		String text = gameMessageField.getText().trim();

		// prevent sending empty message
		if (text.isEmpty()) {
			showErrorStatus("Message cannot be empty.");
			return;
		}

		clientConnection.sendPrivate(currentOpponent, text);
		gameMessageField.clear();
	}

	// Append one line to the chatting section
	private void appendGameMessage(String text) {
		gameMessageList.getItems().add(text);
	}

	// Update turn label
	private void updateTurnLabel() {
		if (currentTurnUsername == null || currentTurnUsername.isEmpty()) {
			turnLabel.setText("Current turn: ");
			return;
		}

		if (currentTurnUsername.equals(myUsername)) {
			turnLabel.setText("Current turn: Your turn");
		}
		else {
			turnLabel.setText("Current turn: " + currentTurnUsername);
		}
	}

	// Show quit confirmation popup during active game
	private void showLeaveGamePopup() {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Leave Game");
		alert.setHeaderText("Are you sure to leave the game?");
		alert.setContentText("You would automatically lose the game.");

		ButtonType resumeButton = new ButtonType("Resume", ButtonBar.ButtonData.CANCEL_CLOSE);
		ButtonType quitButton = new ButtonType("Quit", ButtonBar.ButtonData.OK_DONE);

		alert.getButtonTypes().setAll(resumeButton, quitButton);

		Optional<ButtonType> result = alert.showAndWait();

		if (result.isPresent() && result.get() == quitButton) {
			clientConnection.sendLeaveGame();
		}
	}

	// Handle all incoming messages from server
	private void handleIncomingData(Serializable data) {
		Message message = (Message) data;

		switch (message.getType()) {

			// valid username --> proceed to lobby
			case usernameValid:
				joined = true;
				myUsername = message.getRequestedUsername();
				lobbyTitleLabel.setText("Lobby - " + myUsername);
				waitingForGame = false;
				playButton.setDisable(false);
				playButton.setText("Play");
				showSuccessStatus("Username accepted: " + myUsername);
				showLobbyScene();
				break;

			// invalid username --> display error message
			case usernameInValid:
				joined = false;
				showErrorStatus(message.getText());
				break;
			// update connected users list in the lobby
			case currentUserList:
				connectedUsersList.getItems().setAll(message.getCurrentUserList());
				break;

			// update scoreboard in the lobby
			case scoreboardUpdate:
				latestScoreboardLines = message.getScoreboardLines();
				updateScoreboardView(latestScoreboardLines);
				break;

			// match found --> initialize game session scene
			case matchFound:
				waitingForGame = false;
				playButton.setDisable(false);
				playButton.setText("Play");

				currentRoomNum = message.getRoomNum();
				currentOpponent = message.getOpponentName();
				myAssignedColor = message.getAssignedColor();
				currentBoard = message.getBoard();
				currentTurnUsername = message.getCurrentTurn();

				clearSelection();
				gameMessageList.getItems().clear();

				gameTitleLabel.setText("Checkers Game - Opponent: " + currentOpponent);

				if (myAssignedColor.equals("RED")) {
					myPieceOorX.setText("O");
					myPieceOorX.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 18;");
				}
				else {
					myPieceOorX.setText("X");
					myPieceOorX.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 18;");
				}

				updateBoardUI(currentBoard);
				updateTurnLabel();
				showNeutralStatus(message.getText());
				showGameScene();
				break;

			// update the game board after a single move
			case gameStateUpdate:
				currentRoomNum = message.getRoomNum();
				currentBoard = message.getBoard();
				currentTurnUsername = message.getCurrentTurn();

				clearSelection();
				updateBoardUI(currentBoard);
				updateTurnLabel();
				showNeutralStatus(message.getText());
				break;

			// show a reason for invalid (rejected) move
			case invalidMove:
				showErrorStatus(message.getText());
				break;

			// as the game is over, show the result, move to results scene
			case gameOver:
				currentBoard = message.getBoard();
				updateBoardUI(currentBoard);
				clearSelection();

				playAgainButton.setDisable(false);
				quitToLobbyButton.setDisable(false);

				resultStatusLabel.setText(message.getText());
				resultStatusLabel.setStyle("-fx-background-color: #e0d22f; -fx-text-fill: black; -fx-padding: 6;");
				showResultScene();
				break;

			// reset the game status, as the opponent left the game, or was disconnected
			case opponentLeft:
				currentRoomNum = -1;
				currentOpponent = null;
				currentTurnUsername = null;
				myAssignedColor = null;
				waitingForGame = false;
				playButton.setDisable(false);
				playButton.setText("Play");
				clearSelection();
				showNeutralStatus(message.getText());
				showLobbyScene();
				break;

			// private message sent during game session
			case privateMessage:
				appendGameMessage(message.getSender() + ": " + message.getText());
				break;

			// (In project3, broadcast is not used)

			case serverMessage:
				String serverText = message.getText();

				if ("Entered waiting queue.".equals(serverText)) {
					waitingForGame = true;
					playButton.setText("Cancel Queue");
				}
				else if ("Left waiting queue.".equals(serverText)) {
					waitingForGame = false;
					playButton.setText("Play");
				}
				else if ("Returned to lobby.".equals(serverText)) {
					currentRoomNum = -1;
					currentOpponent = null;
					currentTurnUsername = null;
					myAssignedColor = null;
					waitingForGame = false;
					playButton.setDisable(false);
					playButton.setText("Play");
					clearSelection();
					showNeutralStatus(serverText);
					showLobbyScene();
					break;
				}

				showNeutralStatus(serverText);
				break;

			case errorMessage:
				showErrorStatus(message.getText());
				break;

			default:
				break;
		}
	}
}