package org.pharmgkb.pharmcat.reporter.model.result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
import org.pharmgkb.common.comparator.HaplotypeNameComparator;
import org.pharmgkb.pharmcat.haplotype.NamedAlleleMatcher;
import org.pharmgkb.pharmcat.haplotype.model.GeneCall;
import org.pharmgkb.pharmcat.reporter.DiplotypeFactory;
import org.pharmgkb.pharmcat.reporter.VariantReportFactory;
import org.pharmgkb.pharmcat.reporter.model.DrugLink;
import org.pharmgkb.pharmcat.reporter.model.MessageAnnotation;
import org.pharmgkb.pharmcat.reporter.model.OutsideCall;
import org.pharmgkb.pharmcat.reporter.model.VariantReport;
import org.pharmgkb.pharmcat.util.Slco1b1AlleleMatcher;


/**
 * This class is used to help collect Gene-related data for later reporting
 */
public class GeneReport implements Comparable<GeneReport> {
  // never display these genes in the gene call list
  private static final Set<String> IGNORED_GENES       = ImmutableSet.of("IFNL4");
  private static final Set<String> OVERRIDE_DIPLOTYPES = ImmutableSet.of("SLCO1B1");
  private static final Set<String> SINGLE_PLOIDY       = ImmutableSet.of("G6PD", "MT-RNR1");
  private static final Set<String> CHROMO_X            = ImmutableSet.of("G6PD");
  private static final Set<String> ALLELE_PRESENCE     = ImmutableSet.of("HLA-A", "HLA-B");
  private static final String UNCALLED = "not called";
  public static  final String NA = "N/A";
  public static  final String YES = "Yes";
  public static  final String NO  = "No";

  @Expose
  @SerializedName("geneSymbol")
  private final String f_gene;
  @Expose
  @SerializedName("chr")
  private String m_chr;
  @Expose
  @SerializedName("phased")
  private boolean m_phased = false;
  @Expose
  @SerializedName("callSource")
  private final CallSource f_callSource;
  @Expose
  @SerializedName("uncalledHaplotypes")
  private final SortedSet<String> m_uncalledHaplotypes = new TreeSet<>(HaplotypeNameComparator.getComparator());
  @Expose
  @SerializedName("messages")
  private final List<MessageAnnotation> m_messages = new ArrayList<>();
  @Expose
  @SerializedName("relatedDrugs")
  private Set<DrugLink> m_relatedDrugs = new TreeSet<>();
  @Expose
  @SerializedName("matcherDiplotypes")
  private final List<Diplotype> m_matcherDiplotypes = new ArrayList<>();
  @Expose
  @SerializedName("diplotypes")
  private final List<Diplotype> m_reporterDiplotypes = new ArrayList<>();
  @Expose
  @SerializedName("variants")
  private final List<VariantReport> m_variantReports = new ArrayList<>();
  @Expose
  @SerializedName("variantsOfInterest")
  private final List<VariantReport> m_variantOfInterestReports = new ArrayList<>();
  @Expose
  @SerializedName("highlightedVariants")
  private final List<String> m_highlightedVariants = new ArrayList<>();

  /**
   * public constructor
   */
  public GeneReport(String geneSymbol) {
    f_gene = geneSymbol;
    f_callSource = CallSource.NONE;
  }

  public GeneReport(GeneCall call) {
    f_gene = call.getGene();
    f_callSource = CallSource.MATCHER;
    try {
      m_chr = call.getChromosome();
      m_uncalledHaplotypes.clear();
      m_uncalledHaplotypes.addAll(call.getUncallableHaplotypes());
      m_phased = call.isPhased();

      VariantReportFactory variantReportFactory = new VariantReportFactory(f_gene, m_chr);
      call.getVariants().stream()
          .map(variantReportFactory::make).forEach(m_variantReports::add);
      call.getMatchData().getMissingPositions().stream()
          .map(variantReportFactory::make).forEach(m_variantReports::add);
      call.getVariantsOfInterest().stream()
          .map(variantReportFactory::make).forEach(m_variantOfInterestReports::add);

      // set the flag in reports for the variants with mismatched alleles
      call.getMatchData().getMismatchedPositions().stream()
          .flatMap(a -> m_variantReports.stream().filter(v -> v.getPosition() == a.getPosition()))
          .forEach(r -> r.setMismatch(true));
    } catch (IOException ex) {
      throw new RuntimeException("Could not start a gene report", ex);
    }
  }

  public GeneReport(OutsideCall call) {
    f_gene = call.getGene();
    f_callSource = CallSource.OUTSIDE;
  }

  public void setDiplotypes(DiplotypeFactory diplotypeFactory, GeneCall geneCall) {
    m_matcherDiplotypes.addAll(diplotypeFactory.makeDiplotypes(geneCall));

    if (Slco1b1AlleleMatcher.shouldBeUsedOn(this)) {
      Slco1b1AlleleMatcher
          .makeLookupCalls(this, diplotypeFactory)
          .ifPresent(m_reporterDiplotypes::add);
    }
    else {
      m_reporterDiplotypes.addAll(diplotypeFactory.makeDiplotypes(geneCall));
    }
  }

