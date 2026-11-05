package me.opd.DataReading;
import me.opd.JewelHandeling.Jewel;
import me.opd.Optimization.WizardProfile;
import me.opd.PetHandeling.PetTrait;
import java.util.*;
public class Catalog {
    public int schemaVersion = 1;
    public List<Gear> gear = new ArrayList<>();
    public List<Jewel> jewels = new ArrayList<>();
    /** Supported standalone talent definitions for optional automatic pet planning. */
    public List<PetTrait> petTalents = new ArrayList<>();
    public List<SetBonus> setBonuses = new ArrayList<>();
    /** Exact school:level keys; no interpolation or guessed level scaling. */
    public Map<String,StatBlock> baseStats = new TreeMap<>();
    public Map<String,Object> extensions = new TreeMap<>();
    public void validate() {
        if (schemaVersion != 1 || gear == null || jewels == null || petTalents == null || setBonuses == null || baseStats == null || extensions == null)
            throw new IllegalArgumentException("Unsupported or incomplete catalog schema");
        Set<String> ids = new HashSet<>(), thresholds = new HashSet<>(), sets = new HashSet<>();
        Set<String> talentIds=new HashSet<>();
        for (PetTrait talent : petTalents) {
            if (talent==null) throw new IllegalArgumentException("Null pet talent definition");
            talent.validate();
            if (!talentIds.add(talent.id)) throw new IllegalArgumentException("Duplicate pet talent definition " + talent.id);
        }
        for (Gear item : gear) { if (item == null) throw new IllegalArgumentException("Null gear record"); item.validate(); if (!ids.add(item.id)) throw new IllegalArgumentException("Duplicate item ID " + item.id); }
        for (Jewel item : jewels) { if (item == null) throw new IllegalArgumentException("Null jewel record"); item.validate(); if (!ids.add(item.id)) throw new IllegalArgumentException("Duplicate item ID " + item.id); }
        for (SetBonus bonus : setBonuses) {
            if (bonus == null) throw new IllegalArgumentException("Null set rule");
            bonus.validate(); sets.add(bonus.setId);
            if (!thresholds.add(bonus.setId + ":" + bonus.pieces)) throw new IllegalArgumentException("Duplicate set threshold");
        }
        for (Gear item : gear) if (!sets.containsAll(item.setIds)) throw new IllegalArgumentException("Missing set rules for " + item.id);
        baseStats.forEach((k,v) -> {
            String[] p = k.split(":");
            if (p.length != 2 || School.valueOf(p[0]) == School.UNIVERSAL || Integer.parseInt(p[1]) < 1)
                throw new IllegalArgumentException("Invalid base-stat key " + k);
            v.validate();
        });
    }
    public StatBlock resolveBaseStats(WizardProfile wizard) {
        StatBlock source = baseStats.get(wizard.school + ":" + wizard.level);
        if (source == null && wizard.baseOverrides == null)
            throw new IllegalArgumentException("No base stats for " + wizard.school + " level " + wizard.level
                + ". Supply wizard.baseOverrides (an explicit empty block means zero), or add an exact catalog baseStats record.");
        StatBlock total = source == null ? new StatBlock() : source.copy();
        if (wizard.baseOverrides != null) total.values.putAll(wizard.baseOverrides.values);
        return total;
    }
}
