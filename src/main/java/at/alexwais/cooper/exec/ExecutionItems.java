package at.alexwais.cooper.exec;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecutionItems {

    private List<String> vmsToLaunch;
    private List<String> vmsToTerminate;

    private Map<String, String> containersToStart; // containerId, vmId
    private List<String> containersToStop;

}
