package me.opd;
import me.opd.DataReading.StatBlock;
import me.opd.Optimization.*;
import java.util.*;
/** Frozen pre-tuning arithmetic and detail construction (2026-11-05). */
class LegacyFitness {
    public static FitnessBreakdown evaluate(StatBlock stats, List<StatTarget> targets) {
        return evaluate(stats,targets,null);
    }
    public static FitnessBreakdown evaluate(StatBlock stats,List<StatTarget> targets,StatScaling scaling) {
        double deficit = 0, objective = 0;
        boolean feasible = true;
        List<FitnessBreakdown.Component> components = new ArrayList<>();
        for (StatTarget target : targets) {
            double actual = scaling==null || target.mode==StatTarget.Mode.IGNORE
                ? stats.effective(target.stat,target.school) : scaling.effective(stats,target.stat,target.school);
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

