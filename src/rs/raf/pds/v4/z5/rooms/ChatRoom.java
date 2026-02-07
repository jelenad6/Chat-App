package rs.raf.pds.v4.z5.rooms;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class ChatRoom implements Serializable {
    private String name;
    private Set<String> members = new HashSet<>();

    public ChatRoom(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<String> getMembers() {
        return members;
    }

    public void addMember(String user) {
        members.add(user);
    }

    public boolean hasMember(String user) {
        return members.contains(user);
    }
}
