package me.opd.Optimization;
import me.opd.*;
import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import java.util.*;
/** Precompiled per-request domains. All operators use these domains, including socket locks. */
public class SearchSpace {
    public final OptimizationRequest request;
    public final Catalog catalog;
    public final StatBlock base;
    public final List<GearSlot> slots;
    public final Map<GearSlot,List<Gear>> gear = new EnumMap<>(GearSlot.class);
    private final Map<String,List<List<Jewel>>> sockets = new HashMap<>();
    public SearchSpace(Catalog catalog, OptimizationRequest request) {
        request.validate(); catalog.validate();
        this.request=request; this.catalog=catalog; base=catalog.resolveBaseStats(request.wizard);
        slots=Arrays.stream(GearSlot.values()).filter(request.requiredSlots::contains).toList();
        List<Jewel> eligibleJewels=catalog.jewels.stream().filter(j -> allowed(j.id,j.source,j.category,j.complete)
            && j.eligible(request.wizard.school,request.wizard.level)).sorted(Comparator.comparing(j -> j.id)).toList();
        for (GearSlot slot : slots) {
            List<Gear> candidates=new ArrayList<>();
            for (Gear item : catalog.gear.stream().filter(g -> g.slot==slot).sorted(Comparator.comparing(g -> g.id)).toList()) {
                if (!allowed(item.id,item.source,item.category,item.complete) || !item.eligible(request.wizard.school,request.wizard.level)
                    || (request.wizard.ownedOnly && item.pet != null && item.pet.hypothetical)
                    || (request.lockedItems.containsKey(slot) && !request.lockedItems.get(slot).equals(item.id))) continue;
                List<List<Jewel>> choices=new ArrayList<>();
                boolean valid=true;
                for (String lock : request.lockedJewels.keySet())
                    if (lock.startsWith(slot + ":") && Integer.parseInt(lock.split(":")[1])>=item.sockets.size()) valid=false;
                for (int i=0;i<item.sockets.size();i++) {
                    final int index=i;
                    List<Jewel> options=new ArrayList<>(eligibleJewels.stream().filter(j ->
                        item.sockets.get(index).accepts(j,item,request.wizard.school,request.wizard.level)).toList());
                    String lock=request.lockedJewels.get(slot + ":" + i);
                    if (lock != null) {
                        options.removeIf(j -> !lock.equals(j.id));
                        if (options.isEmpty()) valid=false;
                    } else options.add(0,null); // empty sockets are legal and keep their indices
                    choices.add(Collections.unmodifiableList(options));
                }
                if (valid) { candidates.add(item); sockets.put(item.id,choices); }
            }
            if (candidates.isEmpty()) throw new IllegalArgumentException("No valid candidates for " + slot
                + (request.lockedItems.containsKey(slot) ? " (locked " + request.lockedItems.get(slot) + ")" : "")
                + ". Check school, level, exclusions, ownership, completeness and socket locks.");
            gear.put(slot,List.copyOf(candidates));
        }
        Map<String,Integer> lockedCounts=new HashMap<>();
        request.lockedJewels.values().forEach(id -> lockedCounts.merge(id,1,Integer::sum));
        lockedCounts.forEach((id,count) -> {
            if (request.wizard.ownedOnly && count>request.wizard.quantity(id))
                throw new IllegalArgumentException("Not enough owned copies of locked jewel " + id);
        });
    }
    private boolean allowed(String id,String source,String category,boolean complete) {
        return !request.excludedIds.contains(id) && !request.excludedSources.contains(source)
            && !request.excludedCategories.contains(category) && (complete || request.allowIncompleteItems)
            && (!request.wizard.ownedOnly || request.wizard.quantity(id)>0);
    }
    public List<List<Jewel>> socketChoices(Gear item) { return sockets.get(item.id); }
    public Individual random(Random random) {
        Individual build=new Individual();
        for (GearSlot slot : slots) {
            List<Gear> options=gear.get(slot);
            build.gearSet.put(slot,options.get(random.nextInt(options.size())));
        }
        repairJewels(build,random);
        return build;
    }
    /** Keep compatible choices, reserve locked copies first, then repair remaining sockets. */
    public void repairJewels(Individual build,Random random) {
        Map<String,Integer> used=new HashMap<>();
        for (GearSlot slot : slots) {
            List<Jewel> prior=build.jewels.getOrDefault(slot,List.of());
            List<Jewel> replacement=new ArrayList<>();
            List<List<Jewel>> choices=socketChoices(build.gearSet.get(slot));
            for (int i=0;i<choices.size();i++) {
                String lock=request.lockedJewels.get(slot + ":" + i);
                Jewel chosen=lock == null ? (i<prior.size() ? prior.get(i) : null) : choices.get(i).getFirst();
                replacement.add(chosen);
                if (lock != null) used.merge(chosen.id,1,Integer::sum);
            }
            build.jewels.put(slot,replacement);
        }
        for (GearSlot slot : slots) {
            var lists=socketChoices(build.gearSet.get(slot));
            var selected=build.jewels.get(slot);
            for (int i=0;i<lists.size();i++) if (!request.lockedJewels.containsKey(slot + ":" + i)) {
                List<Jewel> options=lists.get(i).stream().filter(j -> available(j,used)).toList();
                Jewel prior=selected.get(i);
                Jewel chosen=options.contains(prior) ? prior : options.get(random.nextInt(options.size()));
                selected.set(i,chosen);
                if (chosen != null) used.merge(chosen.id,1,Integer::sum);
            }
        }
    }
    public boolean available(Jewel jewel,Map<String,Integer> used) {
        return jewel==null || !request.wizard.ownedOnly || used.getOrDefault(jewel.id,0)<request.wizard.quantity(jewel.id);
    }
    public void validate(Individual build) {
        if (!build.gearSet.keySet().equals(request.requiredSlots) || !build.jewels.keySet().equals(request.requiredSlots))
            throw new IllegalArgumentException("Build must contain exactly the selected equipment slots");
        Map<String,Integer> used=new HashMap<>();
        for (GearSlot slot : slots) {
            Gear item=build.gearSet.get(slot);
            if (item==null || gear.get(slot).stream().noneMatch(g -> g.id.equals(item.id)))
                throw new IllegalArgumentException("Illegal equipment in " + slot);
            var choices=socketChoices(item); var selected=build.jewels.get(slot);
            if (selected==null || selected.size()!=choices.size()) throw new IllegalArgumentException("Socket count mismatch: " + slot);
            for (int i=0;i<choices.size();i++) {
                Jewel jewel=selected.get(i);
                if (choices.get(i).stream().noneMatch(j -> j==null ? jewel==null : jewel!=null && j.id.equals(jewel.id)))
                    throw new IllegalArgumentException("Incompatible/locked jewel: " + slot + ":" + i);
                if (!available(jewel,used)) throw new IllegalArgumentException("Owned jewel quantity exceeded: " + jewel.id);
                if (jewel!=null) used.merge(jewel.id,1,Integer::sum);
            }
        }
    }
    /** Upper bound includes empty sockets; used to refuse excessive exact searches before enumeration. */
    public long combinations(long limit) {
        long total=1;
        for (GearSlot slot : slots) {
            long sum=0;
            for (Gear item : gear.get(slot)) {
                long n=1;
                for (var choices : socketChoices(item)) { if (n>limit/choices.size()) return limit+1; n*=choices.size(); }
                if (sum>limit-n) return limit+1;
                sum+=n;
            }
            if (total>limit/sum) return limit+1;
            total*=sum;
        }
        return total;
    }
}
