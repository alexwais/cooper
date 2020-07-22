package at.alexwais.cooper;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Based on https://itsallbinary.com/java-printing-to-console-in-table-format-simple-code-with-flexible-width-left-align-header-separator-line/
 */
public class ConsoleTable {

    private String[] columns;
    private boolean leftJustified = false;
    private List<Object[]> table;

    public ConsoleTable(String... columns){
        this.columns = columns;
        table = new ArrayList<>();
        addRow(columns);
    }

    public void addRow(Object... rowValues) {
        if (rowValues.length != columns.length) throw new IllegalArgumentException();
        table.add(rowValues);
    }

    public void print() {
        Map<Integer, Integer> columnLengths = new HashMap<>();
        table.forEach(a -> Stream.iterate(0, (i -> i < a.length), (i -> ++i)).forEach(i -> {
            columnLengths.putIfAbsent(i, 0);
            if (columnLengths.get(i) < a[i].toString().length()) {
                columnLengths.put(i, a[i].toString().length());
            }
        }));

        final StringBuilder formatString = new StringBuilder("");
        String flag = leftJustified ? "-" : "";
        columnLengths.forEach((key, value) -> formatString.append("| %").append(flag).append(value).append("s "));
        formatString.append("|\n");

        table.stream().findFirst().ifPresent(a -> System.out.printf(formatString.toString(), a));
        var sep = columnLengths.values().stream().map("-"::repeat).toArray(String[]::new);
        System.out.printf(formatString.toString(), sep);
        table.remove(0);
        table.forEach(a -> System.out.printf(formatString.toString(), a));
    }

}
