package rs.raf.pds.v4.z5.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import rs.raf.pds.v4.z5.messages.ChatPacket;
import rs.raf.pds.v4.z5.rooms.ChatRoom;

public class ChatServiceImpl extends UnicastRemoteObject
        implements ChatService {

    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, List<ChatPacket>> roomMessages =
            new ConcurrentHashMap<>();

    public ChatServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public synchronized boolean createRoom(String roomName, String owner)
            throws RemoteException {
        if (rooms.containsKey(roomName)) return false;

        ChatRoom room = new ChatRoom(roomName);
        room.addMember(owner);

        rooms.put(roomName, room);
        roomMessages.put(roomName, new ArrayList<>());

        return true;
    }

    @Override
    public List<String> listRooms() throws RemoteException {
        return new ArrayList<>(rooms.keySet());
    }

    @Override
    public synchronized List<ChatPacket> joinRoom(String roomName, String user)
            throws RemoteException {
        ChatRoom room = rooms.get(roomName);
        if (room == null) return Collections.emptyList();

        room.addMember(user);

        List<ChatPacket> msgs = roomMessages.get(roomName);
        return lastN(msgs, 10);
    }

    @Override
    public List<ChatPacket> loadOlderMessages(
            String roomName,
            long beforeId,
            int limit
    ) throws RemoteException {

        List<ChatPacket> msgs = roomMessages.get(roomName);
        if (msgs == null) return Collections.emptyList();

        List<ChatPacket> result = new ArrayList<>();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatPacket p = msgs.get(i);
            if (p.id < beforeId) {
                result.add(p);
                if (result.size() == limit) break;
            }
        }
        Collections.reverse(result);
        return result;
    }

    // helper
    private List<ChatPacket> lastN(List<ChatPacket> list, int n) {
        if (list.size() <= n) return new ArrayList<>(list);
        return list.subList(list.size() - n, list.size());
    }

    // koristićemo ovo iz ChatServer-a
    public void addRoomMessage(String room, ChatPacket packet) {
        List<ChatPacket> msgs = roomMessages.get(room);
        if (msgs != null) {
            msgs.add(packet);
        }
    }
}
