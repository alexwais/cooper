package at.ac.tuwien.dsg.cooper.benchmark;

import at.ac.tuwien.dsg.cooper.scheduler.Model;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BenchmarkService {

    private Model model;
    private String outputFileName;

    private Map<Integer, BenchmarkRecord> records = new HashMap<>();

    @Autowired
    public BenchmarkService(Model model, @Value("${cooper.benchmarkOutput}") String outputFileName) {
        this.model = model;
        this.outputFileName = outputFileName;
    }


    public void addRecord(BenchmarkRecord record) {
        // TODO proper time of records?...
//        if (record.getLastOptimizationResult() != null) {
            var simulation = new InteractionSimulation(model, record.getLastOptResult().getAllocation(), record.getMeasures());
            simulation.simulate();
            var avgLatency = simulation.getInteractionRecorder().getAverageLatency();
            record.setAvgLatency(avgLatency);
//        }
        this.records.put(record.getT(), record);
    }

    public void print() throws IOException {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = mapper.schemaFor(BenchmarkRecord.class)
                .withColumnSeparator(';')
                .withUseHeader(true);
        ObjectWriter myObjectWriter = mapper.writer(schema);

        var file = new File("benchmark/" + outputFileName);
        FileOutputStream tempFileOutputStream = new FileOutputStream(file);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(tempFileOutputStream, 1024);
        OutputStreamWriter writerOutputStream = new OutputStreamWriter(bufferedOutputStream, "UTF-8");
        myObjectWriter.writeValue(writerOutputStream, records.values());
    }
}
