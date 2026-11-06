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
    public final StatScaling scaling;
    public final List<String> selectionNotes;
    /** Archive records rejected independently of allowIncompleteItems and name exclusions. */
    public final Map<String,String> rejectedItems;
    public final AutoPetSearch autoPets;
    public final List<GearSlot> slots;
    public final Map<GearSlot,List<Gear>> gear = new EnumMap<>(GearSlot.class);
    private final Map<String,List<List<Jewel>>> sockets = new HashMap<>();
    private final Map<GearSlot,Set<String>> gearIds = new EnumMap<>(GearSlot.class);
    private final Map<String,List<Set<String>>> socketIds = new HashMap<>();
    public SearchSpace(Catalog catalog, OptimizationRequest inputRequest) {
        inputRequest.validate(); catalog.validate();
        var resolved=GearNameResolver.resolve(catalog,inputRequest);
        OptimizationRequest request=resolved.request();
        List<String> notes=new ArrayList<>(resolved.notes());
        Map<String,String> rejected=new TreeMap<>();
        Map<String,String> rejectedNames=new TreeMap<>();
        for (Gear item : catalog.gear) {
            String reason=PlayerItemFilter.rejectionReason(item);
            if (reason!=null) { rejected.put(item.id,reason); rejectedNames.put(item.id,item.name); }
        }
        for (Jewel item : catalog.jewels) {
            String reason=PlayerItemFilter.rejectionReason(item);
            if (reason!=null) { rejected.put(item.id,reason); rejectedNames.put(item.id,item.name); }
        }
        rejectedItems=Collections.unmodifiableMap(rejected);
        if (!rejected.isEmpty()) {
            Map<String,Integer> counts=new TreeMap<>();
            rejected.values().forEach(reason -> counts.merge(reason,1,Integer::sum));
            notes.add("Player-item filter rejected "+rejected.size()+" archive record(s): "+counts
                +". Examples: "+rejectedNames.entrySet().stream().limit(5).map(e -> e.getValue()+" ["+e.getKey()+"]").toList()
                +". Remaining archive records still require retail availability verification.");
        }
        boolean automatic=request.petSearch.mode==PetSearchConfig.Mode.AUTO;
        var definitions=PetTalentResolver.definitions(catalog,automatic);
        var lockedTalents=PetTalentResolver.resolve(request.petSearch.lockedTalents,definitions,"petSearch.lockedTalents");
        var talentPool=automatic ? PetTalentResolver.resolve(request.petSearch.talentPool,definitions,"petSearch.talentPool") : List.<me.opd.PetHandeling.PetTrait>of();
        this.request=request; this.catalog=catalog; base=catalog.resolveBaseStats(request.wizard);
        scaling=StatScaling.forRequest(request,catalog);
        slots=Arrays.stream(GearSlot.values()).filter(request.requiredSlots::contains).toList();
        List<Jewel> eligibleJewels=catalog.jewels.stream().filter(j -> !rejectedItems.containsKey(j.id) && allowed(j.id,j.source,j.category,j.complete)
            && j.eligible(request.wizard.school,request.wizard.level)).sorted(Comparator.comparing(j -> j.id)).toList();
        for (GearSlot slot : slots) {
            List<Gear> candidates=new ArrayList<>();
            for (Gear source : catalog.gear.stream().filter(g -> g.slot==slot).sorted(Comparator.comparing(g -> g.id)).toList()) {
                Gear item=source;
                if (rejectedItems.containsKey(item.id) || !allowed(item.id,item.source,item.category,item.complete) || !item.eligible(request.wizard.school,request.wizard.level)
                    || (request.wizard.ownedOnly && item.pet != null && item.pet.hypothetical)
                    || (request.lockedItems.containsKey(slot) && !request.lockedItems.get(slot).equals(item.id))) continue;
                if (slot==GearSlot.PET) {
                    if (automatic) {
                        if (!request.petSearch.template.matches(item)) continue;
                        item=AutoPetSearch.emptyTemplate(item);
                    } else if (!lockedTalents.isEmpty() && (item.pet==null || !item.pet.traits.stream().map(t -> t.id).toList()
                            .containsAll(lockedTalents.stream().map(t -> t.id).toList()))) continue;
                }
                final Gear holder=item;
                List<List<Jewel>> choices=new ArrayList<>();
                boolean valid=true;
                for (String lock : request.lockedJewels.keySet())
                    if (lock.startsWith(slot + ":") && Integer.parseInt(lock.split(":")[1])>=item.sockets.size()) valid=false;
                for (int i=0;i<item.sockets.size();i++) {
                    final int index=i;
                    List<Jewel> options=new ArrayList<>(eligibleJewels.stream().filter(j ->
                        holder.sockets.get(index).accepts(j,holder,request.wizard.school,request.wizard.level)).toList());
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
                + (request.lockedItems.containsKey(slot) && rejectedItems.containsKey(request.lockedItems.get(slot)) ? "; player-item filter: "+rejectedItems.get(request.lockedItems.get(slot)) : "")
                + ". Check school, level, exclusions, ownership, completeness, player-item filtering and socket locks.");
            gear.put(slot,List.copyOf(candidates));
        }
        if (automatic) {
            var templates=gear.get(GearSlot.PET);
            if (templates.size()!=1) throw new IllegalArgumentException("Ambiguous AUTO pet template; qualify its name with level or use a PET gear ID lock");
            Gear template=templates.getFirst();
            autoPets=new AutoPetSearch(template,talentPool,lockedTalents,sockets.get(template.id),request);
            notes.add("AUTO pet search: five manifested talents from "+talentPool.size()+" definitions; template '"+template.name+"'; star jewel selected separately");
        } else autoPets=null;
        if (!lockedTalents.isEmpty()) notes.add("Locked manifested pet talents: "+lockedTalents.stream().map(t -> t.name).toList());
        selectionNotes=List.copyOf(notes);
        // Keep the ordered choice lists for sampling; index membership separately.
        // Identical socket domains share their index to avoid one large set per holder.
        Map<List<Jewel>,Set<String>> domains=new HashMap<>();
        for (GearSlot slot : slots) {
            Set<String> ids=new HashSet<>();
            for (Gear item : gear.get(slot)) {
                ids.add(item.id);
                List<Set<String>> indexed=new ArrayList<>();
                for (List<Jewel> options : socketChoices(item)) indexed.add(domains.computeIfAbsent(options,list -> {
                    Set<String> jewelIds=new HashSet<>();
                    for (Jewel jewel : list) jewelIds.add(jewel==null ? null : jewel.id);
                    return Collections.unmodifiableSet(jewelIds);
                }));
                socketIds.put(item.id,List.copyOf(indexed));
            }
            gearIds.put(slot,Set.copyOf(ids));
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
    public List<List<Jewel>> socketChoices(Gear item) {
        if (autoPets!=null && item.slot==GearSlot.PET) {
            var choices=autoPets.sockets(item);
            if (choices!=null) return choices;
        }
        return sockets.get(item.id);
    }
    /** Exact search expands pet combinations; GA sampling and mutation remain lazy. */
    public List<Gear> enumerationChoices(GearSlot slot) {
        return autoPets!=null && slot==GearSlot.PET ? autoPets.all() : gear.get(slot);
    }
    public Individual random(Random random) {
        Individual build=new Individual();
        for (GearSlot slot : slots) {
            List<Gear> options=gear.get(slot);
            build.gearSet.put(slot,autoPets!=null && slot==GearSlot.PET ? autoPets.random(random) : options.get(random.nextInt(options.size())));
        }
        repairJewels(build,random);
        return build;
    }
    /** Keep compatible choices, reserve locked copies first, then repair remaining sockets. */
    public void repairJewels(Individual build,Random random) {
        Map<String,Integer> used=new HashMap<>();
        Map<GearSlot,Integer> priorSizes=new EnumMap<>(GearSlot.class);
        for (GearSlot slot : slots) {
            List<Jewel> prior=build.jewels.getOrDefault(slot,List.of());
            priorSizes.put(slot,prior.size());
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
                // A missing choice belongs to new gear/initialization and should be sampled.
                // An explicit null is an existing empty socket and should survive repair.
                Jewel chosen=i<priorSizes.get(slot) && options.contains(prior) ? prior : options.get(random.nextInt(options.size()));
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
            boolean autoPet=autoPets!=null && slot==GearSlot.PET;
            if (item==null || item.id==null || (autoPet ? !autoPets.contains(item) : !gearIds.get(slot).contains(item.id)))
                throw new IllegalArgumentException("Illegal equipment in " + slot);
            var choices=socketChoices(item); var selected=build.jewels.get(slot);
            if (selected==null || selected.size()!=choices.size()) throw new IllegalArgumentException("Socket count mismatch: " + slot);
            for (int i=0;i<choices.size();i++) {
                Jewel jewel=selected.get(i);
                if (autoPet ? !autoPets.accepts(item,i,jewel) : (jewel!=null && jewel.id==null)
                        || !socketIds.get(item.id).get(i).contains(jewel==null ? null : jewel.id))
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
            for (Gear item : enumerationChoices(slot)) {
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
