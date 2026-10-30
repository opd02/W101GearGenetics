package me.opd.JewelHandeling;
import me.opd.DataReading.*;
import java.util.*;
public class Socket {
    public JewelType type;
    public Set<School> allowedSchools = new HashSet<>();
    public Socket() {}
    public Socket(JewelType type) { this.type = type; }
    public boolean accepts(Jewel jewel, Gear gear, School school, int level) {
        return jewel.type == type && jewel.eligible(school, level)
            && (gear.pet == null || gear.pet.traits.stream().noneMatch(t -> jewel.grantedTalentIds.contains(t.id)))
            && (allowedSchools.isEmpty() || allowedSchools.containsAll(jewel.affectedSchools()))
            && (jewel.gearSlots.isEmpty() || jewel.gearSlots.contains(gear.slot));
    }
    public void validate() {
        if (type == null || allowedSchools == null) throw new IllegalArgumentException("Socket needs type and restrictions");
    }
}
