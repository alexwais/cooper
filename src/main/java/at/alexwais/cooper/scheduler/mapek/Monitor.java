package at.alexwais.cooper.scheduler.mapek;

import at.alexwais.cooper.scheduler.dto.LoadMeasures;


public interface Monitor {

    LoadMeasures getCurrentLoad(int elapsedSeconds);

}
