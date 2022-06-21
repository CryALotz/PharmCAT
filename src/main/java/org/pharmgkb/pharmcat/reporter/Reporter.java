package org.pharmgkb.pharmcat.reporter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.pharmgkb.pharmcat.reporter.handlebars.ReportHelpers;
import org.pharmgkb.pharmcat.reporter.model.result.GeneReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the main class for running the reporting tool. It's responsible for taking input of all the
 * necessary data files, parsing them, and running the reporter components.
 *
 * This can be run both on the command line and procedurally.
 *
 * @author greytwist
 * @author Ryan Whaley
 */
public class Reporter {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String FINAL_REPORT      = "report";
  private static final String sf_templatePrefix = "/org/pharmgkb/pharmcat/reporter";
  private static final Gson sf_gson = new GsonBuilder().serializeNulls().excludeFieldsWithoutExposeAnnotation()
      .setPrettyPrinting().create();

  private ReportContext m_reportContext = null;
  private boolean m_testMode = false;


  /**
   * Run the actual report process. Parse the input file, do the matching, and write the report files.
   *
   * @param geneReports collection of {@link GeneReport} objects that came from the Phenotyper
   */
  public Reporter analyze(Collection<GeneReport> geneReports) throws IOException {

    //This is the primary work flow for generating the report where calls are matched to exceptions and
    // drug gene m_guidelineFiles based on reported haplotypes
    m_reportContext = new ReportContext(geneReports);

    return this;
  }

  /**
   * Print a HTML file of compiled report data
   * @param reportFile file to write output to
   */
  public void printHtml(Path reportFile, @Nullable String title, @Nullable Path jsonFile) throws IOException {

    Map<String,Object> reportData = m_reportContext.compile(title);
    if (isTestMode()) {
      reportData.remove("pharmcatVersion");
      reportData.remove("generatedOn");
      reportData.remove("cpicVersion");
    }

    Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader(sf_templatePrefix));
    StringHelpers.register(handlebars);
    handlebars.registerHelpers(ReportHelpers.class);

    try (BufferedWriter writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
      writer.write(handlebars.compile(FINAL_REPORT).apply(reportData));
    }

    if (jsonFile != null) {
      try (BufferedWriter writer = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
        writer.write(sf_gson.toJson(reportData));
        sf_logger.info("Writing Reporter JSON to " + jsonFile);
      }
    }
  }

  public ReportContext getContext() {
    return m_reportContext;
  }

  /**
   * Run the reporter in "test" mode. This means certain metadata (version numbers and dates) will be left out of
   * output data. This makes diffing comparisons between test runs easier.
   * @return true if this reporter is running on test data.
   */
  public boolean isTestMode() {
    return m_testMode;
  }

  public void setTestMode(boolean testMode) {
    m_testMode = testMode;
  }
}
