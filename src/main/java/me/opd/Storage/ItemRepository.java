package me.opd.Storage;
import me.opd.DataReading.*;
import me.opd.Optimization.OptimizationRequest;
import java.util.*;
public interface ItemRepository extends AutoCloseable {
    record Query(GearSlot slot,School school,int level,StatType stat,School statSchool,double minimum) {}
    Catalog snapshot();
    void importCatalog(Catalog catalog);
    List<Gear> query(Query query);
    void saveProfile(String name,OptimizationRequest request);
    OptimizationRequest loadProfile(String name);
    void setOwnedQuantity(String profile,String id,int quantity);
    Map<String,Integer> ownership(String profile);
    @Override void close();
}
