package at.ac.tuwien.dsg.cooper.api;

import at.ac.tuwien.dsg.cooper.scheduler.dto.MonitoringResult;
import at.ac.tuwien.dsg.cooper.simulated.EndOfScenarioException;


public interface MonitoringController {

    MonitoringResult getCurrentLoad(int elapsedSeconds) throws EndOfScenarioException;

}
