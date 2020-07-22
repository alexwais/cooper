package at.alexwais.cooper;

import java.util.List;
import lombok.Data;


@Data
public class DataCenterConfig {

    private List<InstanceTypeConfig> instances;

}
