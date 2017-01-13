package org.pharmgkb.pharmcat.reporter;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import org.pharmgkb.pharmcat.haplotype.model.GeneCall;
import org.pharmgkb.pharmcat.reporter.model.DosingGuideline;
import org.pharmgkb.pharmcat.reporter.model.Group;
import org.pharmgkb.pharmcat.reporter.model.RelatedGene;
import org.pharmgkb.pharmcat.reporter.model.result.GeneReport;
import org.pharmgkb.pharmcat.reporter.model.result.GuidelineReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class acts as a central context for all data needed to generate the final report.
 *
 * It currently gathers
 * <ul>
 *   <li>{@link GeneCall} objects from the named allele matcher</li>
 *   <li>{@link GuidelineReport} objects from dosing guideline annotations</li>
 *   <li>Allele definitions on a per-gene basis</li>
 * </ul>
 *
 * @author greytwist
 * @author Ryan Whaley
 */
public class ReportContext {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String UNCALLED = "*uncalled*";

  private List<GeneCall> m_calls;
  private Multimap<String, String> m_sampleGeneToDiplotypeMap = TreeMultimap.create();
  private Map<String, GeneReport> m_symbolToGeneReportMap = new TreeMap<>();
  private List<GuidelineReport> m_interactionList;
  private Multimap<String, String> m_geneToDrugMap = TreeMultimap.create();

  /**
   * public constructor
   * @param calls GeneCall objects from the sample data
   * @param guidelines a List of all the guidelines to try to apply
   */
  public ReportContext(List<GeneCall> calls, List<DosingGuideline> guidelines) throws Exception {
    m_calls = calls;
    m_interactionList = guidelines.stream().map(GuidelineReport::new).collect(Collectors.toList());

    // prime the pump to list all genes needed to call all guidelines
    guidelines.stream()
        .flatMap(g -> g.getRelatedGenes().stream())
        .map(RelatedGene::getSymbol)
        .forEach(s -> m_sampleGeneToDiplotypeMap.put(s, UNCALLED));

    m_interactionList.forEach(r -> {
      for (String gene : r.getRelatedGeneSymbols()) {
        m_geneToDrugMap.putAll(gene, r.getRelatedDrugs());
      }
    });

    compileGeneData();
    findMatches();
  }

  /**
   * Takes {@link GeneCall} data and preps internal data structures for usage. Also prepares exception logic and applies
   * it to the calling data
   */
  private void compileGeneData() throws Exception {
    // ExceptionMatcher exceptionMatcher = new ExceptionMatcher();

    for (GeneCall call : m_calls) {

      // starts a new GeneReport based on data in the GeneCall
      GeneReport geneReport = new GeneReport(call);
      // adds exceptions to the GeneReport
      // exceptionMatcher.addExceptions(geneReport); 

      m_sampleGeneToDiplotypeMap.removeAll(call.getGene());
      m_sampleGeneToDiplotypeMap.putAll(call.getGene(), geneReport.getDips());

      m_symbolToGeneReportMap.put(call.getGene(), geneReport);
    }
    fixCyp2c19();
  }

  private boolean isCalled(String gene) {
    return m_calls != null && m_calls.stream()
        .filter(c -> c.getHaplotypes().size() > 0)
        .anyMatch(c -> c.getGene().equals(gene));
  }

  /**
   * Substitutes "*4" for "*4A" or "*4B" in the map of called diplotypes
   *
   * This is a temporary fix while the allele definitions for CYP2C19 don't match what's been annotated by CPIC
   *
   */
  private void fixCyp2c19() {
    if (m_sampleGeneToDiplotypeMap.keySet().contains("CYP2C19")) {
      List<String> fixedDiplotypes = m_sampleGeneToDiplotypeMap.get("CYP2C19").stream()
          .map(d -> d.replaceAll("\\*4[AB]", "*4"))
          .collect(Collectors.toList());
      m_sampleGeneToDiplotypeMap.removeAll("CYP2C19");
      m_sampleGeneToDiplotypeMap.putAll("CYP2C19", fixedDiplotypes);
    }
  }

  /**
   *  Call to do the actual matching, this should all be broken out into
   *  independent methods so errors are clearly and atomically identified
   *  and handled.
   *
   *  This is going to need to be rethought through and reconstructed
   */
  private void findMatches() throws Exception {

    for(GuidelineReport guideline : m_interactionList) {
      boolean reportable = guideline.getRelatedGeneSymbols().stream()
          .allMatch(this::isCalled);

      guideline.setReportable(reportable);

      if (!reportable) {
        String unCalledGenes = guideline.getRelatedGeneSymbols().stream()
            .filter(g -> !isCalled(g))
            .collect(Collectors.joining(","));

        sf_logger.warn("Can't annotate guideline {}, it's missing {}", guideline.getName(), unCalledGenes );
        continue;
      }

      sf_logger.info("Able to use {}", guideline.getName());

      Set<String> calledGenotypesForGuideline = makeAllCalledGenotypes(guideline.getRelatedGeneSymbols());

      for (Group annotationGroup : guideline.getGroups()) {
        calledGenotypesForGuideline.stream()
            .filter(calledGenotype -> annotationGroup.getGenotypes().contains(calledGenotype))
            .forEach(calledGenotype -> {
              guideline.addMatchingGroup(annotationGroup);
              guideline.putMatchedDiplotype(annotationGroup.getId(), calledGenotype);
            });
      }
    }
  }

  /**
   * Makes a set of called genotype Strings for the given collection of genes. This can be used later for matching to
   * annotation group genotypes
   * @param geneSymbols the gene symbols to include in the genotype strings
   * @return a Set of string genotype calls in the form "GENEA:*1/*2;GENEB:*2/*3"
   */
  private Set<String> makeAllCalledGenotypes(Collection<String> geneSymbols) {
    Set<String> results = new TreeSet<>();
    for (String symbol : geneSymbols) {
      results = makeCalledGenotypes(symbol, results);
    }
    return results;
  }

  private Set<String> makeCalledGenotypes(String symbol, Set<String> results) {
    if (results.size() == 0) {
      return Sets.newHashSet(m_sampleGeneToDiplotypeMap.get(symbol));
    }
    else {
      Set<String> newResults = new TreeSet<>();
      for (String geno1 : results) {
        for (String geno2 : m_sampleGeneToDiplotypeMap.get(symbol)) {
          Set<String> genos = new TreeSet<>();
          genos.add(geno1);
          genos.add(geno2);
          newResults.add(genos.stream().collect(Collectors.joining(";")));
        }
      }
      return newResults;
    }
  }

  /**
   * Maps a gene symbol String ("CYP2C19") to a Collection of the call Strings for that gene ("*1/*2")
   */
  public Multimap<String,String> getGeneCallMap() {
    return m_sampleGeneToDiplotypeMap;
  }

  public List<GuidelineReport> getGuidelineResults() {
    return m_interactionList;
  }

  public Map<String, GeneReport> getSymbolToGeneReportMap() {
    return m_symbolToGeneReportMap;
  }

  public Collection<String> getRelatedDrugs(@Nonnull String geneSymbol) {
    return m_geneToDrugMap.get(geneSymbol);
  }
}
