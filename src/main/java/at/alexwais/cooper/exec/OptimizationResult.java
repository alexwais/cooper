package at.alexwais.cooper.exec;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OptimizationResult {

    private Map<String, Boolean> vmAllocation;

}
