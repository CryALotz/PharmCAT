package org.pharmgkb.pharmcat.definition.model;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.pharmgkb.pharmcat.util.DataManager;


/**
 * This class represents one complete allele translation set for a gene.
 *
 * @author Ryan Whaley
 */
public class DefinitionFile {
  public static final String FORMAT_VERSION = "1";
  // metadata
  @Expose
  @SerializedName("formatVersion")
  private String m_formatVersion = FORMAT_VERSION;
  @Expose
  @SerializedName("cpicVersion")
  private String m_cpicVersion;
  @Expose
  @SerializedName("modificationDate")
  private Date m_modificationDate;
  @Expose
  @SerializedName("gene")
  private String m_geneSymbol;
  @Expose
  @SerializedName("orientation")
  private String m_orientation;
  @Expose
  @SerializedName("chromosome")
  private String m_chromosome;
  @Expose
  @SerializedName("genomeBuild")
  private String m_genomeBuild;
  @Expose
  @SerializedName("refSeqChromosomeId")
  private String m_refSeqChromosome;
  // definitions
  @Expose
  @SerializedName("variants")
  private VariantLocus[] m_variants;
  @Expose
  @SerializedName("namedAlleles")
  private SortedSet<NamedAllele> m_namedAlleles;


  /**
   * The format version of the definition file.
   */
  public String getFormatVersion() {
    return m_formatVersion;
  }

  public void setFormatVersion(String formatVersion) {
    m_formatVersion = formatVersion;
  }

  /**
   * The version of the CPIC DB that this definition was pulled from
   */
  public String getCpicVersion() {
    return m_cpicVersion;
  }

  public void setCpicVersion(String cpicVersion) {
    m_cpicVersion = cpicVersion;
  }

  /**
   * The date this file should be considered to be last modified (should be manually set by curators)
   */
  public Date getModificationDate() {
    return m_modificationDate;
  }

  public void setModificationDate(Date modificationDate) {
    m_modificationDate = modificationDate;
  }

  /**
   * The symbol for the gene these alleles are on
   */
  public String getGeneSymbol() {
    return m_geneSymbol;
  }

  public void setGeneSymbol(String geneSymbol) {
    m_geneSymbol = geneSymbol;
  }

  /**
   * The orientation of the gene relative to the chromosome
   */
  public String getOrientation() {
    return m_orientation;
  }

  public void setOrientation(String orientation) {
    m_orientation = orientation;
  }

  /**
   * The name of the chromosome this translation is on.
   */
  public String getChromosome() {
    return m_chromosome;
  }

  public void setChromosome(String chromosome) {
    m_chromosome = chromosome;
  }

  /**
   * The human genome assembly (build) the positions in this translation are from (e.g. b38 or b37)
   */
  public String getGenomeBuild() {
    return m_genomeBuild;
  }

  public void setGenomeBuild(String genomeBuild) {
    m_genomeBuild = genomeBuild;
  }


  /**
   * The RefSeq identifier for the chromosome this translation is on (should agree with build).
   */
  public String getRefSeqChromosome() {
    return m_refSeqChromosome;
  }

  public void setRefSeqChromosome(String refSeqChromosome) {
    m_refSeqChromosome = refSeqChromosome;
  }


  /**
   * The {@link VariantLocus} objects used to define {@link NamedAllele}s in this translation
   */
  public VariantLocus[] getVariants() {
    return m_variants;
  }

  public void setVariants(VariantLocus[] variants) {
    m_variants = variants;
  }


  /**
   * All the named alleles defined in this translation
   */
  public SortedSet<NamedAllele> getNamedAlleles() {
    return m_namedAlleles;
  }

  public void setNamedAlleles(SortedSet<NamedAllele> namedAlleles) {
    m_namedAlleles = namedAlleles;
  }

  public void addNamedAllele(NamedAllele allele) {
    if (m_namedAlleles == null) {
      m_namedAlleles = new TreeSet<>();
    }
    m_namedAlleles.add(allele);
  }


