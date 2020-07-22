package at.alexwais.cooper.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class DataCenter {

    private final String name;

    private List<InstanceType> instanceTypes = new ArrayList<>();

    private Map<String, List<Instance>> instances = new HashMap<>();

}
