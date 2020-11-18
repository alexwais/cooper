package at.alexwais.cooper;

import at.alexwais.cooper.config.DataCenterConfigMap;
import at.alexwais.cooper.config.DataCenterDistanceConfigList;
import at.alexwais.cooper.config.ServiceConfigMap;
import at.alexwais.cooper.scheduler.Initializer;
import at.alexwais.cooper.scheduler.Model;
import at.alexwais.cooper.scheduler.SchedulingCycle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

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

	@Autowired
	private SchedulingCycle scheduler;
	@Value("${scenario}")
	private String scenario;


	@Override
	public void run(String... args) {
		log.info("");
		log.info("EXECUTING COOPER with scenario: {}", scenario);

		scheduler.run();
	}

	@Bean
	public Model model() {
		var initializer = new Initializer(dataCenterConfig, distanceConfig, serviceConfig);
		return new Model(initializer.getDataCenters(), initializer.getServices(), initializer.getInteractionMultiplication(), initializer.getDataCenterDistanceGraph());
	}

}
