package at.alexwais.cooper.config;

import java.util.List;
import lombok.Data;


@Data
public class DataCenterConfig {

    private List<InstanceTypeConfig> instanceTypes;

}
