package at.alexwais.cooper.csp;

public interface Listener {

    void cycleElapsed(long clock, Scheduler scheduler);

    int getClockInterval(); // seconds

}
