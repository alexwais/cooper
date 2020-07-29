package at.alexwais.cooper.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class Service {

    private final String name;

    private List<ContainerConfiguration> containerConfigurations = new ArrayList<>();

    private List<ContainerInstance> containers = new ArrayList<>();

    private Map<String, List<ContainerInstance>> containersByConfiguration = new HashMap<>();

}