  public void setDiplotypes(DiplotypeFactory diplotypeFactory, OutsideCall outsideCall) {
    m_reporterDiplotypes.removeIf(Diplotype::isUnknown);
    m_reporterDiplotypes.addAll(diplotypeFactory.makeDiplotypes(outsideCall));
  }

  /**
   * The gene symbol for this gene
   */
  public String getGene(){
    return f_gene;
  }

  /**
   * The gene symbol for display purposes, like in a report. This accounts for the IFNL3/4 problem
   */
  public String getGeneDisplay() {
    switch (f_gene) {
      case "IFNL3":
      case "IFNL4":
        return "IFNL3/4";
      default:
        return f_gene;
    }
  }

  /**
   * The chromosome this gene appears on
   */
  public String getChr() {
    return m_chr;
  }

  /**
   * The list of messages that apply to this gene
   */
  public List<MessageAnnotation> getMessages(){
    return m_messages;
  }

  public void addMessages(Collection<MessageAnnotation> messages) {
    if (messages == null) {
      return;
    }

    // separate the general messages from specific genotype call messages
    messages.forEach(ma -> {
      if (ma.getExceptionType().equals(MessageAnnotation.TYPE_GENOTYPE)) {
        String rsid = ma.getMatches().getVariant();

        Optional<String> call = getVariantReports().stream()
            .filter(v -> v.getDbSnpId() != null && v.getDbSnpId().matches(rsid) && !v.isMissing())
            .map(VariantReport::getCall)
            .reduce((a,b) -> {throw new RuntimeException();});
        String genotype;
        if (call.isEmpty() || StringUtils.isBlank(call.get())) {
          genotype = "missing";
        }
        else {
          genotype = rsid + call.get().replaceAll("[|/]", "/"+rsid);
        }
        m_highlightedVariants.add(genotype);

      }
      else {
        m_messages.add(ma);
      }
    });
  }

  /**
   * This will add the applicable variant warnings to the {@link VariantReport} objects in this {@link GeneReport}
   * @param variantWarnings a Map of all variant warnings by "chr:position" strings
   */
  public void addVariantWarningMessages(Map<String, Collection<String>> variantWarnings) {
    if (variantWarnings != null && variantWarnings.size() > 0) {
      for (VariantReport variantReport : m_variantReports) {
        Collection<String> warnings = variantWarnings.get(variantReport.toChrPosition());
        if (warnings != null && warnings.size() > 0) {
          variantReport.setWarnings(warnings);
        }
      }
    }
  }

  public List<VariantReport> getVariantReports() {
    return m_variantReports;
  }

  /**
   * Finds the first {@link VariantReport} in this gene report that has the specified RSID. Since RSIDs should be unique
   * to variants this should never silently hide multiple matches.
   * @param rsid a dbSNP RSID that identifies a variant
   * @return an {@link Optional} {@link VariantReport}
   */
  public Optional<VariantReport> findVariantReport(String rsid) {
    return m_variantReports.stream().filter(v -> v.getDbSnpId() != null && v.getDbSnpId().contains(rsid)).findFirst();
  }

  public List<VariantReport> getVariantOfInterestReports() {
    return m_variantOfInterestReports;
  }

  /**
   * Gets the Set of Haplotypes the the matcher could not evaluate
   */
  public Set<String> getUncalledHaplotypes() {
    return m_uncalledHaplotypes;
  }

  public String toString() {
    return f_gene + " Report";
  }

  /**
   * True if the {@link NamedAlleleMatcher} has returned at least one call for this gene, false otherwise
   */
  public boolean isCalled() {
    return m_matcherDiplotypes.size() > 0 && m_matcherDiplotypes.stream().noneMatch(Diplotype::isUnknown);
  }

  /**
   * True if there is a diplotype for this gene that the reporter can use, false otherwise. The reporter may be able to 
   * use diplotype calls not made by the matcher (e.g. SLCO1B1)
   */
  public boolean isReportable() {
    return m_reporterDiplotypes.size() > 0 && m_reporterDiplotypes.stream().noneMatch(Diplotype::isUnknown);
  }

  /**
   * True if this gene does not use allele function to assign phenotype but instead relies on the presence or absense of
   * alleles for its phenotypes (e.g. HLA's)
   * @return true if this gene assigns phenotype based on allele presence
   */
  public boolean isAllelePresenceType() {
    return ALLELE_PRESENCE.contains(f_gene);
  }

  @Override
  public int compareTo(GeneReport o) {
    return Objects.compare(getGene(), o.getGene(), String.CASE_INSENSITIVE_ORDER);
  }

