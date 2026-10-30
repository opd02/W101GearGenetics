package me.opd;
import me.opd.DataReading.*;
import java.util.*;
public class StatBounder {
    /** Additive gear-only bounds for represented slots; excludes sockets, pets and set rules. */
    public static double[] findDamageBounds(List<Gear> pool, School school) {
        double min = 0, max = 0;
        for (GearSlot slot : GearSlot.values()) {
            var values = pool.stream().filter(g -> g.slot == slot)
                .mapToDouble(g -> g.baseStats.effective(StatType.DAMAGE,school)).summaryStatistics();
            if (values.getCount() != 0) { min += values.getMin(); max += values.getMax(); }
        }
        return new double[]{min,max};
    }
}
