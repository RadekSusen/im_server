package utb.fai;

import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class SocketHandler {
	/** mySocket je socket, o který se bude tento SocketHandler starat */
	Socket mySocket;

	/** client ID je øetìzec ve formátu <IP_adresa>:<port> */
	String clientID;
	private String name;
	private Set<String> chatRooms = new HashSet<>();

	/**
	 * activeHandlers je reference na mnoinu všech právě běžících SocketHandlerů
	 */
	ActiveHandlers activeHandlers;

	/** messages je fronta příchozích zpráv */
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(20);

	/** startSignal je synchronizační závora */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** outputHandler.run() se bude starat o OutputStream mého socketu */
	OutputHandler outputHandler = new OutputHandler();

	/** inputHandler.run() se bude starat o InputStream mého socketu */
	InputHandler inputHandler = new InputHandler();

	/** inputFinished indicates if input has ended */
	volatile boolean inputFinished = false;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
        this.mySocket = mySocket;
        clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
        this.activeHandlers = activeHandlers;
    
        synchronized (activeHandlers) {
            activeHandlers.add(this);
        }
    }

	public String getName() {
		return name;
	}

	public Set<String> getChatRooms() {
		return chatRooms;
	}

	public void handleCommand(String command) {
		try {
			String[] parts = command.split(" ", 3);
			String mainCommand = parts[0].substring(1); // Remove the '#' prefix
			String subCommand, message;

			switch (mainCommand) {
				case "setMyName":
					if (parts.length < 2) {
						break;
					}
					subCommand = parts[1];
					// subCommand = "NoveJmeno1";
					synchronized (activeHandlers) {
						if (activeHandlers.isNameAvailable(subCommand)) {
							// Remove from activeHandlersSet with the old name
							activeHandlers.remove(this);
							this.name = subCommand;
							// Re-add to activeHandlersSet with the new name
							activeHandlers.add(this);
						} else {
							messages.offer("The name " + subCommand + " is already in use.\r\n");
						}
					}
					break;

				case "sendPrivate":
					if (parts.length < 3) {
						break;
					}
					subCommand = parts[1];
					message = parts[2];
					activeHandlers.sendPrivateMessage(this, subCommand, message);
					break;

				case "join":
					if (parts.length < 2) {
						break;
					}
					subCommand = parts[1];
					activeHandlers.addToRoom(subCommand, this);
					break;

				case "leave":
					if (parts.length < 2) {
						break;
					}
					subCommand = parts[1];
					if (activeHandlers.removeFromRoom(subCommand, this)) {
					}
					break;

				case "groups":
					Set<String> rooms = activeHandlers.getRoomsForClient(this);
					if (rooms.isEmpty()) {
						break;
					} else {
						messages.offer(String.join(", ", rooms));
					}
					break;

			}
		} catch (Exception e) {
			messages.offer("Error processing command: " + e.getMessage() + "\r\n");
		}
	}

	class OutputHandler implements Runnable {
		public void run() {
			try (OutputStreamWriter writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8")) {
				startSignal.countDown();
				startSignal.await();
				writer.flush();
				while (!inputFinished) {
					String message = messages.take();
					writer.write(message + "\r\n");
					writer.flush();
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(mySocket.getInputStream(), "UTF-8"))) {
				startSignal.countDown();
				startSignal.await();
				String initialName = reader.readLine();
				handleCommand("#setMyName " + initialName);

				activeHandlers.addToRoom("public", SocketHandler.this);

				String request;
				while ((request = reader.readLine()) != null) {
					if (request.startsWith("#")) {
						handleCommand(request);
					} else {
						for (String room : chatRooms) {
							activeHandlers.broadcastToRoom(SocketHandler.this, room, "[" + name + "] >> " + request);
						}
					}
				}
				inputFinished = true;
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			} finally {
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
		}
	}
}
