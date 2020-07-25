package at.alexwais.cooper;

import at.alexwais.cooper.exec.CooperExecution;
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

//		var service = new SchedulerService(configMap);
//		service.printState();

		var execution = new CooperExecution();
		execution.run();


//		var cloudSimProvider = new CloudSimRunner();
//		cloudSimProvider.registerListener(new SampleListener(cloudSimProvider));
//		cloudSimProvider.run();

//		SimPlus.run();
	}





}
