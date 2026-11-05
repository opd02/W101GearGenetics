package me.opd.DataReading;
import me.opd.JewelHandeling.Socket;
import me.opd.PetHandeling.Pet;
import java.util.*;
public class Gear {
    public String id, name;
    public GearSlot slot;
    public int level;
    public Set<School> schools = new HashSet<>(); // empty = unrestricted
    public StatBlock baseStats = new StatBlock();
    public List<Socket> sockets = new ArrayList<>();
    public Set<String> setIds = new TreeSet<>();
    public List<String> itemCards = new ArrayList<>();
    public Pet pet;
    public String category = "gear", source = "manual", gameVersion = "unknown", importedAt = "unknown";
    public boolean complete;
    public Map<String, Object> extensions = new TreeMap<>();
    public boolean eligible(School school, int wizardLevel) {
        return level <= wizardLevel && (schools.isEmpty() || schools.contains(school));
    }
    public void validate() {
        if (id == null || id.isBlank() || name == null || name.isBlank() || slot == null || level < 1)
            throw new IllegalArgumentException("Gear needs ID, name, slot and positive level: " + id);
        if (schools == null || schools.contains(School.UNIVERSAL) || baseStats == null || sockets == null || setIds == null
                || itemCards == null || extensions == null || source == null || category == null)
            throw new IllegalArgumentException("Invalid metadata for " + id);
        baseStats.validate();
        for (Socket socket : sockets) { if (socket == null) throw new IllegalArgumentException("Null socket: " + id); socket.validate(); }
        if (pet != null) {
            if (slot != GearSlot.PET) throw new IllegalArgumentException("Pet talents on non-pet " + id);
            pet.validate();
            if (sockets.stream().filter(s -> s.type == JewelType.PET).count() > 1)
                throw new IllegalArgumentException("A pet may have at most one pet jewel socket: " + id);
            pet.getTotalStats(); // Fail early for missing attributes or unsupported modifier profiles.
        }
    }
}
