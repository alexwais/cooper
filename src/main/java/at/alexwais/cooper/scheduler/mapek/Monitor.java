package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.scheduler.dto.MonitoringResult;


public interface Monitor {

    MonitoringResult getCurrentLoad(int elapsedSeconds);

}
