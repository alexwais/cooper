package at.alexwais.cooper;

import at.alexwais.cooper.config.DataCenterConfigMap;
import at.alexwais.cooper.config.DataCenterDistanceConfigList;
import at.alexwais.cooper.config.ServiceConfigMap;
import at.alexwais.cooper.scheduler.Initializer;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.SchedulingLoop;
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
	private DataCenterConfigMap dataCenterConfig;
	@Autowired
	private DataCenterDistanceConfigList distanceConfig;
	@Autowired
	private ServiceConfigMap serviceConfig;


	@Override
	public void run(String... args) {
		log.info("EXECUTING : cooper");

		var initializer = new Initializer(dataCenterConfig, distanceConfig, serviceConfig);
//		initializer.printState();
		var model = new Model(initializer.getDataCenters(), initializer.getServices(), initializer.getInteractionMultiplication(), initializer.getDataCenterDistanceGraph());
		var execution = new SchedulingLoop(model);
		execution.run();


//		var cloudSimProvider = new CloudSimRunner();
//		cloudSimProvider.registerListener(new SampleListener(cloudSimProvider));
//		cloudSimProvider.run();

//		SimPlus.run();
	}





}
