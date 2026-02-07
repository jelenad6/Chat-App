package rs.raf.pds.v4.z5.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import rs.raf.pds.v4.z5.messages.ChatPacket;

public interface ChatService extends Remote {

    boolean createRoom(String roomName, String owner)
            throws RemoteException;

    List<String> listRooms()
            throws RemoteException;

    List<ChatPacket> joinRoom(String roomName, String user)
            throws RemoteException;

    List<ChatPacket> loadOlderMessages(
            String roomName,
            long beforeMessageId,
            int limit
    ) throws RemoteException;
}
