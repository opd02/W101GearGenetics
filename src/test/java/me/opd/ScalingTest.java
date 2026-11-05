package me.opd;

import me.opd.DataReading.*;
import me.opd.GA.GAConfig;
import me.opd.JewelHandeling.Socket;
import me.opd.Optimization.*;
import me.opd.Storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScalingTest {
    @TempDir Path temp;
    OptimizationRequest request(GearSlot slot) {
        var r=OptimizerTest.request(slot); r.wizard.school=School.STORM; r.wizard.level=180; r.wizard.scaledLevel=170;
        return r;
    }
    StatTarget target(StatType stat,School school,double minimum) {
        return OptimizerTest.target(stat,school,StatTarget.Mode.MINIMUM,minimum,true);
    }
    @Test void publishedRatiosUsePrimarySchoolOffschoolCapsAndAggregateUniversalBeforeScaling() {
        var r=request(GearSlot.HAT); var scaling=StatScaling.forRequest(r,new Catalog());
        var raw=new StatBlock().put(StatType.DAMAGE,School.DEATH,340).put(StatType.DAMAGE,School.UNIVERSAL,10)
            .put(StatType.HEALTH,School.UNIVERSAL,10000).put(StatType.PIERCE,School.DEATH,60)
            .put(StatType.ACCURACY,School.STORM,35).put(StatType.POWER_PIP_CHANCE,School.UNIVERSAL,100);
        assertEquals(350.0/371*368,scaling.effective(raw,StatType.DAMAGE,School.DEATH),1e-9);
        assertEquals(10000.0/11975*11218,scaling.effective(raw,StatType.HEALTH,School.UNIVERSAL),1e-9);
        assertEquals(59,scaling.effective(raw,StatType.PIERCE,School.DEATH));
        assertEquals(35,scaling.effective(raw,StatType.ACCURACY,School.STORM));
        assertEquals(100,scaling.effective(raw,StatType.POWER_PIP_CHANCE,School.UNIVERSAL),1e-9);
        assertEquals(340,raw.raw(StatType.DAMAGE,School.DEATH));
        assertEquals(350.0/371*368,scaling.project(raw).effective(StatType.DAMAGE,School.DEATH),1e-9);
        raw.put(StatType.DAMAGE,School.DEATH,1000);
        assertEquals(368,scaling.effective(raw,StatType.DAMAGE,School.DEATH));
        r.wizard.school=School.DEATH;
        assertEquals(213,StatScaling.forRequest(r,new Catalog()).effective(raw,StatType.DAMAGE,School.DEATH));
    }
    @Test void searchesLevel180GearButRejectsBuildThatPassesOnlyBeforeScaling() {
        Gear low=OptimizerTest.gear("raw-only",GearSlot.RING); low.level=180;
        low.baseStats.put(StatType.PIERCE,School.DEATH,54);
        Gear high=OptimizerTest.gear("scaled-pass",GearSlot.RING); high.level=180;
        high.baseStats.put(StatType.PIERCE,School.DEATH,55);
        Gear ineligible=OptimizerTest.gear("too-high",GearSlot.RING); ineligible.level=181;
        ineligible.baseStats.put(StatType.PIERCE,School.DEATH,1000);
        Catalog c=OptimizerTest.catalog(low,high,ineligible); var r=request(GearSlot.RING);
        r.targets.add(target(StatType.PIERCE,School.DEATH,54));
        assertEquals(2,new SearchSpace(c,r).gear.get(GearSlot.RING).size());
        var ga=new Optimizer().optimize(c,r); var best=ga.solutions().getFirst();
        assertTrue(best.fitness().feasible()); assertEquals("scaled-pass",best.equipment().gearSet.get(GearSlot.RING).id);
        assertEquals(55,best.rawCharacterStats().effective(StatType.PIERCE,School.DEATH));
        assertEquals(55.0/60*59,best.targetStats().effective(StatType.PIERCE,School.DEATH),1e-9);
        assertEquals(best.targetStats().effective(StatType.PIERCE,School.DEATH),best.fitness().components().getFirst().actual());
        assertEquals(180,ga.scaling().characterLevel()); assertEquals(170,ga.scaling().scaledLevel());
        r.search.method=GAConfig.Method.EXACT;
        assertEquals(best.fitness(),new Optimizer().optimize(c,r).solutions().getFirst().fitness());
        r.lockedItems.put(GearSlot.RING,"raw-only");
        var failed=new Optimizer().optimize(c,r).solutions().getFirst();
        assertFalse(failed.fitness().feasible());
        assertEquals(53.1,failed.fitness().components().getFirst().actual(),1e-9);
        assertEquals(0.9/54,failed.fitness().hardDeficit(),1e-9);
    }
    @Test void socketSearchUsesScaledCapsAndKeepsHealthWhenExtraDamageIsSaturated() {
        Gear ring=OptimizerTest.gear("ring",GearSlot.RING); ring.level=180;
        ring.baseStats.put(StatType.DAMAGE,School.DEATH,400).put(StatType.HEALTH,School.UNIVERSAL,1000);
        ring.sockets.add(new Socket(JewelType.CIRCLE)); ring.sockets.add(new Socket(JewelType.CIRCLE));
        var health=OptimizerTest.jewel("health",JewelType.CIRCLE); health.level=180;
        health.statBonus.put(StatType.HEALTH,School.UNIVERSAL,1000);
        var pierce=OptimizerTest.jewel("pierce",JewelType.CIRCLE); pierce.level=180;
        pierce.statBonus.put(StatType.PIERCE,School.DEATH,55);
        var damage=OptimizerTest.jewel("wasted-damage",JewelType.CIRCLE);
        damage.statBonus.put(StatType.DAMAGE,School.DEATH,10000);
        Catalog c=OptimizerTest.catalog(ring); c.jewels.addAll(List.of(health,pierce,damage));
        var r=request(GearSlot.RING); r.targets.add(target(StatType.DAMAGE,School.DEATH,340));
        r.targets.add(target(StatType.PIERCE,School.DEATH,54));
        var maximize=OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MAXIMIZE,0,false);
        maximize.scale=100; r.targets.add(maximize);
        maximize=OptimizerTest.target(StatType.HEALTH,School.UNIVERSAL,StatTarget.Mode.MAXIMIZE,0,false);
        maximize.scale=1000; r.targets.add(maximize);
        var ga=new Optimizer().optimize(c,r).solutions().getFirst();
        assertTrue(ga.fitness().feasible());
        assertEquals(Set.of("health","pierce"),new HashSet<>(ga.equipment().jewels.get(GearSlot.RING).stream().map(j -> j.id).toList()));
        assertEquals(368,ga.targetStats().effective(StatType.DAMAGE,School.DEATH));
        r.search.method=GAConfig.Method.EXACT;
        assertEquals(new Optimizer().optimize(c,r).solutions().getFirst().fitness(),ga.fitness());
    }
    @Test void petJewelAttributeModifiersAreCalculatedBeforeScaling() {
        PetIntegrationTest fixture=new PetIntegrationTest();
        Gear pet=fixture.gear("pet",GearSlot.PET); pet.pet=fixture.pet(); pet.sockets.add(new Socket(JewelType.PET));
        Catalog c=OptimizerTest.catalog(pet); c.jewels.add(fixture.mighty());
        var r=request(GearSlot.PET); r.targets.add(target(StatType.DAMAGE,School.DEATH,17));
        var best=new Optimizer().optimize(c,r).solutions().getFirst();
        assertTrue(best.fitness().feasible());
        assertEquals(17.625,best.rawCharacterStats().effective(StatType.DAMAGE,School.DEATH),1e-9);
        assertEquals(17.625/371*368,best.targetStats().effective(StatType.DAMAGE,School.DEATH),1e-9);
    }
    @Test void explicitSameBandAppliesCapsWhileOmittedScalingKeepsRawBehavior() {
        Gear hat=OptimizerTest.gear("hat",GearSlot.HAT); hat.baseStats.put(StatType.DAMAGE,School.DEATH,1000);
        var r=request(GearSlot.HAT); r.wizard.level=179; r.wizard.scaledLevel=170;
        r.targets.add(target(StatType.DAMAGE,School.DEATH,400));
        var capped=new Optimizer().optimize(OptimizerTest.catalog(hat),r).solutions().getFirst();
        assertFalse(capped.fitness().feasible()); assertEquals(368,capped.targetStats().effective(StatType.DAMAGE,School.DEATH));
        r.wizard.scaledLevel=null;
        var raw=new Optimizer().optimize(OptimizerTest.catalog(hat),r);
        assertNull(raw.scaling()); assertTrue(raw.solutions().getFirst().fitness().feasible());
        assertEquals(1000,raw.solutions().getFirst().targetStats().effective(StatType.DAMAGE,School.DEATH));
    }
    @Test void invalidLevelsUnsupportedScopesAndMismatchedRevisionsFailClearly() {
        var r=request(GearSlot.HAT); r.wizard.scaledLevel=181;
        assertThrows(IllegalArgumentException.class,r::validate);
        r.wizard.scaledLevel=0; assertThrows(IllegalArgumentException.class,r::validate);
        r.wizard.scaledLevel=170; r.wizard.level=190;
        assertThrows(IllegalArgumentException.class,() -> StatScaling.forRequest(r,new Catalog()));
        r.wizard.level=180;
        r.targets.add(target(StatType.RESISTANCE,School.UNIVERSAL,40));
        assertTrue(assertThrows(IllegalArgumentException.class,() -> StatScaling.forRequest(r,new Catalog())).getMessage().contains("explicit school"));
        r.targets.clear(); r.targets.add(target(StatType.ENERGY,School.UNIVERSAL,100));
        assertTrue(assertThrows(IllegalArgumentException.class,() -> StatScaling.forRequest(r,new Catalog())).getMessage().contains("unavailable"));
        r.targets.clear(); Gear wrong=OptimizerTest.gear("wrong",GearSlot.HAT);
        wrong.source="wizard101-wad"; wrong.gameVersion="exe-sha256:different";
        assertTrue(assertThrows(IllegalArgumentException.class,() -> StatScaling.forRequest(r,OptimizerTest.catalog(wrong))).getMessage().contains("revisions differ"));
    }
    @Test void requestProfilesResultJsonAndCliPreserveBothLevelsAndShowScaledTotals() throws Exception {
        Gear g=OptimizerTest.gear("180-ring",GearSlot.RING); g.level=180; g.baseStats.put(StatType.PIERCE,School.DEATH,55);
        Catalog c=OptimizerTest.catalog(g); var r=request(GearSlot.RING); r.targets.add(target(StatType.PIERCE,School.DEATH,54));
        Path requestFile=temp.resolve("request.json"),catalogFile=temp.resolve("catalog.json"),output=temp.resolve("result.json");
        JsonIO.write(requestFile,r); JsonIO.write(catalogFile,c);
        assertEquals(170,JsonIO.readRequest(requestFile).wizard.scaledLevel);
        try (var db=new SqliteItemRepository(temp.resolve("profile.sqlite"))) {
            db.saveProfile("raid",r); assertEquals(180,db.loadProfile("raid").wizard.level);
            assertEquals(170,db.loadProfile("raid").wizard.scaledLevel);
        }
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        assertEquals(0,Main.run(new String[]{"optimize","--request",requestFile.toString(),"--catalog",catalogFile.toString(),"--output",output.toString()},new PrintStream(stdout),new PrintStream(stderr)));
        assertTrue(stdout.toString().contains("targets use POST-SCALING stats"));
        assertTrue(stdout.toString().contains("Post-scaling character totals used for targets"));
        var saved=JsonIO.read(output,OptimizationResult.class);
        assertEquals(170,saved.scaling().scaledLevel()); assertTrue(saved.solutions().getFirst().fitness().feasible());
    }
    @Test void interactiveRequestAcceptsCharacterAndCombatLevels() throws Exception {
        InputStream prior=System.in; ByteArrayOutputStream out=new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream("STORM\n180\n170\nyes\n\nPIERCE DEATH MINIMUM 54 true 1 54\n\n\n\n\nno\n7\n".getBytes()));
            Path output=temp.resolve("interactive.json");
            assertEquals(0,Main.run(new String[]{"init","--output",output.toString()},new PrintStream(out),new PrintStream(out)),out.toString());
            var saved=JsonIO.readRequest(output); assertEquals(180,saved.wizard.level); assertEquals(170,saved.wizard.scaledLevel);
        } finally { System.setIn(prior); }
    }
}
