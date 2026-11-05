package me.opd.JewelHandeling;
import me.opd.DataReading.*;
import me.opd.PetHandeling.PetTrait;
import java.util.*;
public class Jewel {
    public String id, name;
    public JewelType type;
    public int level;
    public Set<School> schools = new HashSet<>(), bonusSchools = new HashSet<>();
    public Set<GearSlot> gearSlots = new HashSet<>();
    public Set<String> grantedTalentIds = new HashSet<>();
    public List<PetTrait> petTalents = new ArrayList<>();
    public int minimumPetAge;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Set<String> talentIds() {
        Set<String> ids = new HashSet<>(grantedTalentIds);
        petTalents.forEach(t -> ids.add(t.id));
        return ids;
    }
    public StatBlock statBonus = new StatBlock();
    public List<String> itemCards = new ArrayList<>();
    public String source = "manual", category = "jewel", gameVersion = "unknown", importedAt = "unknown";
    public boolean complete;
    public Map<String,Object> extensions = new TreeMap<>();
    public boolean eligible(School school, int wizardLevel) { return level <= wizardLevel && (schools.isEmpty() || schools.contains(school)); }
    public Set<School> affectedSchools() {
        Set<School> result=new HashSet<>(bonusSchools);
        List<StatBlock> bonuses = new ArrayList<>(); bonuses.add(statBonus);
        petTalents.forEach(t -> { bonuses.add(t.statBonus); t.effects.forEach(e -> bonuses.add(e.coefficients)); });
        bonuses.forEach(bonus -> bonus.values.forEach((key,value) -> {
            if (value!=0) {
                String[] parts=key.split(":");
                if (StatType.valueOf(parts[0]).schoolSpecific) result.add(School.valueOf(parts[1]));
            }
        }));
        return result;
    }
    public void validate() {
        if (id == null || id.isBlank() || name == null || name.isBlank() || type == null || level < 1
                || schools == null || bonusSchools == null || gearSlots == null || statBonus == null || itemCards == null
                || source == null || category == null || extensions == null || grantedTalentIds == null || petTalents == null)
            throw new IllegalArgumentException("Malformed jewel: " + id);
        statBonus.validate();
        if (minimumPetAge < 0 || minimumPetAge > 7 || (minimumPetAge > 0 || !petTalents.isEmpty()) && type != JewelType.PET)
            throw new IllegalArgumentException("Pet talents/age requirements need a PET jewel: " + id);
        Set<String> unique = new HashSet<>();
        for (PetTrait talent : petTalents) {
            if (talent == null) throw new IllegalArgumentException("Null jewel talent: " + id);
            talent.validate();
            if (!unique.add(talent.id)) throw new IllegalArgumentException("Duplicate jewel talent: " + talent.id);
        }
        if (!grantedTalentIds.isEmpty() && type != JewelType.PET)
            throw new IllegalArgumentException("Granted pet talent IDs require a PET jewel: " + id);
        if (grantedTalentIds.stream().anyMatch(t -> t == null || t.isBlank()))
            throw new IllegalArgumentException("Blank pet talent ID: " + id);
        if (schools.contains(School.UNIVERSAL)) throw new IllegalArgumentException("Use empty jewel schools for unrestricted eligibility");
    }
}
