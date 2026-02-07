package rs.raf.pds.v4.z5;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.Naming;
import java.util.Arrays;
import java.util.List;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import rs.raf.pds.v4.z5.messages.ChatMessage;
import rs.raf.pds.v4.z5.messages.ChatPacket;
import rs.raf.pds.v4.z5.messages.InfoMessage;
import rs.raf.pds.v4.z5.messages.KryoUtil;
import rs.raf.pds.v4.z5.messages.ListUsers;
import rs.raf.pds.v4.z5.messages.Login;
import rs.raf.pds.v4.z5.messages.PacketType;
import rs.raf.pds.v4.z5.messages.WhoRequest;

// RMI
import rs.raf.pds.v4.z5.rmi.ChatService;

public class ChatClient implements Runnable {

    public static int DEFAULT_CLIENT_READ_BUFFER_SIZE = 1000000;
    public static int DEFAULT_CLIENT_WRITE_BUFFER_SIZE = 1000000;

    private volatile Thread thread = null;
    volatile boolean running = false;

    final Client client;
    final String hostName;
    final int portNumber;
    final String userName;

    // RMI
    private ChatService chatService;

    public ChatClient(String hostName, int portNumber, String userName) {
        this.client = new Client(DEFAULT_CLIENT_WRITE_BUFFER_SIZE, DEFAULT_CLIENT_READ_BUFFER_SIZE);

        this.hostName = hostName;
        this.portNumber = portNumber;
        this.userName = userName;

        KryoUtil.registerKryoClasses(client.getKryo());
        registerListener();

        // RMI lookup
        try {
            this.chatService = (ChatService) Naming.lookup("rmi://" + hostName + "/ChatService");
            System.out.println("Connected to RMI ChatService");
        } catch (Exception e) {
            System.out.println("WARNING: Cannot connect to RMI ChatService. Rooms/history won't work.");
            System.out.println("Reason: " + e.getMessage());
            this.chatService = null;
        }
    }

    private void registerListener() {
        client.addListener(new Listener() {
            public void connected(Connection connection) {
                Login loginMessage = new Login(userName);
                client.sendTCP(loginMessage);
            }

            public void received(Connection connection, Object object) {

                if (object instanceof ChatPacket) {
                    ChatPacket p = (ChatPacket) object;
                    showChatPacket(p);
                    return;
                }

                // staro (kompatibilnost)
                if (object instanceof ChatMessage) {
                    ChatMessage chatMessage = (ChatMessage) object;
                    showChatMessage(chatMessage);
                    return;
                }

                if (object instanceof ListUsers) {
                    ListUsers listUsers = (ListUsers) object;
                    showOnlineUsers(listUsers.getUsers());
                    return;
                }

                if (object instanceof InfoMessage) {
                    InfoMessage message = (InfoMessage) object;
                    showMessage("Server: " + message.getTxt());
                    return;
                }
            }

            public void disconnected(Connection connection) {
            }
        });
    }

    private void showChatMessage(ChatMessage chatMessage) {
        System.out.println(chatMessage.getUser() + ":" + chatMessage.getTxt());
    }

    private void showChatPacket(ChatPacket p) {
        if (p.type == PacketType.EDIT) {
            System.out.println("[EDIT] #" + p.editTargetId + " " + p.from + " (edited): " + p.text);
            return;
        }

        String prefix;
        if (p.type == PacketType.DM) prefix = "[DM]";
        else if (p.type == PacketType.MCAST) prefix = "[MCAST]";
        else prefix = "[PUBLIC]";

        String editedPart = p.edited ? " (edited)" : "";

        String replyPart = "";
        if (p.replyToId > 0) {
            replyPart = " >> reply to " + p.replyAuthor + ": \"" + p.replyExcerpt + "\"";
        }

        System.out.println(prefix + " #" + p.id + " " + p.from + editedPart + ": " + p.text + replyPart);
    }

    private void showMessage(String txt) {
        System.out.println(txt);
    }

    private void showOnlineUsers(String[] users) {
        System.out.print("Server:");
        for (int i = 0; i < users.length; i++) {
            String user = users[i];
            System.out.print(user);
            System.out.printf((i == users.length - 1 ? "\n" : ", "));
        }
    }

