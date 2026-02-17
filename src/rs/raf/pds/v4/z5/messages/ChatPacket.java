package rs.raf.pds.v4.z5.messages;

import java.io.Serializable;

public class ChatPacket implements Serializable {
    private static final long serialVersionUID = 1L;
    // Tip poruke
    public PacketType type;

    // Identitet
    public String from;

    // Sadrzaj
    public String text;

    // Ciljevi (DM/MCAST)
    public String[] to;     // za DM: user, za MCAST: lista korisnika, za PUBLIC: null

    // ID i vreme
    public long id;         // dodeljuje server za nove poruke
    public long timestamp;  // dodeljuje server

    // Reply
    public long replyToId;          // -1 ako nije reply
    public String replyAuthor;      // popunjava server
    public String replyExcerpt;     // popunjava server (kratak isečak)

    // Edit
    public long editTargetId; // -1 ako nije edit

    // Flag
    public boolean edited;

    
    public ChatPacket() { }

    
    public static ChatPacket publicMsg(String from, String text) {
        ChatPacket p = new ChatPacket();
        p.type = PacketType.PUBLIC;
        p.from = from;
        p.text = text;
        p.replyToId = -1;
        p.editTargetId = -1;
        return p;
    }

    public static ChatPacket dm(String from, String toUser, String text) {
        ChatPacket p = new ChatPacket();
        p.type = PacketType.DM;
        p.from = from;
        p.to = new String[] { toUser };
        p.text = text;
        p.replyToId = -1;
        p.editTargetId = -1;
        return p;
    }

    public static ChatPacket mcast(String from, String[] toUsers, String text) {
        ChatPacket p = new ChatPacket();
        p.type = PacketType.MCAST;
        p.from = from;
        p.to = toUsers;
        p.text = text;
        p.replyToId = -1;
        p.editTargetId = -1;
        return p;
    }

    public static ChatPacket edit(String from, long targetId, String newText) {
        ChatPacket p = new ChatPacket();
        p.type = PacketType.EDIT;
        p.from = from;
        p.editTargetId = targetId;
        p.text = newText;
        p.replyToId = -1;
        return p;
    }
}

