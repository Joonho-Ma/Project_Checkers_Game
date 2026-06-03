import java.util.HashMap;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class GuiServer extends Application {

	HashMap<String, Scene> sceneMap;
	Server serverConnection;

	ListView<String> listItems;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		listItems = new ListView<String>(); // initiate listItems

		sceneMap = new HashMap<String, Scene>();
		sceneMap.put("server", createServerGui()); // add 'scene' on sceneMap

		serverConnection = new Server(data -> { // create server object
			Platform.runLater(() -> {
				listItems.getItems().add(data.toString());
			});
		});

		primaryStage.setOnCloseRequest((WindowEvent t) -> { // set an action when the window is closed
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(sceneMap.get("server"));
		primaryStage.setTitle("Server");
		primaryStage.show();
	}

	// create server scene GUI
	public Scene createServerGui() {
		Label titleLabel = new Label("[Server Log]");
		titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");

		VBox topBox = new VBox(10, titleLabel);
		topBox.setPadding(new Insets(10));

		BorderPane pane = new BorderPane();
		pane.setPadding(new Insets(10));
		pane.setStyle("-fx-background-color: lightblue; -fx-font-family: 'serif';");

		pane.setTop(topBox);
		pane.setCenter(listItems);

		return new Scene(pane, 350, 500);
	}
}