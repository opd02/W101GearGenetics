package me.opd.DataReading;
import java.util.*;
/** Each reached threshold is additive, once per build. */
public class SetBonus {
    public String setId;
    public int pieces;
    public StatBlock stats = new StatBlock();
    public List<String> itemCards = new ArrayList<>();
    public void validate() {
        if (setId == null || setId.isBlank() || pieces < 1 || stats == null || itemCards == null)
            throw new IllegalArgumentException("Invalid set threshold");
        stats.validate();
    }
}
