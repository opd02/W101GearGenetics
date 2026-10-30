package me.opd.Optimization;
import java.util.*;
public record FitnessBreakdown(boolean feasible, double hardDeficit, double objective, List<Component> components)
        implements Comparable<FitnessBreakdown> {
    public record Component(String stat, String mode, double actual, double desired, boolean mandatory,
                            boolean achieved, double normalizedDistance, double weight, double scale, double hardContribution, double contribution) {}
    /** Larger is always better. Feasibility dominates any finite soft score. */
    @Override public int compareTo(FitnessBreakdown other) {
        int result = Boolean.compare(feasible,other.feasible);
        if (result == 0) result = Double.compare(other.hardDeficit,hardDeficit);
        if (result == 0) result = Double.compare(objective,other.objective);
        return result;
    }
}
