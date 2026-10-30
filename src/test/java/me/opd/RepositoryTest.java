package me.opd;
import me.opd.DataReading.*;
import me.opd.Storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class RepositoryTest {
    @TempDir Path temp;
    @Test void jsonSqliteUpsertQueryProfilesOwnershipAndExport() throws Exception {
        Catalog c=JsonIO.readCatalog(Path.of("examples/catalog.json"));
        var request=JsonIO.readRequest(Path.of("examples/offschool-request.json"));
        Path db=temp.resolve("items.sqlite");
        try (var repo=new SqliteItemRepository(db)) {
            repo.importCatalog(c); repo.importCatalog(c);
            assertEquals(c.gear.size(),repo.snapshot().gear.size());
            repo.saveProfile("raid",request); repo.setOwnedQuantity("raid",c.gear.getFirst().id,2);
            c.gear.getFirst().name="Updated";
            repo.importCatalog(c);
            assertEquals(School.DEATH,repo.loadProfile("raid").wizard.school);
            assertEquals(2,repo.ownership("raid").get(c.gear.getFirst().id));
            var found=repo.query(new ItemRepository.Query(GearSlot.HAT,School.DEATH,170,StatType.DAMAGE,School.STORM,60));
            assertEquals(2,found.size()); assertTrue(found.stream().anyMatch(g -> g.name.equals("Updated")));
            Path exported=temp.resolve("catalog.json"); JsonIO.write(exported,repo.snapshot());
            Catalog roundTrip=JsonIO.readCatalog(exported);
            assertEquals(JsonIO.encode(repo.snapshot()),JsonIO.encode(roundTrip));
        }
        try (var repo=new SqliteItemRepository(db)) { assertEquals(2,repo.ownership("raid").values().iterator().next()); }
    }
    @Test void incompleteImportCannotOverwriteAndTransactionRollsBack() throws Exception {
        Catalog c=JsonIO.readCatalog(Path.of("examples/catalog.json"));
        try (var repo=new SqliteItemRepository(temp.resolve("safe.sqlite"))) {
            repo.importCatalog(c);
            String oldName=repo.snapshot().gear.getFirst().name;
            c.gear.getFirst().name="Should not persist"; c.gear.getLast().complete=false;
            assertThrows(IllegalArgumentException.class,() -> repo.importCatalog(c));
            assertEquals(oldName,repo.snapshot().gear.getFirst().name);
        }
    }
    @Test void unknownDataPreservedUnknownTopLevelAndMalformedStatsRejected() throws Exception {
        Catalog c=JsonIO.readCatalog(Path.of("examples/catalog.json"));
        c.gear.getFirst().extensions.put("unmappedRating",123);
        try (var repo=new SqliteItemRepository(temp.resolve("unknown.sqlite"))) {
            repo.importCatalog(c); c.gear.getFirst().extensions.clear(); repo.importCatalog(c);
            assertEquals(123,repo.snapshot().gear.stream().filter(g -> g.id.equals(c.gear.getFirst().id)).findFirst().orElseThrow().extensions.get("unmappedRating"));
        }
        Path bad=temp.resolve("bad.json");
        Files.writeString(bad,JsonIO.encode(c).replace("\"schemaVersion\" : 1","\"schemaVersion\" : 1, \"typoField\":true"));
        assertThrows(Exception.class,() -> JsonIO.readCatalog(bad));
        c.gear.getFirst().baseStats.values.put("UNRECOGNIZED:STORM",1.0);
        assertThrows(IllegalArgumentException.class,c::validate);
        c.gear.getFirst().baseStats.values.remove("UNRECOGNIZED:STORM"); c.gear.add(c.gear.getFirst());
        assertThrows(IllegalArgumentException.class,c::validate);
    }
}
