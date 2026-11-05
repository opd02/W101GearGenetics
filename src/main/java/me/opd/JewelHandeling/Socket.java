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
            && (!type.name().endsWith("_PIN") || Set.of(GearSlot.HAT,GearSlot.ROBE,GearSlot.BOOTS).contains(gear.slot))
            && (type != JewelType.PET || gear.slot == GearSlot.PET)
            && (jewel.petTalents.isEmpty() || gear.pet != null)
            && (jewel.minimumPetAge == 0 || gear.pet != null && gear.pet.age >= Math.max(4,jewel.minimumPetAge))
            && (type != JewelType.PET || gear.pet == null || gear.pet.age < 0 || gear.pet.age >= 4)
            && (gear.pet == null || gear.pet.traits.stream().noneMatch(t -> jewel.talentIds().contains(t.id)))
            && (gear.pet == null || gear.pet.fullyTrained || jewel.petTalents.stream().allMatch(t -> t.attributeModifiers.isEmpty()))
            && (jewel.petTalents.isEmpty() || me.opd.PetHandeling.PetCalculator.canApply(gear.pet,jewel))
            && (allowedSchools.isEmpty() || allowedSchools.containsAll(jewel.affectedSchools()))
            && (jewel.gearSlots.isEmpty() || jewel.gearSlots.contains(gear.slot));
    }
    public void validate() {
        if (type == null || allowedSchools == null) throw new IllegalArgumentException("Socket needs type and restrictions");
    }
}
