package com.endava.cats.io;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.TimeExecutionDetails;
import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.endava.cats.model.report.CatsTestCase;
import com.endava.cats.model.report.CatsTestCaseSummary;
import com.endava.cats.model.report.CatsTestReport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.fusesource.jansi.Ansi;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * This class is responsible for writing the final report file(s).
 */
@Service
public class TestCaseExporter {
    public static final Gson GSON = new GsonBuilder()
            .setLenient()
            .setPrettyPrinting()
            .setExclusionStrategies(new ExcludeTestCaseStrategy())
            .registerTypeAdapter(Long.class, new LongTypeSerializer())
            .serializeNulls().create();

    private static final String TEST_CASES_FOLDER = "cats-report";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(TestCaseExporter.class);
    private static final String SOURCE = "SOURCE";
    private static final String SCRIPT = "<script type=\"text/javascript\" src=\"" + SOURCE + "\"></script>";
    private static final StringBuilder builder = new StringBuilder();
    private static final String VAR = "var";
    private static final String PLACEHOLDER = "PLACEHOLDER";
    private static final String REPORT_ZIP = "report.zip";
    private static final String SUMMARY = "summary";
    private static final String REPORT_HTML = "index.html";
    private static final String JAVASCRIPT_EXTENSION = ".js";
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private Path path;
    @Autowired
    private ReportingArguments reportingArguments;

    @PostConstruct
    void initPath() throws IOException {
        String subFolder = reportingArguments.isTimestampReports() ? String.valueOf(System.currentTimeMillis()) : "";
        path = Paths.get(TEST_CASES_FOLDER, subFolder);
        if (!reportingArguments.isTimestampReports() && path.toFile().exists()) {
            deleteFiles(path);
        }
        if (!path.toFile().exists()) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                LOGGER.error("Exception while creating root test cases folder: {}", e.getMessage());
            }
        }
    }

    private void deleteFiles(Path path) throws IOException {
        for (File file : Objects.requireNonNull(path.toFile().listFiles())) {
            if (!file.isDirectory()) {
                Files.delete(file.toPath());
            }
        }
    }

    public void writePerformanceReport(Map<String, CatsTestCase> testCaseMap) {
        if (reportingArguments.printExecutionStatistics()) {
            Map<String, List<CatsTestCase>> executionDetails = extractExecutionDetails(testCaseMap);

            LOGGER.info(" ");
            LOGGER.info(" ---------------------------- Execution time details ---------------------------- ");
            LOGGER.info(" ");
            executionDetails.forEach(this::writeExecutionTimesForPathAndHttpMethod);
            LOGGER.info(" ");
        } else {
            LOGGER.skip("Skip printing time execution statistics. You can use --printExecutionStatistics to enable this feature!");
        }
    }

    private Map<String, List<CatsTestCase>> extractExecutionDetails(Map<String, CatsTestCase> testCaseMap) {
        Map<String, CatsTestCase> allRun = testCaseMap.entrySet().stream().filter(entry -> entry.getValue().isNotSkipped() && entry.getValue().notIgnoredForExecutionStatistics())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return allRun.values().stream()
                .collect(Collectors.groupingBy(testCase -> testCase.getResponse().getHttpMethod() + " " + testCase.getPath()))
                .entrySet().stream().filter(entry -> entry.getValue().size() > 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void writeExecutionTimesForPathAndHttpMethod(String key, List<CatsTestCase> value) {
        double average = value.stream().mapToLong(testCase -> testCase.getResponse().getResponseTimeInMs()).average().orElse(0);
        List<CatsTestCase> sortedRuns = value.stream().sorted(Comparator.comparingLong(testCase -> testCase.getResponse().getResponseTimeInMs())).collect(Collectors.toList());
        CatsTestCase bestCase = sortedRuns.get(0);
        CatsTestCase worstCase = sortedRuns.get(sortedRuns.size() - 1);
        List<String> executions = sortedRuns.stream().map(CatsTestCase::executionTimeString).collect(Collectors.toList());
        TimeExecutionDetails timeExecutionDetails = TimeExecutionDetails.builder().average(average).
                path(key).bestCase(bestCase.executionTimeString()).worstCase(worstCase.executionTimeString()).
                executions(executions).build();


        LOGGER.info("Details for path {} ", ansi().fg(Ansi.Color.GREEN).a(timeExecutionDetails.getPath()).reset());
        LOGGER.note(ansi().fgYellow().a("Average response time: {}ms").reset().toString(), ansi().bold().a(NumberFormat.getInstance().format(timeExecutionDetails.getAverage())));
        LOGGER.note(ansi().fgRed().a("Worst case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getWorstCase()));
        LOGGER.note(ansi().fgGreen().a("Best case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getBestCase()));
        LOGGER.note("{} executed tests (sorted by response time):  {}", timeExecutionDetails.getExecutions().size(), timeExecutionDetails.getExecutions());
        LOGGER.info(" ");
    }

    public void writeSummary(Map<String, CatsTestCase> testCaseMap, int all, int success, int warnings, int errors) {
        Path testPath = Paths.get(path.toFile().getAbsolutePath(), SUMMARY.concat(JAVASCRIPT_EXTENSION));

        List<CatsTestCaseSummary> summaries = testCaseMap.entrySet().stream()
                .filter(entry -> entry.getValue().isNotSkipped())
                .map(testCase -> CatsTestCaseSummary.fromCatsTestCase(testCase.getKey(), testCase.getValue())).sorted()
                .collect(Collectors.toList());

        CatsTestReport report = CatsTestReport.builder().summaryList(summaries).errors(errors).success(success).totalTests(all)
                .warnings(warnings).timestamp(OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).build();

        String toWrite = GSON.toJson(report);

        toWrite = VAR + " " + SUMMARY + " = " + toWrite;
        this.write(SUMMARY, testPath, toWrite);
    }

    public void writeReportFiles() {
        try (ZipInputStream zipInputStream = new ZipInputStream(resolver.getResourceLoader().getResource(REPORT_ZIP).getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Files.copy(zipInputStream, Paths.get(path.toFile().getAbsolutePath(), entry.getName()));
            }
            try (Stream<String> index = Files.lines(Paths.get(path.toFile().getAbsolutePath(), REPORT_HTML))) {
                List<String> updatedIndex = index.map(line -> line.replace(PLACEHOLDER, builder.toString())).collect(Collectors.toList());
                Files.write(Paths.get(path.toFile().getAbsolutePath(), REPORT_HTML), updatedIndex);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to write reporting files!", e);
        }
    }

    public void writeToFile(CatsTestCase testCase) {
        String testCaseName = MDC.get("id").replace(" ", "");
        Path testPath = Paths.get(path.toFile().getAbsolutePath(), testCaseName.concat(JAVASCRIPT_EXTENSION));

        String toWrite = GSON.toJson(testCase);

        toWrite = VAR + " " + testCaseName + " = " + toWrite;
        builder.append(SCRIPT.replace(SOURCE, testCaseName.concat(JAVASCRIPT_EXTENSION))).append(System.lineSeparator());
        write(testCaseName, testPath, toWrite);
    }

    private void write(String id, Path testPath, String toWrite) {
        try (BufferedWriter writer = Files.newBufferedWriter(testPath)) {

            writer.write(toWrite);
            LOGGER.complete("Finish writing test case {} to file {}", id, testPath);
        } catch (IOException e) {
            LOGGER.warning("Something went wrong while writing test case {}: {}", id, e.getMessage(), e);
        }
    }

    public Path getPath() {
        return path;
    }
}
