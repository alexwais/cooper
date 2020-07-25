package at.alexwais.cooper.exec;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecutionItems {

    private List<String> vmsToLaunch;
    private List<String> vmsToTerminate;

}
