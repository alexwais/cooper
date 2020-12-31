package at.alexwais.cooper.api;

import at.alexwais.cooper.scheduler.dto.MonitoringResult;
import at.alexwais.cooper.simulated.EndOfScenarioException;


public interface MonitoringController {

    MonitoringResult getCurrentLoad(int elapsedSeconds) throws EndOfScenarioException;

}
