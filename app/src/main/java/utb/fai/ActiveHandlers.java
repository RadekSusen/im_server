package utb.fai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();
    private Map<String, Set<SocketHandler>> chatRooms = new ConcurrentHashMap<>();

    public synchronized boolean addToRoom(String roomName, SocketHandler client) {
        chatRooms.computeIfAbsent(roomName, k -> Collections.synchronizedSet(new HashSet<>()));
        boolean added = chatRooms.get(roomName).add(client);
        if (added) {
            client.getChatRooms().add(roomName);
        }
        return added;
    }

    public synchronized boolean removeFromRoom(String roomName, SocketHandler client) {
        Set<SocketHandler> room = chatRooms.get(roomName);
        if (room != null && room.remove(client)) {
            client.getChatRooms().remove(roomName);
            if (room.isEmpty()) {
                chatRooms.remove(roomName);
            }
            return true;
        }
        return false;
    }

    public void broadcastToRoom(SocketHandler sender, String roomName, String message) {
        Set<SocketHandler> room = chatRooms.get(roomName);
        if (room != null) {
            synchronized (room) {
                for (SocketHandler handler : room) {
                    if (handler != sender) {
                        handler.messages.offer(message);
                    }
                }
            }
        } else {
            sender.messages.offer("Room " + roomName + " does not exist.\r\n");
        }
    }

    public synchronized boolean isNameAvailable(String name) {
        for (SocketHandler handler : activeHandlersSet) {
            if (name.equals(handler.getName())) {
                return false;
            }
        }
        return true;
    }

    public synchronized Set<String> getRoomsForClient(SocketHandler client) {
        return new HashSet<>(client.getChatRooms());
    }

    public synchronized SocketHandler getClientByName(String name) {
        for (SocketHandler handler : activeHandlersSet) {
            if (name.equals(handler.getName())) {
                return handler;
            }
        }
        return null;
    }

    public synchronized boolean add(SocketHandler handler) {
        return activeHandlersSet.add(handler);
    }

    public synchronized boolean remove(SocketHandler handler) {
        return activeHandlersSet.remove(handler);
    }

    public synchronized void sendPrivateMessage(SocketHandler sender, String targetName, String message) {
        SocketHandler recipient = getClientByName(targetName);
        if (recipient != null) {
            boolean success = recipient.messages.offer("[" + sender.getName() + "] >> " + message + "\r\n");
            if (!success) {
                sender.messages.offer("The message queue for " + targetName + " is full. Message not sent.\r\n");
            }
        } else {
            sender.messages.offer("User " + targetName + " does not exist.\r\n");
        }
    }
    

}
