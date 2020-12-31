package at.alexwais.cooper.csp;

public interface Cloud {

    void registerListener(Listener listener);

    void run();

}
