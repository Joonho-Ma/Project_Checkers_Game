import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.function.Consumer;

/*
	class Client
		- connect to the server
		- send Message objects to server
		- receive Message objects from server
*/
public class Client extends Thread {

	Socket socketClient;
	ObjectOutputStream out;
	ObjectInputStream in;

	private Consumer<Serializable> callback;

	Client(Consumer<Serializable> call) {
		callback = call;
	}

	@Override
	public void run() {

		try {
			// connect to local server
			socketClient = new Socket("127.0.0.1", 5555);

			// create output stream first
			out = new ObjectOutputStream(socketClient.getOutputStream());
			out.flush();

			// then create input stream
			in = new ObjectInputStream(socketClient.getInputStream());
			socketClient.setTcpNoDelay(true);
		}
		catch (Exception e) {
			// report connection failure to GUI
			callback.accept(Message.createErrorMessage("Could not connect to server."));
			return;
		}

		// keep listening for incoming Message objects
		while (true) {
			try {
				Message message = (Message) in.readObject();
				callback.accept(message);
			}
			catch (Exception e) {
				// report closed connection to GUI
				callback.accept(Message.createErrorMessage("Connection to server was closed."));
				break;
			}
		}

		closeConnection();
	}

	// General send method used by all client requests
	public synchronized void send(Message message) {
		if (out == null) {
			callback.accept(Message.createErrorMessage("Not connected to server yet."));
			return;
		}

		try {
			out.writeObject(message);
			out.flush();
		}
		catch (IOException e) {
			callback.accept(Message.createErrorMessage("Failed to send message."));
		}
	}

	// Send username request to server
	public void sendUsername(String username) {
		send(Message.createSetUsername(username));
	}

	// Send private chat message to one user
	public void sendPrivate(String targetUser, String text) {
		send(Message.createPrivateMessage(null, targetUser, text));
	}

	// Send request to enter the waiting queue
	public void sendQueueForGame() {
		send(Message.createQueueForGame());
	}

	// Send request to leave the waiting queue
	public void sendCancelQueue() {
		send(Message.createCancelQueue());
	}

	// Send move request during a game
	public void sendMove(int roomNum, int fromRow, int fromCol, int toRow, int toCol) {
		send(Message.createMakeMove(roomNum, fromRow, fromCol, toRow, toCol));
	}

	// Send play-again response after game ends
	public void sendPlayAgainResponse(boolean playAgainAnswer) {
		send(Message.createPlayAgainResponse(playAgainAnswer));
	}

	// Send request to leave current game
	public void sendLeaveGame() {
		send(Message.createLeaveGame());
	}

	// Close socket and streams
	private void closeConnection() {

		try {
			if (out != null) {
				out.close();
			}
		}
		catch (Exception e) {
		}

		try {
			if (in != null) {
				in.close();
			}
		}
		catch (Exception e) {
		}

		try {
			if (socketClient != null) {
				socketClient.close();
			}
		}
		catch (Exception e) {
		}
	}
}