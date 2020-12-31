package at.ac.tuwien.dsg.cooper.csp;

import at.ac.tuwien.dsg.cooper.api.CloudController;

public interface Listener {

    void cycleElapsed(long clock, CloudController cloudController);

    int getClockInterval(); // seconds

}
