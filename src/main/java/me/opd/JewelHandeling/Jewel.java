package me.opd.JewelHandeling;
import me.opd.DataReading.*;
import java.util.*;
public class Jewel {
    public String id, name;
    public JewelType type;
    public int level;
    public Set<School> schools = new HashSet<>(), bonusSchools = new HashSet<>();
    public Set<GearSlot> gearSlots = new HashSet<>();
    public Set<String> grantedTalentIds = new HashSet<>();
    public StatBlock statBonus = new StatBlock();
    public List<String> itemCards = new ArrayList<>();
    public String source = "manual", category = "jewel", gameVersion = "unknown", importedAt = "unknown";
    public boolean complete;
    public Map<String,Object> extensions = new TreeMap<>();
    public boolean eligible(School school, int wizardLevel) { return level <= wizardLevel && (schools.isEmpty() || schools.contains(school)); }
    public Set<School> affectedSchools() {
        Set<School> result=new HashSet<>(bonusSchools);
        statBonus.values.forEach((key,value) -> {
            if (value!=0) {
                String[] parts=key.split(":");
                if (StatType.valueOf(parts[0]).schoolSpecific) result.add(School.valueOf(parts[1]));
            }
        });
        return result;
    }
    public void validate() {
        if (id == null || id.isBlank() || name == null || name.isBlank() || type == null || level < 1
                || schools == null || bonusSchools == null || gearSlots == null || statBonus == null || itemCards == null
                || source == null || category == null || extensions == null || grantedTalentIds == null)
            throw new IllegalArgumentException("Malformed jewel: " + id);
        statBonus.validate();
        if (!grantedTalentIds.isEmpty() && type != JewelType.PET)
            throw new IllegalArgumentException("Granted pet talent IDs require a PET jewel: " + id);
        if (grantedTalentIds.stream().anyMatch(t -> t == null || t.isBlank()))
            throw new IllegalArgumentException("Blank pet talent ID: " + id);
        if (schools.contains(School.UNIVERSAL)) throw new IllegalArgumentException("Use empty jewel schools for unrestricted eligibility");
    }
}
