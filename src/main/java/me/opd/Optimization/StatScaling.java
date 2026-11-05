package me.opd.Optimization;

import me.opd.DataReading.*;
import me.opd.Storage.JsonIO;
import java.util.*;

/** Versioned proportional cap model. Raid rounding and floor-field semantics remain provisional. */
public final class StatScaling {
    public record Info(int characterLevel,int scaledLevel,String tableVersion,String model,Map<String,String> source) {}
    private record Row(School school,int level,Map<String,Double> caps) {}
    private record Table(int schemaVersion,String version,Map<String,String> source,Map<String,Double> floorPercent,List<Row> rows) {}
    private static final class Tables {
        static final Table DATA=load();
        static Table load() {
            try (var input=StatScaling.class.getResourceAsStream("/me/opd/scaling/retail-20261031.json")) {
                if (input==null) throw new IllegalStateException("Bundled level-scaling table missing");
                Table table=JsonIO.MAPPER.readValue(input,Table.class);
                if (table.schemaVersion()!=1 || table.rows().isEmpty()) throw new IllegalStateException("Invalid scaling table version");
                Set<String> identities=new HashSet<>();
                for (Row row : table.rows()) {
                    if (!identities.add(row.school()+":"+row.level())) throw new IllegalStateException("Duplicate scaling band");
                    StatBlock caps=new StatBlock(); caps.values.putAll(row.caps()); caps.validate();
                    if (row.caps().values().stream().anyMatch(v -> v<0)) throw new IllegalStateException("Negative scaling cap");
                }
                return table;
            } catch (Exception e) { throw new IllegalStateException("Cannot load level-scaling table",e); }
        }
    }
    private final Map<String,Double> sourceCaps,targetCaps;
    private final Info info;
    private StatScaling(WizardProfile wizard) {
        Table table=Tables.DATA;
        sourceCaps=find(table,wizard.school,wizard.level);
        targetCaps=find(table,wizard.school,wizard.scaledLevel);
        info=new Info(wizard.level,wizard.scaledLevel,table.version(),"CAPPED_PROPORTIONAL_UNROUNDED",Map.copyOf(table.source()));
    }
    public static StatScaling forRequest(OptimizationRequest request,Catalog catalog) {
        if (request.wizard.scaledLevel==null) return null;
        StatScaling scaling=new StatScaling(request.wizard);
        for (StatTarget target : request.targets) if (target.mode!=StatTarget.Mode.IGNORE) scaling.requireSupported(target.stat,target.school);
        String expected="exe-sha256:"+scaling.info.source().get("executableSha256");
        if (catalog.gear.stream().anyMatch(g -> g.source.startsWith("wizard101-wad") && !expected.equals(g.gameVersion))
                || catalog.jewels.stream().anyMatch(j -> j.source.startsWith("wizard101-wad") && !expected.equals(j.gameVersion)))
            throw new IllegalArgumentException("Scaling table and WAD catalog client revisions differ; use matching versioned data");
        return scaling;
    }
    private static Map<String,Double> find(Table table,School school,int level) {
        int band=level/10*10;
        return table.rows().stream().filter(r -> r.school()==school && r.level()==band).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No level-scaling caps for "+school+" at level "+level+" (band "+band+"); bundled bands are 10..180"))
            .caps();
    }
    private void requireSupported(StatType stat,School school) {
        if (stat.schoolSpecific && school==School.UNIVERSAL)
            throw new IllegalArgumentException("Scaled "+stat+" targets need an explicit school; universal bonuses are included separately in every school's scaled total");
        String key=StatBlock.key(stat,school);
        if (!sourceCaps.containsKey(key) || !targetCaps.containsKey(key))
            throw new IllegalArgumentException("Level-scaling caps are unavailable for target "+key);
    }
    public Info info() { return info; }
    public double effective(StatBlock raw,StatType stat,School school) {
        String key=StatBlock.key(stat,school);
        Double source=sourceCaps.get(key),target=targetCaps.get(key);
        if (source==null || target==null) throw new IllegalArgumentException("Unsupported scaled stat "+key);
        double value=raw.effective(stat,school);
        if (source==0 || target==0) return 0;
        // Clamp before multiplying. Do not round before mandatory comparisons.
        return Math.min(value/source,1.0)*target;
    }
    /** Full effective school totals, rather than a synthetic universal/specific decomposition. */
    public StatBlock project(StatBlock raw) {
        StatBlock projected=new StatBlock();
        for (String key : targetCaps.keySet()) {
            String[] parts=key.split(":");
            projected.values.put(key,effective(raw,StatType.valueOf(parts[0]),School.valueOf(parts[1])));
        }
        projected.validate(); return projected;
    }
}
