package at.alexwais.cooper.exec;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OptimizationInput {

    private Map<String, Boolean> runningVms;

}
