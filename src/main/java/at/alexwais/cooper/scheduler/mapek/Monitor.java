package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.scheduler.dto.MonitoringResult;
import at.alexwais.cooper.scheduler.simulated.EndOfScenarioException;


public interface Monitor {

    MonitoringResult getCurrentLoad(int elapsedSeconds) throws EndOfScenarioException;

}
