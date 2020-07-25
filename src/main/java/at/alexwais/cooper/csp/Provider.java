package at.alexwais.cooper.csp;

public interface Provider {

    void registerListener(Listener listener);

    void run();

}
