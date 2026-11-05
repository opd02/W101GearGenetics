package me.opd.Storage;
import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import me.opd.Optimization.*;
import me.opd.PetHandeling.PetTrait;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
/** Catalog payloads evolve independently of indexes. Profiles and ownership never get replaced by imports. */
public class SqliteItemRepository implements ItemRepository {
    private final Connection connection;
    public SqliteItemRepository(Path path) {
        try {
            connection=DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            try (Statement s=connection.createStatement()) {
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("CREATE TABLE IF NOT EXISTS schema_info(version INTEGER NOT NULL)");
                s.execute("INSERT INTO schema_info SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM schema_info)");
                try (ResultSet rs=s.executeQuery("SELECT version FROM schema_info")) {
                    if (!rs.next() || rs.getInt(1)!=1) throw new IllegalArgumentException("Unsupported SQLite schema version");
                }
                s.execute("CREATE TABLE IF NOT EXISTS items(id TEXT PRIMARY KEY,kind TEXT NOT NULL,slot TEXT,level INTEGER NOT NULL,complete INTEGER NOT NULL,payload TEXT NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS slot_level ON items(kind,slot,level)");
                s.execute("CREATE TABLE IF NOT EXISTS set_rules(id TEXT NOT NULL,pieces INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(id,pieces))");
                s.execute("CREATE TABLE IF NOT EXISTS base_stats(key TEXT PRIMARY KEY,payload TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS catalog_extensions(key TEXT PRIMARY KEY,payload TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS pet_talents(key TEXT PRIMARY KEY,payload TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS profiles(name TEXT PRIMARY KEY,payload TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS ownership(profile TEXT NOT NULL,item_id TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>=0),PRIMARY KEY(profile,item_id))");
            }
        } catch (SQLException e) { throw failure(e); }
    }
    private static IllegalStateException failure(SQLException e) { return new IllegalStateException("SQLite: " + e.getMessage(),e); }
    @Override public void importCatalog(Catalog incoming) {
        incoming.validate();
        try {
            connection.setAutoCommit(false);
            try {
                // Validate the merged catalog before committing (including set references).
                for (Gear item : incoming.gear) upsert(item.id,"gear",item.slot.name(),item.level,item.complete,importPayload(item));
                for (Jewel item : incoming.jewels) upsert(item.id,"jewel",null,item.level,item.complete,importPayload(item));
                for (SetBonus rule : incoming.setBonuses) {
                    try (PreparedStatement p=connection.prepareStatement("INSERT INTO set_rules VALUES(?,?,?) ON CONFLICT(id,pieces) DO UPDATE SET payload=excluded.payload")) {
                        p.setString(1,rule.setId); p.setInt(2,rule.pieces); p.setString(3,JsonIO.encode(rule)); p.executeUpdate();
                    }
                }
                for (var entry : incoming.baseStats.entrySet()) putPayload("base_stats",entry.getKey(),JsonIO.encode(entry.getValue()));
                for (var entry : incoming.extensions.entrySet()) putPayload("catalog_extensions",entry.getKey(),JsonIO.encode(entry.getValue()));
                for (PetTrait talent : incoming.petTalents) putPayload("pet_talents",talent.id,JsonIO.encode(talent));
                snapshot().validate();
                connection.commit();
            } catch (RuntimeException | SQLException e) { connection.rollback(); throw e; }
            finally { connection.setAutoCommit(true); }
        } catch (SQLException e) { throw failure(e); }
    }
    private static String importPayload(Object item) {
        var tree=(com.fasterxml.jackson.databind.node.ObjectNode)JsonIO.MAPPER.valueToTree(item);
        if (!tree.hasNonNull("importedAt") || tree.path("importedAt").asText().equals("unknown"))
            tree.put("importedAt",java.time.Instant.now().toString());
        return JsonIO.encode(tree);
    }
    private void upsert(String id,String kind,String slot,int level,boolean complete,String json) throws SQLException {
        try (PreparedStatement check=connection.prepareStatement("SELECT kind,complete,payload FROM items WHERE id=?")) {
            check.setString(1,id);
            try (ResultSet rs=check.executeQuery()) {
                if (rs.next()) {
                    if (!kind.equals(rs.getString(1))) throw new IllegalArgumentException("ID changes equipment kind: " + id);
                    if (rs.getBoolean(2) && !complete) throw new IllegalArgumentException("Incomplete import would overwrite complete item " + id);
                    // Extensions include unresolved data and must not be dropped by a later import.
                    var oldTree=JsonIO.MAPPER.readTree(rs.getString(3));
                    var newTree=JsonIO.MAPPER.readTree(json);
                    var merged=(com.fasterxml.jackson.databind.node.ObjectNode)newTree.path("extensions");
                    oldTree.path("extensions").fields().forEachRemaining(e -> { if (!merged.has(e.getKey())) merged.set(e.getKey(),e.getValue()); });
                    json=JsonIO.MAPPER.writeValueAsString(newTree);
                }
            } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid item JSON",e); }
        }
        try (PreparedStatement p=connection.prepareStatement("INSERT INTO items VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET slot=excluded.slot,level=excluded.level,complete=excluded.complete,payload=excluded.payload")) {
            p.setString(1,id); p.setString(2,kind); p.setString(3,slot); p.setInt(4,level); p.setBoolean(5,complete); p.setString(6,json); p.executeUpdate();
        }
    }
    private void putPayload(String table,String key,String json) throws SQLException {
        // table names are internal constants, never user input
        try (PreparedStatement p=connection.prepareStatement("INSERT INTO " + table + " VALUES(?,?) ON CONFLICT(key) DO UPDATE SET payload=excluded.payload")) {
            p.setString(1,key); p.setString(2,json); p.executeUpdate();
        }
    }
    @Override public Catalog snapshot() {
        Catalog catalog=new Catalog();
        try (Statement s=connection.createStatement()) {
            try (ResultSet rs=s.executeQuery("SELECT kind,payload FROM items ORDER BY id")) {
                while (rs.next()) if (rs.getString(1).equals("gear")) catalog.gear.add(JsonIO.decode(rs.getString(2),Gear.class));
                else catalog.jewels.add(JsonIO.decode(rs.getString(2),Jewel.class));
            }
            try (ResultSet rs=s.executeQuery("SELECT payload FROM set_rules ORDER BY id,pieces")) { while (rs.next()) catalog.setBonuses.add(JsonIO.decode(rs.getString(1),SetBonus.class)); }
            try (ResultSet rs=s.executeQuery("SELECT key,payload FROM base_stats ORDER BY key")) { while (rs.next()) catalog.baseStats.put(rs.getString(1),JsonIO.decode(rs.getString(2),StatBlock.class)); }
            try (ResultSet rs=s.executeQuery("SELECT key,payload FROM catalog_extensions ORDER BY key")) { while (rs.next()) catalog.extensions.put(rs.getString(1),JsonIO.decode(rs.getString(2),Object.class)); }
            try (ResultSet rs=s.executeQuery("SELECT payload FROM pet_talents ORDER BY key")) { while (rs.next()) catalog.petTalents.add(JsonIO.decode(rs.getString(1),PetTrait.class)); }
            return catalog;
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public List<Gear> query(Query query) {
        List<Gear> items=new ArrayList<>();
        try (PreparedStatement p=connection.prepareStatement("SELECT payload FROM items WHERE kind='gear' AND slot=? AND level<=? ORDER BY id")) {
            p.setString(1,query.slot().name()); p.setInt(2,query.level());
            try (ResultSet rs=p.executeQuery()) {
                while (rs.next()) {
                    Gear item=JsonIO.decode(rs.getString(1),Gear.class);
                    if (item.eligible(query.school(),query.level()) && (query.stat()==null
                            || item.baseStats.effective(query.stat(),query.statSchool())>=query.minimum())) items.add(item);
                }
            }
            return items;
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void saveProfile(String name,OptimizationRequest request) {
        request.validate();
        if (name==null || name.isBlank()) throw new IllegalArgumentException("Profile name required");
        try (PreparedStatement p=connection.prepareStatement("INSERT INTO profiles VALUES(?,?) ON CONFLICT(name) DO UPDATE SET payload=excluded.payload")) {
            p.setString(1,name); p.setString(2,JsonIO.encode(request)); p.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public OptimizationRequest loadProfile(String name) {
        try (PreparedStatement p=connection.prepareStatement("SELECT payload FROM profiles WHERE name=?")) {
            p.setString(1,name);
            try (ResultSet rs=p.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Unknown saved profile: " + name);
                OptimizationRequest request=JsonIO.decode(rs.getString(1),OptimizationRequest.class); request.validate(); return request;
            }
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void setOwnedQuantity(String profile,String id,int quantity) {
        if (profile==null || profile.isBlank() || id==null || id.isBlank() || quantity<0) throw new IllegalArgumentException("Invalid inventory entry");
        try (PreparedStatement p=connection.prepareStatement("INSERT INTO ownership VALUES(?,?,?) ON CONFLICT(profile,item_id) DO UPDATE SET quantity=excluded.quantity")) {
            p.setString(1,profile); p.setString(2,id); p.setInt(3,quantity); p.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public Map<String,Integer> ownership(String profile) {
        Map<String,Integer> result=new TreeMap<>();
        try (PreparedStatement p=connection.prepareStatement("SELECT item_id,quantity FROM ownership WHERE profile=? ORDER BY item_id")) {
            p.setString(1,profile);
            try (ResultSet rs=p.executeQuery()) { while (rs.next()) result.put(rs.getString(1),rs.getInt(2)); }
            return result;
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void close() { try { connection.close(); } catch (SQLException e) { throw failure(e); } }
}
