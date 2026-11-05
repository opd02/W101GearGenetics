package me.opd.PetHandeling;
import me.opd.DataReading.StatBlock;
import java.util.*;
public class Pet {
    public String name;
    public boolean hypothetical;
    /** Native stages: 0 egg, 1 baby, 2 teen, 3 adult, 4 ancient, 5 epic, 6 mega, 7 ultra; -1 unknown. */
    public int age = -1;
    /** Unmodified trained attributes; fullyTrained profiles add talent/jewel cap modifiers. */
    public Map<PetAttribute,Double> attributes = new EnumMap<>(PetAttribute.class);
    public Map<PetAttribute,Double> maximumAttributes = new EnumMap<>(PetAttribute.class);
    public boolean fullyTrained;
    public enum Rounding { RAW, NEAREST_WHOLE }
    public Rounding rounding = Rounding.RAW;
    public List<PetTrait> traits = new ArrayList<>();
    public void validate() {
        if (traits == null || traits.size() > 5) throw new IllegalArgumentException("At most five manifested pet talents; use a PET socket for the jewel");
        if (age < -1 || age > 7 || attributes == null || maximumAttributes == null || rounding == null)
            throw new IllegalArgumentException("Invalid pet age, attributes or rounding policy");
        if (age >= 0 && traits.size() > Math.max(0,Math.min(5,age-1)))
            throw new IllegalArgumentException("Too many manifested talents for pet age " + age);
        if (fullyTrained && (!attributes.keySet().equals(EnumSet.allOf(PetAttribute.class))
                || !maximumAttributes.keySet().equals(EnumSet.allOf(PetAttribute.class))))
            throw new IllegalArgumentException("fullyTrained requires all five unmodified trained and maximum attributes");
        for (var entry : maximumAttributes.entrySet()) {
            if (entry.getValue() == null || !Double.isFinite(entry.getValue()) || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid maximum pet attribute " + entry.getKey());
            if (fullyTrained && !Objects.equals(attributes.get(entry.getKey()),entry.getValue()))
                throw new IllegalArgumentException("fullyTrained requires trained attributes equal to their unmodified maximums");
        }
        for (var entry : attributes.entrySet()) {
            double value = entry.getValue() == null ? Double.NaN : entry.getValue();
            if (!Double.isFinite(value) || value < 0 || maximumAttributes.containsKey(entry.getKey())
                    && value > maximumAttributes.get(entry.getKey())) throw new IllegalArgumentException("Invalid base pet attribute " + entry.getKey());
        }
        Set<String> ids = new HashSet<>();
        for (PetTrait trait : traits) {
            if (trait == null) throw new IllegalArgumentException("Null pet talent");
            trait.validate();
            if (!ids.add(trait.id)) throw new IllegalArgumentException("Duplicate pet talent " + trait.id);
        }
    }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public StatBlock getTotalStats() { return PetCalculator.calculate(this,List.of()); }
}
