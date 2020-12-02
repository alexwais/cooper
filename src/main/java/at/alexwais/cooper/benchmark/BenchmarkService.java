package at.alexwais.cooper.benchmark;

import at.alexwais.cooper.scheduler.Model;
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

    private InteractionSimulation simulation;
    private String outputFileName;

    private Map<Integer, BenchmarkRecord> records = new HashMap<>();

    @Autowired
    public BenchmarkService(Model model, @Value("${cooper.benchmarkOutput}") String outputFileName) {
        this.simulation = new InteractionSimulation(model);
        this.outputFileName = outputFileName;
    }


    public void addRecord(BenchmarkRecord record) {
        if (record.getOptimizationResult() != null) {
            simulation.simulate(record.getOptimizationResult().getAllocation(), record.getOptimizationResult().getUnderlyingMeasures());
        }
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
