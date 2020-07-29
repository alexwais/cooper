package at.alexwais.cooper.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DataCenter {

    private final String name;

    private final List<InstanceType> vmTypes = new ArrayList<>();

    private final List<Instance> vmInstances = new ArrayList<>();

    private final Map<String, List<Instance>> vmsByType = new HashMap<>();

}
