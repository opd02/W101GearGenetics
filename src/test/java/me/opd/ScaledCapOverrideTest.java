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

class ScaledCapOverrideTest {
    @TempDir Path temp;
    private OptimizationRequest request() {
        var r=OptimizerTest.request(GearSlot.RING); r.wizard.school=School.DEATH;
        r.wizard.level=180; r.wizard.scaledLevel=170; return r;
    }
    private void overrides(OptimizationRequest r) {
        r.wizard.scaledCapOverrides=new StatBlock().put(StatType.DAMAGE,School.DEATH,235).put(StatType.DAMAGE,School.ICE,243);
    }
    @Test void omittedAndEmptyOverridesKeepExactExistingArithmeticAndMetadata() {
        var r=request(); var raw=new StatBlock().put(StatType.DAMAGE,School.DEATH,1000).put(StatType.DAMAGE,School.ICE,200);
        var original=StatScaling.forRequest(r,new Catalog());
        assertEquals(213,original.effective(raw,StatType.DAMAGE,School.DEATH));
        assertEquals(200.0/224*221,original.effective(raw,StatType.DAMAGE,School.ICE));
        assertFalse(JsonIO.encode(r).contains("scaledCapOverrides"));
        assertFalse(JsonIO.encode(original.info()).contains("capOverrides"));
        assertEquals(Map.of(),JsonIO.decode(JsonIO.encode(original.info()),StatScaling.Info.class).capOverrides());
        assertEquals(original.info(),new StatScaling.Info(180,170,original.info().tableVersion(),original.info().model(),original.info().source()));
        r.wizard.scaledCapOverrides=new StatBlock();
        var empty=StatScaling.forRequest(r,new Catalog());
        assertEquals(original.info(),empty.info());
        assertEquals(original.project(raw).values,empty.project(raw).values);
        r.wizard.scaledLevel=null; assertNull(StatScaling.forRequest(r,new Catalog()));
    }
    @Test void userCeilingsRetainRatiosAggregateUniversalAndDoNotMutateBundledCaps() {
        var r=request(); overrides(r); r.validate();
        var scaling=StatScaling.forRequest(r,new Catalog());
        var raw=new StatBlock().put(StatType.DAMAGE,School.DEATH,220).put(StatType.DAMAGE,School.ICE,224)
            .put(StatType.DAMAGE,School.UNIVERSAL,10).put(StatType.PIERCE,School.DEATH,60);
        assertEquals(230.0/216*213,scaling.effective(raw,StatType.DAMAGE,School.DEATH));
        assertEquals(234.0/224*221,scaling.effective(raw,StatType.DAMAGE,School.ICE));
        assertEquals(60.0/63*56,scaling.effective(raw,StatType.PIERCE,School.DEATH));
        raw.put(StatType.DAMAGE,School.DEATH,1000).put(StatType.DAMAGE,School.ICE,1000);
        assertEquals(235,scaling.effective(raw,StatType.DAMAGE,School.DEATH));
        assertEquals(243,scaling.project(raw).effective(StatType.DAMAGE,School.ICE));
        assertEquals(Map.of("DAMAGE:DEATH",235.0,"DAMAGE:ICE",243.0),scaling.info().capOverrides());
        assertEquals("PROPORTIONAL_WITH_USER_CAPS_UNROUNDED",scaling.info().model());
        r.wizard.scaledCapOverrides.values.put("DAMAGE:DEATH",300.0);
        assertEquals(235,scaling.effective(raw,StatType.DAMAGE,School.DEATH));
        assertEquals(213,StatScaling.forRequest(request(),new Catalog()).effective(raw,StatType.DAMAGE,School.DEATH));
        assertEquals(1000,raw.raw(StatType.DAMAGE,School.DEATH));
    }
    @Test void gaExactAndSocketRefinementEnforceUserDamageMinimumsAboveOldSaturation() {
        Gear ring=OptimizerTest.gear("ring",GearSlot.RING); ring.level=180;
        ring.baseStats.put(StatType.DAMAGE,School.DEATH,220).put(StatType.DAMAGE,School.ICE,250)
            .put(StatType.PIERCE,School.DEATH,60).put(StatType.PIERCE,School.ICE,60)
            .put(StatType.ACCURACY,School.DEATH,25).put(StatType.ACCURACY,School.ICE,25)
            .put(StatType.POWER_PIP_CHANCE,School.UNIVERSAL,100);
        ring.sockets.add(new Socket(JewelType.CIRCLE));
        var damage=OptimizerTest.jewel("damage",JewelType.CIRCLE); damage.statBonus.put(StatType.DAMAGE,School.DEATH,40);
        var health=OptimizerTest.jewel("health",JewelType.CIRCLE); health.statBonus.put(StatType.HEALTH,School.UNIVERSAL,1000);
        var c=OptimizerTest.catalog(ring); c.jewels.addAll(List.of(damage,health)); var r=request();
        r.targets.add(OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MINIMUM,235,true));
        r.targets.add(OptimizerTest.target(StatType.DAMAGE,School.ICE,StatTarget.Mode.MINIMUM,243,true));
        r.targets.add(OptimizerTest.target(StatType.PIERCE,School.DEATH,StatTarget.Mode.MINIMUM,45,true));
        r.targets.add(OptimizerTest.target(StatType.PIERCE,School.ICE,StatTarget.Mode.MINIMUM,45,true));
        r.targets.add(OptimizerTest.target(StatType.ACCURACY,School.DEATH,StatTarget.Mode.MINIMUM,15,true));
        r.targets.add(OptimizerTest.target(StatType.ACCURACY,School.ICE,StatTarget.Mode.MINIMUM,20,true));
        r.targets.add(OptimizerTest.target(StatType.POWER_PIP_CHANCE,School.UNIVERSAL,StatTarget.Mode.MINIMUM,100,true));
        r.targets.add(OptimizerTest.target(StatType.HEALTH,School.UNIVERSAL,StatTarget.Mode.MAXIMIZE,0,false));
        r.targets.getLast().scale=1000;
        assertFalse(new Optimizer().optimize(c,r).solutions().getFirst().fitness().feasible());
        overrides(r); var ga=new Optimizer().optimize(c,r);
        var best=ga.solutions().getFirst(); assertTrue(best.fitness().feasible());
        assertEquals("damage",best.equipment().jewels.get(GearSlot.RING).getFirst().id);
        assertEquals(235,best.targetStats().effective(StatType.DAMAGE,School.DEATH));
        assertEquals(243,best.targetStats().effective(StatType.DAMAGE,School.ICE));
        assertEquals(260,best.rawCharacterStats().effective(StatType.DAMAGE,School.DEATH));
        assertTrue(ga.warnings().stream().anyMatch(s -> s.contains("User-supplied post-scaling caps") && s.contains("ratios are retained")));
        r.search.method=GAConfig.Method.EXACT;
        assertEquals(best.fitness(),new Optimizer().optimize(c,r).solutions().getFirst().fitness());
        r.lockedJewels.put("RING:0","health");
        assertFalse(new Optimizer().optimize(c,r).solutions().getFirst().fitness().feasible());
    }
    @Test void invalidUnsupportedAndZeroRatioOverridesFailWithoutBypassingRevisionChecks() {
        var r=request(); overrides(r); r.wizard.scaledLevel=null;
        assertThrows(IllegalArgumentException.class,r::validate);
        r.wizard.scaledLevel=170;
        for (double value : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            r.wizard.scaledCapOverrides.values.put("DAMAGE:DEATH",value);
            assertThrows(IllegalArgumentException.class,r::validate);
        }
        for (var block : List.of(new StatBlock().put(StatType.DAMAGE,School.UNIVERSAL,235),
                new StatBlock().put(StatType.ENERGY,School.UNIVERSAL,100),
                new StatBlock().put(StatType.FLAT_RESISTANCE,School.DEATH,10))) {
            r.wizard.scaledCapOverrides=block;
            assertThrows(IllegalArgumentException.class,() -> new SearchSpace(OptimizerTest.catalog(OptimizerTest.gear("ring",GearSlot.RING)),r));
        }
        overrides(r); Gear wrong=OptimizerTest.gear("wrong",GearSlot.RING);
        wrong.source="wizard101-wad"; wrong.gameVersion="exe-sha256:different";
        assertTrue(assertThrows(IllegalArgumentException.class,() -> StatScaling.forRequest(r,OptimizerTest.catalog(wrong))).getMessage().contains("revisions differ"));
    }
    @Test void lowerAndSameBandCeilingsAreScopedToNamedStats() {
        var r=request(); r.wizard.scaledLevel=180;
        r.wizard.scaledCapOverrides=new StatBlock().put(StatType.DAMAGE,School.DEATH,150);
        var raw=new StatBlock().put(StatType.DAMAGE,School.DEATH,200).put(StatType.DAMAGE,School.ICE,200);
        var scaling=StatScaling.forRequest(r,new Catalog());
        assertEquals(150,scaling.effective(raw,StatType.DAMAGE,School.DEATH));
        assertEquals(200,scaling.effective(raw,StatType.DAMAGE,School.ICE));
    }
    @Test void jsonSqliteSavedRequestsAndCliResultsPreserveExplicitCalibration() throws Exception {
        Gear ring=OptimizerTest.gear("ring",GearSlot.RING); ring.level=180;
        ring.baseStats.put(StatType.DAMAGE,School.DEATH,260).put(StatType.DAMAGE,School.ICE,270);
        var c=OptimizerTest.catalog(ring); var r=request(); overrides(r);
        r.targets.add(OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MINIMUM,235,true));
        Path input=temp.resolve("request.json"),catalog=temp.resolve("catalog.json"),saved=temp.resolve("saved.json"),output=temp.resolve("result.json");
        JsonIO.write(input,r); JsonIO.write(catalog,c);
        assertEquals(r.wizard.scaledCapOverrides.values,JsonIO.readRequest(input).wizard.scaledCapOverrides.values);
        try (var db=new SqliteItemRepository(temp.resolve("profiles.sqlite"))) {
            db.saveProfile("death-ice",r);
            assertEquals(r.wizard.scaledCapOverrides.values,db.loadProfile("death-ice").wizard.scaledCapOverrides.values);
        }
        assertEquals(0,Main.run(new String[]{"optimize","--request",input.toString(),"--catalog",catalog.toString(),"--output",output.toString(),"--save-request",saved.toString()},
            new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
        assertEquals(r.wizard.scaledCapOverrides.values,JsonIO.readRequest(saved).wizard.scaledCapOverrides.values);
        var result=JsonIO.read(output,OptimizationResult.class);
        assertEquals(r.wizard.scaledCapOverrides.values,result.scaling().capOverrides());
        assertEquals(235,result.solutions().getFirst().targetStats().effective(StatType.DAMAGE,School.DEATH));
    }
}
