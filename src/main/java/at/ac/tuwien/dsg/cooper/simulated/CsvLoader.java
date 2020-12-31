package at.ac.tuwien.dsg.cooper.simulated;

import at.ac.tuwien.dsg.cooper.scheduler.Model;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;


@Slf4j
public class CsvLoader {

    private Model model;

    private static final String COMMA_DELIMITER = ",|;";

    public CsvLoader(Model model) {
        this.model = model;
    }


    public List<LoadRecord> load(String csvFileName) {
        var csvData = loadCsv(csvFileName);

        // get and remove header row
        var columns = csvData.get(0);
        csvData.remove(0);

        // map rows to records
        var records = new ArrayList<LoadRecord>();
        for (List<String> rawRecord : csvData) {
            var secondsElapsed = Integer.valueOf(rawRecord.get(0));
            var record = new LoadRecord(secondsElapsed);
            model.getServices().keySet().forEach(serviceName -> {
                var columnIndex = columns.indexOf(serviceName);
                var load = Integer.valueOf(rawRecord.get(columnIndex));
                record.getExternalServiceLoad().put(serviceName, load);
            });
            records.add(record);
        }

        return records;
    }

    private List<List<String>> loadCsv(String fileName) {
        try {
            var file = new ClassPathResource(fileName).getFile();
            var records = new ArrayList<List<String>>();

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    var values = line.split(COMMA_DELIMITER);
                    records.add(Arrays.asList(values));
                }
            }

            return records;
        } catch (Exception e) {
            throw new RuntimeException("Error occurred while loading load fixture from CSV", e);
        }
    }

}
