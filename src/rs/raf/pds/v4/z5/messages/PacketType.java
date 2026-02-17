package rs.raf.pds.v4.z5.messages;

public enum PacketType {
    PUBLIC,     // broadcast svima 
    DM,         // private poruka 1 korisniku
    MCAST,      // multicast grupi korisnika
    EDIT        // izmena postojece poruke
}

