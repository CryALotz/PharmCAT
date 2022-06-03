package org.pharmgkb.pharmcat.reporter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.gson.Gson;
import org.pharmgkb.common.util.PathUtils;
import org.pharmgkb.pharmcat.reporter.model.DataSource;
import org.pharmgkb.pharmcat.reporter.model.pgkb.AccessionObject;
import org.pharmgkb.pharmcat.reporter.model.pgkb.DosingGuideline;
import org.pharmgkb.pharmcat.reporter.model.pgkb.GuidelinePackage;
import org.pharmgkb.pharmcat.reporter.model.result.Diplotype;


public class PgkbGuidelineCollection {
  public static final Predicate<GuidelinePackage> ONLY_EXTENDED_GUIDELINES = guidelinePackage -> {
    DosingGuideline guideline = guidelinePackage.getGuideline();
    return guideline.getSource().equals(DataSource.DPWG.name())
        && guideline.isRecommendation()
        && guidelinePackage.getGroups().size() > 0;
  };

  private static final String GUIDELINES_DIR = "org/pharmgkb/pharmcat/reporter/guidelines";

  private final List<GuidelinePackage> f_guidelinePackages = new ArrayList<>();
  private final Multimap<String,GuidelinePackage> f_guidelineMap = TreeMultimap.create();

  public PgkbGuidelineCollection() throws IOException {
    Path guidelinesDir = PathUtils.getPathToResource(GUIDELINES_DIR);
    List<Path> annotationFiles = Files.list(guidelinesDir)
        .filter(f -> f.getFileName().toString().endsWith(".json"))
        .toList();
    if (annotationFiles.size() == 0) {
      throw new IOException("No annotation definitions to read from");
    }

    Gson gson = new Gson();
    for (Path guidelineFile : annotationFiles) {
      try (BufferedReader br = Files.newBufferedReader(guidelineFile)) {
        GuidelinePackage guidelinePackage = gson.fromJson(br, GuidelinePackage.class);
        if (ONLY_EXTENDED_GUIDELINES.test(guidelinePackage)) {
          if (guidelinePackage.getGuideline().getGuidelineGenes().size() > 1) {
            throw new RuntimeException("DPWG Guidelines with more than one gene are not yet supported");
          }
          f_guidelinePackages.add(guidelinePackage);
          for (AccessionObject chemical : guidelinePackage.getGuideline().getRelatedChemicals()) {
            f_guidelineMap.put(chemical.getName(), guidelinePackage);
          }
        }
      }
    }
  }

  public List<GuidelinePackage> getGuidelinePackages() {
    return f_guidelinePackages;
  }

  /**
   * This will return the phenotype Strings that match a given {@link Diplotype}
   */
  public Set<String> getPhenotypesForDiplotype(Diplotype diplotype) {
    Set<String> phenotypes = new TreeSet<>();
    for (GuidelinePackage guidelinePackage : getGuidelinePackages()) {
      Set<String> functionKeys = guidelinePackage.getGuideline().getFunctionKeysForDiplotype(diplotype);
      for (String functionKey : functionKeys) {
        guidelinePackage.getGroups().stream()
            .filter(group -> group.matchesKey(functionKey) && group.getMetabolizerStatus() != null)
            .map(group -> group.getMetabolizerStatus().getHtmlStripped())
            .forEach(phenotypes::add);
      }
    }
    return phenotypes;
  }

  public Collection<GuidelinePackage> findGuidelinePackages(String chemicalName) {
    return f_guidelineMap.get(chemicalName);
  }

  /**
   * Get the collection of Chemicals that have DPWG guidelines
   * @return a Set of chemical name Strings
   */
  public Set<String> getChemicals() {
    return f_guidelineMap.keySet();
  }

  public Set<String> getGenes() {
    return f_guidelinePackages.stream()
        .flatMap(p -> p.getGenes().stream())
        .collect(Collectors.toSet());
  }
}
