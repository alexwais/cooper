package at.ac.tuwien.dsg.cooper.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapUtils {

    public static <K, T> void putToMapList(Map<K, List<T>> map, K key, T item) {
        var listForKey = map.get(key);
        if (listForKey != null) {
            listForKey.add(item);
        } else {
            map.put(key, new ArrayList<>(List.of(item)));
        }
    }

    public static <K, T> void removeFromMapList(Map<K, List<T>> map, K key, T item) {
        var listForKey = map.get(key);
        if (listForKey != null) {
            listForKey.remove(item);
        } else {
            throw new IllegalStateException("Cannot remove not existing item "
                    + item + " from MapList with key: " + key);
        }
    }

}
