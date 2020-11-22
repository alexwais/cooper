package at.alexwais.cooper;

import at.alexwais.cooper.config.DataCenterConfigMap;
import at.alexwais.cooper.config.DataCenterDistanceConfigList;
import at.alexwais.cooper.config.ServiceConfigMap;
import at.alexwais.cooper.scheduler.Initializer;
import at.alexwais.cooper.scheduler.Model;
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

	@Value("${cooper.multiplicator}")
	private Integer multiplicator;


	@Bean
	public Model model() {
		var initializer = new Initializer(multiplicator, dataCenterConfig, distanceConfig, serviceConfig);
		return new Model(initializer.getDataCenters(), initializer.getServices(), initializer.getInteractionMultiplication(), initializer.getDataCenterDistanceGraph());
	}

}
