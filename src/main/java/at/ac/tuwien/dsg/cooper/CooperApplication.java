package at.ac.tuwien.dsg.cooper;

import at.ac.tuwien.dsg.cooper.scheduler.SchedulingCycle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class CooperApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(CooperApplication.class, args);
	}

	@Value("${cooper.scenario}")
	private String scenario;
	@Value("${cooper.multiplicator}")
	private Integer multiplicator;

	@Autowired
	private SchedulingCycle scheduler;

	@Override
	public void run(String... args) {
		log.info("");
		log.info("EXECUTING COOPER with scenario: {}@{}x", scenario, multiplicator);

		scheduler.run();
	}

}