    public void start() throws IOException {
        client.start();
        connect();

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

    public void connect() throws IOException {
        client.connect(1000, hostName, portNumber);
    }

    public void run() {
        try (BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

            String userInput;
            running = true;

            while (running) {
                userInput = stdIn.readLine();

                if (userInput == null || "BYE".equalsIgnoreCase(userInput)) {
                    running = false;
                }
                else if ("WHO".equalsIgnoreCase(userInput)) {
                    client.sendTCP(new WhoRequest());
                }

                // ===== RMI komande =====
                else if (userInput.equals("/rooms")) {
                    if (chatService == null) {
                        System.out.println("RMI not available.");
                    } else {
                        List<String> rooms = chatService.listRooms();
                        if (rooms.isEmpty()) System.out.println("(no rooms)");
                        else rooms.forEach(r -> System.out.println("- " + r));
                    }
                }
                else if (userInput.startsWith("/create ")) {
                    if (chatService == null) {
                        System.out.println("RMI not available.");
                    } else {
                        String room = userInput.substring(8).trim();
                        if (room.isEmpty()) {
                            System.out.println("Usage: /create <roomName>");
                        } else {
                            boolean ok = chatService.createRoom(room, userName);
                            System.out.println(ok ? "Room created: " + room : "Room already exists: " + room);
                        }
                    }
                }
                else if (userInput.startsWith("/join ")) {
                    if (chatService == null) {
                        System.out.println("RMI not available.");
                    } else {
                        String room = userInput.substring(6).trim();
                        if (room.isEmpty()) {
                            System.out.println("Usage: /join <roomName>");
                        } else {
                            List<ChatPacket> history = chatService.joinRoom(room, userName);
                            System.out.println("Joined room: " + room + " | last " + history.size() + " messages:");
                            for (ChatPacket p : history) showChatPacket(p);
                        }
                    }
                }
                else if (userInput.startsWith("/older ")) {
                    if (chatService == null) {
                        System.out.println("RMI not available.");
                    } else {
                        // /older roomName beforeId
                        String rest = userInput.substring(7).trim();
                        String[] parts = rest.split("\\s+");
                        if (parts.length < 2) {
                            System.out.println("Usage: /older <roomName> <beforeMessageId>");
                        } else {
                            String room = parts[0];
                            long beforeId;
                            try {
                                beforeId = Long.parseLong(parts[1]);
                            } catch (NumberFormatException e) {
                                System.out.println("beforeMessageId must be a number.");
                                continue;
                            }

                            List<ChatPacket> older = chatService.loadOlderMessages(room, beforeId, 10);
                            System.out.println("Older messages (" + older.size() + "):");
                            for (ChatPacket p : older) showChatPacket(p);
                        }
                    }
                }

                // ===== Socket komande =====
                else if (userInput.startsWith("/dm ")) {
                    String rest = userInput.substring(4).trim();
                    int sp = rest.indexOf(' ');
                    if (sp < 0) {
                        System.out.println("Usage: /dm <user> <text>");
                    } else {
                        String toUser = rest.substring(0, sp);
                        String txt = rest.substring(sp + 1);
                        client.sendTCP(ChatPacket.dm(userName, toUser, txt));
                    }
                }
                else if (userInput.startsWith("/mcast ")) {
                    String rest = userInput.substring(7).trim();
                    int sp = rest.indexOf(' ');
                    if (sp < 0) {
                        System.out.println("Usage: /mcast <u1,u2,...> <text>");
                    } else {
                        String usersCsv = rest.substring(0, sp);
                        String txt = rest.substring(sp + 1);

                        String[] users = Arrays.stream(usersCsv.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toArray(String[]::new);

                        client.sendTCP(ChatPacket.mcast(userName, users, txt));
                    }
                }
                else if (userInput.startsWith("/reply ")) {
                    String rest = userInput.substring(7).trim();
                    int sp = rest.indexOf(' ');
                    if (sp < 0) {
                        System.out.println("Usage: /reply <messageId> <text>");
                    } else {
                        long replyId;
                        try {
                            replyId = Long.parseLong(rest.substring(0, sp));
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid messageId. Example: /reply 12 ok");
                            continue;
                        }
                        String txt = rest.substring(sp + 1);

                        ChatPacket p = ChatPacket.publicMsg(userName, txt);
                        p.replyToId = replyId;
                        client.sendTCP(p);
                    }
                }
                else if (userInput.startsWith("/edit ")) {
                    String rest = userInput.substring(6).trim();
                    int sp = rest.indexOf(' ');
                    if (sp < 0) {
                        System.out.println("Usage: /edit <messageId> <newText>");
                    } else {
                        long id;
                        try {
                            id = Long.parseLong(rest.substring(0, sp));
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid messageId. Example: /edit 12 new text");
                            continue;
                        }
                        String newText = rest.substring(sp + 1);
                        client.sendTCP(ChatPacket.edit(userName, id, newText));
                    }
                }
                else {
                    // default: public
                    client.sendTCP(ChatPacket.publicMsg(userName, userInput));
                }

                if (!client.isConnected() && running)
                    connect();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            running = false;
            System.out.println("CLIENT SE DISCONNECTUJE");
            client.close();
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar chatClient.jar <host name> <port number> <username>");
            System.out.println("Recommended port number is 54555");
            System.exit(1);
        }

        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String userName = args[2];

        try {
            ChatClient chatClient = new ChatClient(hostName, portNumber, userName);
            chatClient.start();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error:" + e.getMessage());
            System.exit(-1);
        }
    }
}
