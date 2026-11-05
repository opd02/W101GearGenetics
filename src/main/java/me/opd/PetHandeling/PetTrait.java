package me.opd.PetHandeling;
import me.opd.DataReading.StatBlock;
import java.util.*;
public class PetTrait {
    public String id, name;
    /** Fixed observed bonuses; dynamic effects are evaluated separately against the selected pet. */
    public StatBlock statBonus = new StatBlock();
    public List<String> itemCards = new ArrayList<>();
    public Map<PetAttribute,Double> attributeModifiers = new EnumMap<>(PetAttribute.class);
    public List<PetStatEffect> effects = new ArrayList<>();
    public void validate() {
        if (id == null || id.isBlank() || name == null || name.isBlank() || statBonus == null || itemCards == null
                || attributeModifiers == null || effects == null)
            throw new IllegalArgumentException("Pet talent requires ID, name and resolved bonuses");
        statBonus.validate();
        attributeModifiers.forEach((key,value) -> {
            if (value == null || !Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid pet attribute modifier");
        });
        for (PetStatEffect effect : effects) { if (effect == null) throw new IllegalArgumentException("Null pet effect"); effect.validate(); }
    }
}
