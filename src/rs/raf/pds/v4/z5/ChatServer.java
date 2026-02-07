package rs.raf.pds.v4.z5;

import java.io.IOException;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import rs.raf.pds.v4.z5.messages.ChatMessage;
import rs.raf.pds.v4.z5.messages.ChatPacket;
import rs.raf.pds.v4.z5.messages.InfoMessage;
import rs.raf.pds.v4.z5.messages.KryoUtil;
import rs.raf.pds.v4.z5.messages.ListUsers;
import rs.raf.pds.v4.z5.messages.Login;
import rs.raf.pds.v4.z5.messages.PacketType;
import rs.raf.pds.v4.z5.messages.WhoRequest;

// RMI
import rs.raf.pds.v4.z5.rmi.ChatServiceImpl;

public class ChatServer implements Runnable {

    private volatile Thread thread = null;

    volatile boolean running = false;
    final Server server;
    final int portNumber;

    ConcurrentMap<String, Connection> userConnectionMap = new ConcurrentHashMap<>();
    ConcurrentMap<Connection, String> connectionUserMap = new ConcurrentHashMap<>();

    // ID + storage za reply/edit
    private final AtomicLong idGen = new AtomicLong(1);
    private final ConcurrentMap<Long, ChatPacket> messageById = new ConcurrentHashMap<>();

    public ChatServer(int portNumber) {
        this.server = new Server();
        this.portNumber = portNumber;

        KryoUtil.registerKryoClasses(server.getKryo());
        registerListener();
    }

    private void registerListener() {
        server.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (object instanceof Login) {
                    Login login = (Login) object;
                    newUserLogged(login, connection);
                    connection.sendTCP(new InfoMessage("Hello " + login.getUserName()));
                    return;
                }

                if (object instanceof ChatPacket) {
                    ChatPacket p = (ChatPacket) object;
                    handleChatPacket(p, connection);
                    return;
                }

                // Staro (ostavljeno radi kompatibilnosti)
                if (object instanceof ChatMessage) {
                    ChatMessage chatMessage = (ChatMessage) object;
                    System.out.println(chatMessage.getUser() + ":" + chatMessage.getTxt());
                    broadcastChatMessage(chatMessage, connection);
                    return;
                }

                if (object instanceof WhoRequest) {
                    ListUsers listUsers = new ListUsers(getAllUsers());
                    connection.sendTCP(listUsers);
                    return;
                }
            }

