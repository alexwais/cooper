package at.alexwais.cooper.stuff.cloudsim;

import at.alexwais.cooper.csp.Listener;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SampleListener implements Listener {

    private CloudSimRunner runner;

    @Override
    public void cycleElapsed(long clock, at.alexwais.cooper.csp.Scheduler scheduler) {
        System.out.println(getClockInterval() + " seconds elapsed: " + runner.getClock());

        if (clock == 30L) scheduler.launchVm("1.micro", "DC-1");
        if (clock == 60L) scheduler.launchVm("4.xlarge", "DC-1");
        if (clock == 60L) scheduler.launchContainer(2, 1024, 1);
        if (clock == 80L) scheduler.launchContainer(2, 1024, 1);
        if (clock == 100L) scheduler.launchContainer(2, 1600, 1);
        if (clock == 100L) scheduler.launchContainer(2, 1600, 0);
        if (clock == 110L) scheduler.terminateContainer(1);
        if (clock == 130L) scheduler.terminateContainer(2);
        if (clock == 150L) scheduler.terminateVm(0);
        if (clock == 200L) scheduler.abort();

        runner.printVmList();
//            new CloudletsTableBuilder(new ArrayList<>(cloudletList.values())).build();
    }

    @Override
    public int getClockInterval() {
        return 10;
    }
}