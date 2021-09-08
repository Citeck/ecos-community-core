package ru.citeck.ecos.action.group;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Objects necessary for export to Csv-file
 */
@Data
@AllArgsConstructor
public class CsvEnvironment {
    private final String cellDelimiter;
    private StringBuilder reportBuilder;
}
