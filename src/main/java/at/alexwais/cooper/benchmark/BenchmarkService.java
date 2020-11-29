package at.alexwais.cooper.benchmark;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BenchmarkService {

    @Value("${cooper.benchmarkOutput}")
    private String outputFileName;
    private Map<Integer, BenchmarkRecord> records = new HashMap<>();


    public void addRecord(BenchmarkRecord record) {
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
