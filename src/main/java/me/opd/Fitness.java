package me.opd;
import me.opd.DataReading.StatBlock;
import me.opd.Optimization.*;
import java.util.*;
/** Target-based scoring; ranking is lexicographic, never a magic hard-constraint penalty. */
public class Fitness {
    public static FitnessBreakdown evaluate(StatBlock stats, List<StatTarget> targets) {
        return evaluate(stats,targets,null);
    }
    public static FitnessBreakdown evaluate(StatBlock stats,List<StatTarget> targets,StatScaling scaling) {
        List<FitnessBreakdown.Component> components = new ArrayList<>();
        Score score = calculate(stats,targets,scaling,components);
        return new FitnessBreakdown(score.feasible(),score.hardDeficit(),score.objective(),List.copyOf(components));
    }
    /** Ranking only: socket probes do not need an explanation for every discarded candidate. */
    public static Score score(StatBlock stats,List<StatTarget> targets,StatScaling scaling) {
        return calculate(stats,targets,scaling,null);
    }
    public record Score(boolean feasible,double hardDeficit,double objective) implements Comparable<Score> {
        @Override public int compareTo(Score other) {
            int result = Boolean.compare(feasible,other.feasible);
            if (result == 0) result = Double.compare(other.hardDeficit,hardDeficit);
            if (result == 0) result = Double.compare(objective,other.objective);
            return result;
        }
    }
    private static Score calculate(StatBlock stats,List<StatTarget> targets,StatScaling scaling,
                                   List<FitnessBreakdown.Component> components) {
        double deficit = 0, objective = 0;
        boolean feasible = true;
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
            if (components != null) components.add(new FitnessBreakdown.Component(StatBlock.key(target.stat,target.school),target.mode.name(),
                actual,target.value,target.mandatory,achieved,distance,target.weight,target.scale,
                target.mandatory ? target.weight*distance : 0,contribution));
        }
        if (!Double.isFinite(deficit) || !Double.isFinite(objective))
            throw new IllegalArgumentException("Scoring overflow: reduce values/weights or increase normalization scale");
        return new Score(feasible,deficit,objective);
    }
}
