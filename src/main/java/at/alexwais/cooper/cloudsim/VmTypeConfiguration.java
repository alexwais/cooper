package at.alexwais.cooper.cloudsim;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

public class VmTypeConfiguration {

    @Getter
    private final Map<String, VmType> vmTypes;

    public VmTypeConfiguration() {
        var list = List.of(
                new VmType("1.nano", 1, 512),
                new VmType("1.micro", 1, 1024),
                new VmType("1.small", 1, 2048),
                new VmType("2.micro", 2, 1024),
                new VmType("2.small", 2, 2048),
                new VmType("2.medium", 2, 4096),
                new VmType("2.large", 2, 8192),
                new VmType("4.small", 4, 2048),
                new VmType("4.medium", 4, 4096),
                new VmType("4.large", 4, 8192),
                new VmType("4.xlarge", 4, 16384),
                new VmType("8.medium", 8, 4096),
                new VmType("8.large", 8, 8192),
                new VmType("8.xlarge", 8, 16384),
                new VmType("8.xxlarge", 8, 32768)
        );
        vmTypes = list.stream().collect(Collectors.toMap(VmType::getName, Function.identity()));
    }

}
