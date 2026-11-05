package me.opd.PetHandeling;
import me.opd.DataReading.StatBlock;
import java.util.Map;
/** Archive-derived weighted attribute effect. Coefficients are already in output stat units. */
public class PetStatEffect {
    public PetAttribute primaryStat1, primaryStat2, secondaryStat;
    public double secondaryModifier;
    public StatBlock coefficients = new StatBlock();
    public void validate() {
        if (primaryStat1 == null || primaryStat2 == null || secondaryStat == null || coefficients == null
                || !Double.isFinite(secondaryModifier) || secondaryModifier < 0)
            throw new IllegalArgumentException("Pet effect needs three attributes, a nonnegative modifier and coefficients");
        coefficients.validate();
    }
    public double attributeWeight(Map<PetAttribute,Double> attributes) {
        for (var key : new PetAttribute[]{primaryStat1,primaryStat2,secondaryStat})
            if (!attributes.containsKey(key)) throw new IllegalArgumentException("Missing pet attribute " + key);
        return attributes.get(primaryStat1) + attributes.get(primaryStat2) + secondaryModifier * attributes.get(secondaryStat);
    }
}
