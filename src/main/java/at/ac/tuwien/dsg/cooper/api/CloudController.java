package at.ac.tuwien.dsg.cooper.api;

public interface CloudController {

    long launchVm(String type, String dataCenter);

    void terminateVm(long id);

    long launchContainer(int cpuCores, long memory, long vmId);

    void terminateContainer(long id);

    void abort();

}
