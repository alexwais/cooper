package at.alexwais.cooper.exec;

import at.alexwais.cooper.domain.ContainerInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapUtils {

    public static <T> void putToMapList(Map<String, List<T>> map, String key, T item) {
        var listForKey = map.get(key);
        if (listForKey != null) {
            listForKey.add(item);
        } else {
            map.put(key, new ArrayList<>(List.of(item)));
        }
    }

    public static void removeContainerFromMapList(Map<String, List<ContainerInstance>> map, String key, ContainerInstance container) {
        var listForKey = map.get(key);
        if (listForKey != null) {
            listForKey.remove(container);
        } else {
            throw new IllegalStateException("Cannot remove not existing container of type "
                    + container.getConfiguration().getLabel() + " from MapList with key " + key);
        }
    }

}