            public void disconnected(Connection connection) {
                String user = connectionUserMap.get(connection);
                connectionUserMap.remove(connection);
                if (user != null) userConnectionMap.remove(user);
                showTextToAll(user + " has disconnected!", connection);
            }
        });
    }

    // =========================
    // ChatPacket logika
    // =========================

    private void handleChatPacket(ChatPacket p, Connection connection) {
        String sender = connectionUserMap.get(connection);
        if (sender == null) return; // nije logovan

        // server uvek postavlja from
        p.from = sender;

        if (p.type == PacketType.EDIT) {
            handleEdit(p, connection);
            return;
        }

        // nova poruka -> server dodeljuje ID + timestamp
        p.id = idGen.getAndIncrement();
        p.timestamp = System.currentTimeMillis();
        p.edited = false;

        // reply preview (ako je reply)
        if (p.replyToId > 0) {
            ChatPacket original = messageById.get(p.replyToId);
            if (original != null) {
                p.replyAuthor = original.from;
                p.replyExcerpt = excerpt(original.text, 30);
            } else {
                p.replyAuthor = "?";
                p.replyExcerpt = "Original message not found";
            }
        }

        // upis u storage
        messageById.put(p.id, clonePacket(p));

        // routing po tipu
        switch (p.type) {
            case PUBLIC:
                broadcastPacket(p, null);
                break;
            case DM:
                sendDm(p, null);
                break;
            case MCAST:
                sendMcast(p, null);
                break;
            default:
                break;
        }
    }

    private void handleEdit(ChatPacket editReq, Connection connection) {
        String sender = connectionUserMap.get(connection);
        if (sender == null) return;

        long targetId = editReq.editTargetId;
        ChatPacket stored = messageById.get(targetId);

        if (stored == null) {
            connection.sendTCP(new InfoMessage("Server: Message ID " + targetId + " not found."));
            return;
        }
        if (!sender.equals(stored.from)) {
            connection.sendTCP(new InfoMessage("Server: You can edit only your own messages."));
            return;
        }

        stored.text = editReq.text;
        stored.edited = true;

        // edit event
        ChatPacket event = clonePacket(stored);
        event.type = PacketType.EDIT;
        event.editTargetId = targetId;

        // prosledi po originalnom tipu poruke
        if (stored.type == PacketType.PUBLIC) {
            broadcastPacket(event, null);
        } else if (stored.type == PacketType.DM) {
            sendDm(event, null);
        } else if (stored.type == PacketType.MCAST) {
            sendMcast(event, null);
        }
    }

    private void broadcastPacket(ChatPacket p, Connection exception) {
        for (Connection conn : userConnectionMap.values()) {
            if (conn.isConnected() && conn != exception) {
                conn.sendTCP(p);
            }
        }
    }

    private void sendDm(ChatPacket p, Connection exception) {
        if (p.to == null || p.to.length < 1) return;
        String toUser = p.to[0];

        Connection toConn = userConnectionMap.get(toUser);
        if (toConn != null && toConn.isConnected() && toConn != exception) {
            toConn.sendTCP(p);
        }

        Connection senderConn = userConnectionMap.get(p.from);
        if (senderConn != null && senderConn.isConnected() && senderConn != exception) {
            senderConn.sendTCP(p);
        }
    }

    private void sendMcast(ChatPacket p, Connection exception) {
        if (p.to == null || p.to.length == 0) return;

        for (String u : p.to) {
            Connection c = userConnectionMap.get(u);
            if (c != null && c.isConnected() && c != exception) {
                c.sendTCP(p);
            }
        }

        Connection senderConn = userConnectionMap.get(p.from);
        if (senderConn != null && senderConn.isConnected() && senderConn != exception) {
            senderConn.sendTCP(p);
        }
    }

    private String excerpt(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private ChatPacket clonePacket(ChatPacket p) {
        ChatPacket c = new ChatPacket();
        c.type = p.type;
        c.from = p.from;
        c.text = p.text;
        c.to = (p.to == null) ? null : Arrays.copyOf(p.to, p.to.length);
        c.id = p.id;
        c.timestamp = p.timestamp;
        c.replyToId = p.replyToId;
        c.replyAuthor = p.replyAuthor;
        c.replyExcerpt = p.replyExcerpt;
        c.editTargetId = p.editTargetId;
        c.edited = p.edited;
        return c;
    }

    // =========================
    // Postojeci kod
    // =========================

    String[] getAllUsers() {
        String[] users = new String[userConnectionMap.size()];
        int i = 0;
        for (String user : userConnectionMap.keySet()) {
            users[i] = user;
            i++;
        }
        return users;
    }

    void newUserLogged(Login loginMessage, Connection conn) {
        userConnectionMap.put(loginMessage.getUserName(), conn);
        connectionUserMap.put(conn, loginMessage.getUserName());
        showTextToAll("User " + loginMessage.getUserName() + " has connected!", conn);
    }

    private void broadcastChatMessage(ChatMessage message, Connection exception) {
        for (Connection conn : userConnectionMap.values()) {
            if (conn.isConnected() && conn != exception)
                conn.sendTCP(message);
        }
    }

    private void showTextToAll(String txt, Connection exception) {
        System.out.println(txt);
        for (Connection conn : userConnectionMap.values()) {
            if (conn.isConnected() && conn != exception)
                conn.sendTCP(new InfoMessage(txt));
        }
    }

    public void start() throws IOException {
        server.start();
        server.bind(portNumber);

        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    public void stop() {
        Thread stopThread = thread;
        thread = null;
        running = false;
        if (stopThread != null)
            stopThread.interrupt();
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar chatServer.jar <port number>");
            System.out.println("Recommended port number is 54555");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);

        try {
            // RMI registry + bind
            try {
                LocateRegistry.createRegistry(1099);
                System.out.println("RMI Registry started on port 1099");
            } catch (Exception e) {
                System.out.println("RMI Registry already running (or cannot start): " + e.getMessage());
            }

            ChatServiceImpl rmiService = new ChatServiceImpl();
            Naming.rebind("rmi://localhost/ChatService", rmiService);
            System.out.println("RMI ChatService bound as ChatService");

            // Socket server
            ChatServer chatServer = new ChatServer(portNumber);
            chatServer.start();

            chatServer.thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
