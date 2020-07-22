package at.alexwais.cooper;

import at.alexwais.cooper.cloudsim.CloudSimRunner;
import at.alexwais.cooper.csp.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class CooperApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(CooperApplication.class, args);
	}


	@Autowired
	private DataCenterConfigMap configMap;

	private static final int INSTANCE_COUNT = 2_000;

	@Override
	public void run(String... args) {
		log.info("EXECUTING : cooper");

		var service = new SchedulerService(configMap);
		service.printState();


		Provider cloudSimCsp = new CloudSimRunner();
		cloudSimCsp.run();

//		SimPlus.run();
	}





}
