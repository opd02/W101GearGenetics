package me.opd;
import me.opd.DataReading.StatBlock;
import me.opd.Optimization.*;
import java.util.*;
/** Target-based scoring; ranking is lexicographic, never a magic hard-constraint penalty. */
public class Fitness {
    public static FitnessBreakdown evaluate(StatBlock stats, List<StatTarget> targets) {
        double deficit = 0, objective = 0;
        boolean feasible = true;
        List<FitnessBreakdown.Component> components = new ArrayList<>();
        for (StatTarget target : targets) {
            double actual = stats.effective(target.stat,target.school);
            double distance = target.distance(actual) / target.scale;
            boolean achieved = distance == 0;
            double contribution = 0;
            if (target.mandatory) {
                deficit += target.weight * distance;
                feasible &= achieved;
            } else {
                contribution = target.mode == StatTarget.Mode.MAXIMIZE ? target.weight * actual / target.scale : -target.weight * distance;
                objective += contribution;
            }
            components.add(new FitnessBreakdown.Component(StatBlock.key(target.stat,target.school),target.mode.name(),
                actual,target.value,target.mandatory,achieved,distance,target.weight,target.scale,
                target.mandatory ? target.weight*distance : 0,contribution));
        }
        if (!Double.isFinite(deficit) || !Double.isFinite(objective))
            throw new IllegalArgumentException("Scoring overflow: reduce values/weights or increase normalization scale");
        return new FitnessBreakdown(feasible,deficit,objective,List.copyOf(components));
    }
}
