package at.alexwais.cooper.stuff.cloudsim;

import at.alexwais.cooper.api.CloudController;
import at.alexwais.cooper.csp.Listener;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SampleListener implements Listener {

    private CloudSimRunner runner;

    @Override
    public void cycleElapsed(long clock, CloudController cloudController) {
        System.out.println(getClockInterval() + " seconds elapsed: " + runner.getClock());

        if (clock == 30L) cloudController.launchVm("1.micro", "DC-1");
        if (clock == 60L) cloudController.launchVm("4.xlarge", "DC-1");
        if (clock == 60L) cloudController.launchContainer(2, 1024, 1);
        if (clock == 80L) cloudController.launchContainer(2, 1024, 1);
        if (clock == 100L) cloudController.launchContainer(2, 1600, 1);
        if (clock == 100L) cloudController.launchContainer(2, 1600, 0);
        if (clock == 110L) cloudController.terminateContainer(1);
        if (clock == 130L) cloudController.terminateContainer(2);
        if (clock == 150L) cloudController.terminateVm(0);
        if (clock == 200L) cloudController.abort();

        runner.printVmList();
//            new CloudletsTableBuilder(new ArrayList<>(cloudletList.values())).build();
    }

    @Override
    public int getClockInterval() {
        return 10;
    }
}