  @Override
  public String toString() {
    return "Allele definition for " + m_geneSymbol;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DefinitionFile that)) {
      return false;
    }
    return Objects.equals(m_formatVersion, that.getFormatVersion()) &&
        Objects.equals(m_modificationDate, that.getModificationDate()) &&
        Objects.equals(m_geneSymbol, that.getGeneSymbol()) &&
        Objects.equals(m_orientation, that.getOrientation()) &&
        Objects.equals(m_chromosome, that.getChromosome()) &&
        Objects.equals(m_genomeBuild, that.getGenomeBuild()) &&
        Objects.equals(m_refSeqChromosome, that.getRefSeqChromosome()) &&
        Arrays.equals(m_variants, that.getVariants()) &&
        Objects.equals(m_namedAlleles, that.getNamedAlleles());
  }

  @Override
  public int hashCode() {
    return Objects.hash(m_formatVersion, m_modificationDate, m_geneSymbol, m_orientation,
        m_chromosome, m_genomeBuild, m_refSeqChromosome, Arrays.hashCode(m_variants), m_namedAlleles);
  }


  /**
   * Remove ignored positions specified in {@link DefinitionExemption}.
   * Should only be called during initial generation of this {@link DefinitionFile} by {@link DataManager}.
   */
  public void removeIgnoredPositions(DefinitionExemption exemption) {
    // find ignored positions
    Set<Integer> ignoredPositions = new HashSet<>();
    for (int x = 0; x < m_variants.length; x += 1) {
      if (exemption.shouldIgnorePosition(m_variants[x])) {
        ignoredPositions.add(x);
      }
    }
    if (exemption.getIgnoredPositions().size() != ignoredPositions.size()) {
      throw new IllegalStateException("Should have " + exemption.getIgnoredPositions().size() + " ignored positions, " +
          "but only found " + ignoredPositions.size());
    }

    // remove ignored positions
    VariantLocus[] newPositions = new VariantLocus[m_variants.length - ignoredPositions.size()];
    if (newPositions.length != m_variants.length - ignoredPositions.size()) {
      throw new IllegalStateException("Should have " + (m_variants.length - ignoredPositions.size()) +
          " positions, but ended up with " + newPositions.length);
    }

    // update variants
    setVariants(newPositions);

    SortedSet<NamedAllele> updatedNamedAlleles = new TreeSet<>();
    for (NamedAllele namedAllele : m_namedAlleles) {
      String[] alleles = new String[namedAllele.getAlleles().length - ignoredPositions.size()];
      String[] cpicAlleles = new String[namedAllele.getAlleles().length - ignoredPositions.size()];
      if (newPositions.length != alleles.length) {
        throw new IllegalStateException("Number of variants (" + newPositions.length + ") and number of alleles (" +
            alleles.length + ") don't match up for " + namedAllele.getName());
      }
      for (int x = 0, y = 0; x < namedAllele.getAlleles().length; x += 1) {
        if (!ignoredPositions.contains(x)) {
          alleles[y] = namedAllele.getAlleles()[x];
          cpicAlleles[y] = namedAllele.getCpicAlleles()[x];
          y += 1;
        }
      }
      // if there's nothing left that differs from reference allele then don't include the allele in output
      if (!Arrays.stream(alleles).allMatch(Objects::isNull)) {
        updatedNamedAlleles.add(new NamedAllele(namedAllele.getId(), namedAllele.getName(), alleles, cpicAlleles,
            namedAllele.isReference()));
      }
    }
    setNamedAlleles(updatedNamedAlleles);
  }


  /**
   * Makes sure that variants are sorted by position.
   */
  public void sortPositions() {

    VariantLocus[] sortedVariants = new VariantLocus[m_variants.length];
    System.arraycopy(m_variants, 0, sortedVariants, 0, m_variants.length);
    Arrays.sort(sortedVariants);
    if (Arrays.equals(sortedVariants, m_variants)) {
      return;
    }
    System.out.println("Sorting alleles for " + m_geneSymbol);
    for (NamedAllele namedAllele : m_namedAlleles) {
      namedAllele.updatePositions(m_variants, sortedVariants);
    }
    m_variants = sortedVariants;
  }
}
