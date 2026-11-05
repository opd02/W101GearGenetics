package me.opd.PetHandeling;
import me.opd.DataReading.StatBlock;
import me.opd.JewelHandeling.Jewel;
import java.util.*;
/** Resolves a pet and the selected jewel together, so attribute modifiers affect every talent. */
public final class PetCalculator {
    private PetCalculator() {}
    public static boolean canApply(Pet pet, Jewel jewel) {
        try { calculate(pet,List.of(jewel)); return true; }
        catch (IllegalArgumentException e) { return false; }
    }
    public static StatBlock calculate(Pet pet, List<Jewel> jewels) {
        pet.validate();
        List<PetTrait> talents = new ArrayList<>(pet.traits);
        for (Jewel jewel : jewels) if (jewel != null) talents.addAll(jewel.petTalents);
        Set<String> ids = new HashSet<>();
        Map<PetAttribute,Double> attributes = new EnumMap<>(PetAttribute.class);
        attributes.putAll(pet.attributes);
        for (PetTrait talent : talents) {
            talent.validate();
            if (!ids.add(talent.id)) throw new IllegalArgumentException("Duplicate manifested/jewel pet talent " + talent.id);
            if (!talent.attributeModifiers.isEmpty() && !pet.fullyTrained)
                throw new IllegalArgumentException("Attribute-modifying talents require a fullyTrained pet profile; use observed resolved bonuses otherwise");
            talent.attributeModifiers.forEach((key,value) -> {
                if (!attributes.containsKey(key)) throw new IllegalArgumentException("Missing base pet attribute " + key);
                attributes.merge(key,value,Double::sum);
            });
        }
        StatBlock total = new StatBlock();
        for (PetTrait talent : talents) {
            total.add(talent.statBonus);
            // Sum a talent's effects before applying its explicitly selected rounding policy.
            Map<String,Double> dynamic = new TreeMap<>();
            for (PetStatEffect effect : talent.effects) {
                double factor = effect.attributeWeight(attributes);
                effect.coefficients.values.forEach((key,value) -> dynamic.merge(key,factor*value,Double::sum));
            }
            dynamic.forEach((key,value) -> total.values.merge(key,
                pet.rounding == Pet.Rounding.NEAREST_WHOLE ? Math.floor(value + 0.5) : value,Double::sum));
        }
        total.validate();
        return total;
    }
}
