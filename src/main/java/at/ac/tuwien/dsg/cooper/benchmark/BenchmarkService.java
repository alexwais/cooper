package at.ac.tuwien.dsg.cooper.benchmark;

import at.ac.tuwien.dsg.cooper.scheduler.Model;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BenchmarkService {

    private Model model;
    private String outputFileName;

    private Map<Integer, BenchmarkRecord> records = new HashMap<>();

    @Autowired
    public BenchmarkService(Model model, @Value("${cooper.benchmarkOutput}") String outputFileName) {
        this.model = model;
        this.outputFileName = outputFileName;
    }


    private float accumulatedCost = 0f;
    private float currentAccumulatedCost = 0f;
    private BenchmarkRecord currentRecord; // set every 2-minute interval

    public void sample(BenchmarkRecord record) {
        var seconds = record.getSeconds();
        var minute = seconds / 60;
        var isEvenMinute = seconds % 120 == 0;
        var isOddMinute = (seconds + 60) % 120 == 0;

        accumulatedCost += record.getCurrentAllocation().getTotalCost() / 120; // hourly basis
        currentAccumulatedCost += record.getCurrentAllocation().getTotalCost() / 120; // hourly basis

        if (isEvenMinute) {
            if (currentRecord != null) {
                // finalize previous period
                currentRecord.setCost(currentAccumulatedCost);
                currentRecord.setAccCost(accumulatedCost);
                this.records.put(currentRecord.getT(), currentRecord);
                currentRecord.setRecords(new ArrayList<>(this.records.values()));
                currentRecord = null;
            }
            // set target for new period
            var simulation = new InteractionSimulation(model, record.getLastOptResult().getAllocation(), record.getMeasures());
            simulation.simulate();
            record.setLatency(simulation.getInteractionRecorder().getAverageLatency());
            record.setInteractionCalls(simulation.getInteractionRecorder().getTotalCalls().intValue());
            currentRecord = record;
            currentAccumulatedCost = 0f;
        }
        if (isOddMinute) {
            assert currentRecord != null;
            currentRecord.setPeakContainerCount(record.getCurrentAllocation().getAllocatedTuples().size());
        }
    }

    public void saveToFile() throws IOException {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = mapper.schemaFor(BenchmarkRecord.class)
                .withColumnSeparator(';')
                .withUseHeader(true);
        ObjectWriter myObjectWriter = mapper.writer(schema);

        var timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").format(LocalDateTime.now());
        var filePath = "benchmark/" + outputFileName.replace(".csv", "_" + timestamp + ".csv");
        var file = new File(filePath);
        FileOutputStream tempFileOutputStream = new FileOutputStream(file);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(tempFileOutputStream, 1024);
        OutputStreamWriter writerOutputStream = new OutputStreamWriter(bufferedOutputStream, "UTF-8");
        myObjectWriter.writeValue(writerOutputStream, records.values());
        log.info("Successfully wrote benchmark to {}", filePath);
    }

}
