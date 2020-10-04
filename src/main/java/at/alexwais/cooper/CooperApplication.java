package at.alexwais.cooper;

import at.alexwais.cooper.config.DataCenterConfigMap;
import at.alexwais.cooper.config.ServiceConfigMap;
import at.alexwais.cooper.exec.CooperExecution;
import at.alexwais.cooper.exec.Initializer;
import at.alexwais.cooper.exec.Model;
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
	private ServiceConfigMap serviceConfig;


	@Override
	public void run(String... args) {
		log.info("EXECUTING : cooper");

		var initializer = new Initializer(dataCenterConfig, serviceConfig);
//		initializer.printState();
		var model = new Model(initializer.getDataCenters(), initializer.getServices(), initializer.getDownstreamRequestMultiplier());
		var execution = new CooperExecution(model);
		execution.run();


//		var cloudSimProvider = new CloudSimRunner();
//		cloudSimProvider.registerListener(new SampleListener(cloudSimProvider));
//		cloudSimProvider.run();

//		SimPlus.run();
	}





}
