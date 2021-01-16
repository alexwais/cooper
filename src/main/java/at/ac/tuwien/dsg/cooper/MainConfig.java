package at.ac.tuwien.dsg.cooper;

import at.ac.tuwien.dsg.cooper.config.DataCenterConfigMap;
import at.ac.tuwien.dsg.cooper.config.DataCenterDistanceConfigList;
import at.ac.tuwien.dsg.cooper.config.ServiceConfigMap;
import at.ac.tuwien.dsg.cooper.scheduler.Initializer;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MainConfig {

	@Autowired
	private DataCenterConfigMap dataCenterConfig;
	@Autowired
	private DataCenterDistanceConfigList distanceConfig;
	@Autowired
	private ServiceConfigMap serviceConfig;

	@Value("${cooper.scenario-multiplicator}")
	private Float scenarioMultiplicator;
	@Value("${cooper.load-multiplicator}")
	private Integer loadMultiplicator;


	@Bean
	public Model model() {
		var initializer = new Initializer((int) (scenarioMultiplicator * loadMultiplicator), dataCenterConfig, distanceConfig, serviceConfig);
		return new Model(initializer.getDataCenters(), initializer.getServices(), initializer.getInteractionMultiplication(), initializer.getDataCenterDistanceGraph());
	}

}
