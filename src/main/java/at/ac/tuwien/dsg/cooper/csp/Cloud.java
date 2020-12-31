package at.ac.tuwien.dsg.cooper.csp;

public interface Cloud {

    void registerListener(Listener listener);

    void run();

}
