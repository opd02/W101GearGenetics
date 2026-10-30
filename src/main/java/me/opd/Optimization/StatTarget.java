package me.opd.Optimization;
import me.opd.DataReading.*;
public class StatTarget {
    public enum Mode { MINIMUM, MAXIMUM, PREFERRED, MAXIMIZE, IGNORE }
    public StatType stat;
    public School school = School.UNIVERSAL;
    public Mode mode;
    public double value, weight = 1, scale = 1, belowPenalty = 1, abovePenalty = 1;
    public boolean mandatory;
    public void validate() {
        if (stat == null || school == null || mode == null) throw new IllegalArgumentException("Target needs stat, school and mode");
        StatBlock.key(stat, school);
        for (double v : new double[]{value,weight,scale,belowPenalty,abovePenalty})
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Target values must be finite");
        if (value < 0 || weight <= 0 || scale <= 0 || belowPenalty < 0 || abovePenalty < 0)
            throw new IllegalArgumentException("Target value/penalties must be nonnegative; weight/scale positive");
        if (mandatory && mode != Mode.MINIMUM && mode != Mode.MAXIMUM)
            throw new IllegalArgumentException("Only minimum/maximum targets can be mandatory");
    }
    public double distance(double actual) {
        return switch (mode) {
            case MINIMUM -> Math.max(0, value - actual);
            case MAXIMUM -> Math.max(0, actual - value);
            case PREFERRED -> actual < value ? (value-actual)*belowPenalty : (actual-value)*abovePenalty;
            case MAXIMIZE, IGNORE -> 0;
        };
    }
}
