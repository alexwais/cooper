package at.alexwais.cooper.config;

import java.util.List;
import lombok.Data;


@Data
public class DataCenterConfig {

    private boolean onPremise = false;

    private List<InstanceTypeConfig> instanceTypes;

}
