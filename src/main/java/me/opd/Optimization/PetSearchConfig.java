package me.opd.Optimization;

import me.opd.DataReading.GearSlot;
import java.util.*;

/** Optional pet planning. Omitted settings retain the predefined-pet search. */
public class PetSearchConfig {
    public enum Mode { PREDEFINED, AUTO }
    public Mode mode=Mode.PREDEFINED;
    public GearSelector template;
    /** Exact display names or IDs; five manifested talents are selected from this pool. */
    public List<String> talentPool=new ArrayList<>();
    public List<String> lockedTalents=new ArrayList<>();
    public void validate(OptimizationRequest request) {
        if (mode==null || talentPool==null || lockedTalents==null)
            throw new IllegalArgumentException("petSearch sections cannot be null");
        for (String name : talentPool) checkName(name);
        for (String name : lockedTalents) checkName(name);
        if (lockedTalents.size()>5) throw new IllegalArgumentException("At most five pet talents can be locked");
        if ((!lockedTalents.isEmpty() || mode==Mode.AUTO) && !request.requiredSlots.contains(GearSlot.PET))
            throw new IllegalArgumentException("petSearch requires PET in requiredSlots");
        if (mode==Mode.AUTO) {
            if (template==null) throw new IllegalArgumentException("AUTO petSearch needs a template pet name");
            template.validate();
            if (template.slot!=null && template.slot!=GearSlot.PET)
                throw new IllegalArgumentException("AUTO pet template must be PET gear");
            if (talentPool.size()<5 || talentPool.size()>20)
                throw new IllegalArgumentException("AUTO pet talentPool needs 5..20 distinct talent names or IDs");
            if (request.wizard.ownedOnly)
                throw new IllegalArgumentException("AUTO generates hypothetical pets; use PREDEFINED for ownedOnly");
        }
    }
    private static void checkName(String name) {
        if (name==null || GearSelector.normalize(name).isEmpty())
            throw new IllegalArgumentException("Pet talent selections need nonblank names or IDs");
    }
}