  /**
   * Gets a list of {@link DrugLink} objects that are in the same guidelines as this gene
   * @return a list of {@link DrugLink} objects
   */
  public Set<DrugLink> getRelatedDrugs() {
    return m_relatedDrugs;
  }

  private void addRelatedDrug(DrugLink drug) {
    if (m_relatedDrugs == null) {
      m_relatedDrugs = new TreeSet<>();
    }
    m_relatedDrugs.add(drug);
  }

  /**
   * Adds the drugs in the given <code>drugReport</code> to this report as {@link DrugLink} objects
   * @param drugReport a {@link DrugLink} with relatedDrugs
   */
  public void addRelatedDrugs(DrugReport drugReport) {

    drugReport.getRelatedDrugs().stream()
        .map(d -> new DrugLink(d, drugReport.getId()))
        .forEach(this::addRelatedDrug);
  }

  /**
   * True if the genotyping data in the report comes from outside PharmCAT, false if match is made by PharmCAT
   */
  public boolean isOutsideCall() {
    return f_callSource == CallSource.OUTSIDE;
  }

  public CallSource getCallSource() {
    return f_callSource;
  }

  /**
   * Used in the final report template in the Genotype Summary table in the "Genotype" column
   * @return a Collection of diplotype Strings (e.g. *2/*3, *4 (heterozygote))
   */
  public Collection<String> printDisplayCalls() {
    if (!isReportable()) {
      if (OVERRIDE_DIPLOTYPES.contains(getGene()) && !m_reporterDiplotypes.isEmpty()) {
        return m_reporterDiplotypes.stream().sorted().map(Diplotype::printDisplay).collect(Collectors.toList());
      }
      return ImmutableList.of(UNCALLED);
    }

    return m_reporterDiplotypes.stream().sorted().map(Diplotype::printDisplay).collect(Collectors.toList());
  }

  /**
   * Used in the final report template in the Genotype Summary table in the "Allele Functionality" column
   * @return a Collection of function Strings (e.g. Two no function alleles)
   */
  public Collection<String> printDisplayFunctions() {
    if (!isReportable() || isAllelePresenceType()) {
      return new ArrayList<>();
    }
    return m_reporterDiplotypes.stream().sorted().map(Diplotype::printFunctionPhrase).collect(Collectors.toList());
  }

  /**
   * Used in the final report template in the Genotype Summary table in the "Phenotype" column
   * @return a Collection of phenotype Strings (e.g. Poor Metabolizer)
   */
  public Collection<String> printDisplayPhenotypes() {
    if (!isReportable()) {
      return ImmutableList.of(NA);
    }
    return m_reporterDiplotypes.stream().sorted().map(Diplotype::printPhenotypes).collect(Collectors.toList());
  }

  /**
   * Wether this gene has been marked as phased by the matcher
   */
  public boolean isPhased() {
    return m_phased;
  }

  /**
   * Is this gene ignored, meaning it shouldn't be included in reports
   * @return true if this gene should be left out of output reports
   */
  public boolean isIgnored() {
    return isIgnored(getGene());
  }

  /**
   * Is the specified gene symbol ignored, e.g. it shouldn't be included in reports
   * @param gene a gene symbol
   * @return true if this gene should be left out of output reports
   */
  public static boolean isIgnored(String gene) {
    return StringUtils.isNotBlank(gene) && IGNORED_GENES.contains(gene);
  }

  /**
   * Is the specified gene single ploidy, i.e. it is on chrY or chrM which occur on a single chromosome, not a pair.
   * @param gene a gene symbol
   * @return true if the gene occurs on a single chromosome, not a pair
   */
  public static boolean isSinglePloidy(String gene) {
    return StringUtils.isNotBlank(gene) && SINGLE_PLOIDY.contains(gene);
  }

  public static boolean isXChromo(String gene) {
    return StringUtils.isNotBlank(gene) && CHROMO_X.contains(gene);
  }

  /**
   * This will test whether this has a called haplotype at the reporter level, so this will not be just matched
   * haplotypes but also outside call haplotypes and inferred haplotypes
   * @param haplotype a haplotype name to test for
   * @return true if the haplotype has been called at least once (het or hom) for this gene
   */
  public boolean hasHaplotype(String haplotype) {
    return m_reporterDiplotypes.stream()
        .anyMatch((d) -> d.hasAllele(haplotype));
  }

  public List<Diplotype> getMatcherDiplotypes() {
    return m_matcherDiplotypes;
  }

  public List<Diplotype> getReporterDiplotypes() {
    return m_reporterDiplotypes;
  }

  /**
   * Get variants that should be shown separately in the report
   */
  public List<String> getHighlightedVariants() {
    return m_highlightedVariants;
  }

  /**
   * Is there any missing variant in this gene?
   * @return true if there is any missing variant
   */
  public String isMissingVariants() {
    if (getCallSource() == CallSource.OUTSIDE) return NA;
    
    return m_variantReports.stream().anyMatch(VariantReport::isMissing) ? YES : NO;
  }
}
