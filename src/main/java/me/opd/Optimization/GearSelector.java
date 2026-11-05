package me.opd.Optimization;

import com.fasterxml.jackson.annotation.*;
import me.opd.DataReading.*;
import java.text.Normalizer;
import java.util.Locale;

/** User-facing exact gear name, optionally qualified by item level and slot. */
public class GearSelector {
    public String name;
    public Integer level;
    public GearSlot slot;
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public GearSelector(String name) { this.name=name; }
    @JsonCreator(mode=JsonCreator.Mode.PROPERTIES)
    public GearSelector(@JsonProperty("name") String name,@JsonProperty("level") Integer level,@JsonProperty("slot") GearSlot slot) {
        this.name=name; this.level=level; this.slot=slot;
    }
    public void validate() {
        if (name==null || normalize(name).isEmpty() || level!=null && level<1)
            throw new IllegalArgumentException("Gear selection needs a nonblank name and, if supplied, a positive item level");
    }
    public boolean matches(Gear gear) {
        return normalize(name).equals(normalize(gear.name)) && (level==null || level==gear.level) && (slot==null || slot==gear.slot);
    }
    public static String normalize(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKC).replace('\u2019','\'').replace('\u2018','\'')
            .strip().replaceAll("(?U)\\s+"," ").toLowerCase(Locale.ROOT);
    }
}
