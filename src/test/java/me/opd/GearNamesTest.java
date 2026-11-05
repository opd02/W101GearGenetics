package me.opd;

import me.opd.DataReading.*;
import me.opd.GA.GAConfig;
import me.opd.Optimization.*;
import me.opd.Storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearNamesTest {
    @TempDir Path temp;
    Gear gear(String id,String name,GearSlot slot,int level) {
        Gear g=OptimizerTest.gear(id,slot); g.name=name; g.level=level; return g;
    }
    OptimizationRequest request(GearSlot... slots) {
        var r=OptimizerTest.request(slots); r.wizard.level=180; r.wizard.school=School.STORM; return r;
    }
    @Test void exactNameLocksAndAllVersionExclusionsSurviveSearchWithoutMutatingRequest() {
        Gear chosen=gear("chosen","Captain's Cap",GearSlot.HAT,180),other=gear("other","Other Cap",GearSlot.HAT,180);
        Gear banned=gear("banned160","Unwanted Robe",GearSlot.ROBE,160),banned2=gear("banned180","Unwanted Robe",GearSlot.ROBE,180);
        Gear kept=gear("kept","Kept Robe",GearSlot.ROBE,180);
        Catalog c=OptimizerTest.catalog(chosen,other,banned,banned2,kept); var r=request(GearSlot.HAT,GearSlot.ROBE);
        r.lockedGear.put(GearSlot.HAT,new GearSelector("  CAPTAIN’S   cap ")); r.excludedGear.add(new GearSelector("unwanted robe"));
        var ga=new Optimizer().optimize(c,r);
        for (var solution : ga.solutions()) {
            assertEquals("chosen",solution.equipment().gearSet.get(GearSlot.HAT).id);
            assertEquals("kept",solution.equipment().gearSet.get(GearSlot.ROBE).id);
        }
        r.search.method=GAConfig.Method.EXACT;
        assertEquals(ga.solutions().getFirst().equipment().signature(),new Optimizer().optimize(c,r).solutions().getFirst().equipment().signature());
        assertTrue(r.lockedItems.isEmpty()); assertTrue(r.excludedIds.isEmpty());
        assertTrue(ga.warnings().stream().anyMatch(s -> s.contains("Locked HAT by name")));
        assertTrue(ga.warnings().stream().anyMatch(s -> s.contains("2 matching catalog")));
    }
    @Test void ambiguousLocksRequireLevelAndNeverChooseAnArbitraryVersion() {
        Gear low=gear("low","Same Hat",GearSlot.HAT,160),high=gear("high","Same Hat",GearSlot.HAT,180);
        Gear future=gear("future","Same Hat",GearSlot.HAT,190),wrong=gear("wrong","Same Hat",GearSlot.HAT,180);
        wrong.schools.add(School.FIRE); Catalog c=OptimizerTest.catalog(low,high,future,wrong);
        var r=request(GearSlot.HAT); r.lockedGear.put(GearSlot.HAT,new GearSelector("same hat"));
        String error=assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r)).getMessage();
        assertTrue(error.contains("Ambiguous")); assertTrue(error.contains("level 160")); assertTrue(error.contains("level 180"));
        assertFalse(error.contains("level 190"));
        r.lockedGear.put(GearSlot.HAT,new GearSelector("Same Hat",180,null));
        assertEquals(List.of(high),new SearchSpace(c,r).gear.get(GearSlot.HAT));
        // Even identical levels still require explicit resolution when catalog identities differ.
        Gear duplicate=gear("another","Same Hat",GearSlot.HAT,180); c.gear.add(duplicate);
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
        r.lockedItems.put(GearSlot.HAT,"high");
        assertEquals(List.of(high),new SearchSpace(c,r).gear.get(GearSlot.HAT));
    }
    @Test void exclusionQualifiersNarrowLevelAndSlotWhilePlainNameExcludesEveryVersion() {
        Gear low=gear("low","Same",GearSlot.HAT,160),high=gear("high","Same",GearSlot.HAT,180);
        Gear hat=gear("hat","Other Hat",GearSlot.HAT,180),robe=gear("robe","Same",GearSlot.ROBE,160);
        Gear otherRobe=gear("other-robe","Other Robe",GearSlot.ROBE,180);
        Catalog c=OptimizerTest.catalog(low,high,hat,robe,otherRobe); var r=request(GearSlot.HAT,GearSlot.ROBE);
        r.excludedGear.add(new GearSelector("Same",160,GearSlot.HAT)); SearchSpace space=new SearchSpace(c,r);
        assertFalse(space.gear.get(GearSlot.HAT).contains(low)); assertTrue(space.gear.get(GearSlot.HAT).contains(high));
        assertTrue(space.gear.get(GearSlot.ROBE).contains(robe));
        r.excludedGear=List.of(new GearSelector("Same")); space=new SearchSpace(c,r);
        assertEquals(List.of(hat),space.gear.get(GearSlot.HAT)); assertEquals(List.of(otherRobe),space.gear.get(GearSlot.ROBE));
    }
    @Test void namesRespectEligibilityInventoryAndCompletenessAndConflictsAreErrors() {
        Gear owned=gear("owned","My Hat",GearSlot.HAT,180),unowned=gear("unowned","My Hat",GearSlot.HAT,160);
        Gear partial=gear("partial","My Hat",GearSlot.HAT,170); partial.complete=false;
        Gear wrongSchool=gear("wrong-school","My Hat",GearSlot.HAT,180); wrongSchool.schools.add(School.DEATH);
        var r=request(GearSlot.HAT); r.wizard.ownedOnly=true; r.wizard.ownedItemIds.addAll(List.of("owned","partial","wrong-school"));
        r.lockedGear.put(GearSlot.HAT,new GearSelector("My Hat"));
        Catalog c=OptimizerTest.catalog(owned,unowned,partial,wrongSchool);
        assertEquals(List.of(owned),new SearchSpace(c,r).gear.get(GearSlot.HAT));
        r.excludedGear.add(new GearSelector("My Hat"));
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r)).getMessage().contains("no eligible match"));
        r.excludedGear.clear(); r.lockedItems.put(GearSlot.HAT,"not-the-same-name");
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r)).getMessage().contains("Conflicting"));
    }
    @Test void unknownAndPartialNamesAreNotSilentlyIgnoredOrFuzzySelected() {
        Catalog c=OptimizerTest.catalog(gear("cap","Captain's Cap",GearSlot.HAT,180)); var r=request(GearSlot.HAT);
        r.lockedGear.put(GearSlot.HAT,new GearSelector("Captain"));
        String error=assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r)).getMessage();
        assertTrue(error.contains("not found")); assertTrue(error.contains("Captain's Cap"));
        r.lockedGear.clear(); r.excludedGear.add(new GearSelector("missing item"));
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
        r.excludedGear.clear(); r.lockedGear.put(GearSlot.HAT,new GearSelector("Captain's Cap",180,GearSlot.ROBE));
        assertThrows(IllegalArgumentException.class,r::validate);
        r.lockedGear.clear(); r.excludedGear.add(new GearSelector(" "));
        assertThrows(IllegalArgumentException.class,r::validate);
    }
    @Test void stringAndObjectJsonSelectorsRoundTripThroughSavedProfiles() throws Exception {
        var r=JsonIO.decode("""
            {"wizard":{"school":"STORM","level":180,"baseOverrides":{"values":{}}},"requiredSlots":["HAT"],
             "lockedGear":{"HAT":"Captain's Cap"},"excludedGear":[{"name":"Unwanted Robe","level":160,"slot":"ROBE"}]}
            """,OptimizationRequest.class);
        r.validate(); assertEquals("Captain's Cap",r.lockedGear.get(GearSlot.HAT).name);
        assertEquals(160,r.excludedGear.getFirst().level); assertEquals(GearSlot.ROBE,r.excludedGear.getFirst().slot);
        try (var db=new SqliteItemRepository(temp.resolve("names.sqlite"))) {
            db.saveProfile("friendly",r); var saved=db.loadProfile("friendly");
            assertEquals(JsonIO.encode(r),JsonIO.encode(saved));
        }
        assertThrows(IllegalArgumentException.class,() -> JsonIO.decode("{\"lockedGear\":{\"HAT\":{\"name\":\"Cap\",\"typo\":180}}}",OptimizationRequest.class));
        var bad=request(GearSlot.HAT); bad.lockedGear.put(GearSlot.HAT,new GearSelector("Cap",0,null));
        assertThrows(IllegalArgumentException.class,bad::validate);
    }
    @Test void cliFindSearchesNamesAndOptimizeSavesFriendlyRequestRatherThanCompiledIds() throws Exception {
        Gear chosen=gear("chosen","Captain's Cap",GearSlot.HAT,180),other=gear("other","Other Cap",GearSlot.HAT,170);
        Catalog c=OptimizerTest.catalog(chosen,other); Path catalog=temp.resolve("catalog.json"); JsonIO.write(catalog,c);
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        assertEquals(0,Main.run(new String[]{"find","--catalog",catalog.toString(),"--name","CAP","--slot","HAT","--school","STORM","--level","180"},new PrintStream(out),new PrintStream(err)),err.toString());
        assertTrue(out.toString().contains("Captain's Cap | HAT | level 180")); assertTrue(out.toString().contains("2 matching gear records"));
        var r=request(GearSlot.HAT); r.lockedGear.put(GearSlot.HAT,new GearSelector("Captain's Cap"));
        r.excludedGear.add(new GearSelector("Other Cap")); Path request=temp.resolve("request.json"),saved=temp.resolve("saved.json"),output=temp.resolve("result.json");
        JsonIO.write(request,r);
        assertEquals(0,Main.run(new String[]{"optimize","--request",request.toString(),"--catalog",catalog.toString(),"--save-request",saved.toString(),"--output",output.toString()},new PrintStream(out),new PrintStream(err)),err.toString());
        assertEquals("chosen",JsonIO.read(output,OptimizationResult.class).solutions().getFirst().equipment().gearSet.get(GearSlot.HAT).id);
        var savedRequest=JsonIO.readRequest(saved); assertTrue(savedRequest.lockedItems.isEmpty()); assertTrue(savedRequest.excludedIds.isEmpty());
        assertEquals("Captain's Cap",savedRequest.lockedGear.get(GearSlot.HAT).name);
    }
    @Test void interactiveNameEntrySupportsCommasAndQualifiedVersions() throws Exception {
        InputStream previous=System.in; ByteArrayOutputStream out=new ByteArrayOutputStream();
        try {
            String answers="STORM\n180\n170\nyes\n\n\n{\"HAT\":{\"name\":\"Hat, Fancy\",\"level\":180}}\n\n[\"Other, Fancy\"]\nno\n7\n";
            System.setIn(new ByteArrayInputStream(answers.getBytes(StandardCharsets.UTF_8)));
            Path file=temp.resolve("interactive.json");
            assertEquals(0,Main.run(new String[]{"init","--output",file.toString()},new PrintStream(out),new PrintStream(out)),out.toString());
            var r=JsonIO.readRequest(file); assertEquals("Hat, Fancy",r.lockedGear.get(GearSlot.HAT).name);
            assertEquals(180,r.lockedGear.get(GearSlot.HAT).level); assertEquals("Other, Fancy",r.excludedGear.getFirst().name);
        } finally { System.setIn(previous); }
    }
}
