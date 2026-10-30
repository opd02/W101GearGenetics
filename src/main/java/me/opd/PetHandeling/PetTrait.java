package me.opd.PetHandeling;
import me.opd.DataReading.StatBlock;
import java.util.*;
public class PetTrait {
    public String id, name;
    /** Resolved bonuses from this pet's attributes, not guessed talent formulas. */
    public StatBlock statBonus = new StatBlock();
    public List<String> itemCards = new ArrayList<>();
    public void validate() {
        if (id == null || id.isBlank() || name == null || name.isBlank() || statBonus == null || itemCards == null)
            throw new IllegalArgumentException("Pet talent requires ID, name and resolved bonuses");
        statBonus.validate();
    }
}
