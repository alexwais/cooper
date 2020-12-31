package at.alexwais.cooper.csp;

import at.alexwais.cooper.api.CloudController;

public interface Listener {

    void cycleElapsed(long clock, CloudController cloudController);

    int getClockInterval(); // seconds

}
