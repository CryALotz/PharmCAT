package org.pharmgkb.pharmcat.definition.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.ObjectUtils;
import org.pharmgkb.common.comparator.HaplotypeNameComparator;
import org.pharmgkb.pharmcat.haplotype.Iupac;


/**
 * A named allele (aka Haplotype, Star Allele, etc.).
 *
 * @author Ryan Whaley
 */
public class NamedAllele implements Comparable<NamedAllele> {
  @Expose
  @SerializedName("name")
  private final String m_name;
  @Expose
  @SerializedName("id")
  private final String m_id;
  @Expose
  @SerializedName("alleles")
  private final String[] m_alleles;
  @Expose
  @SerializedName("populationFrequency")
  private Map<String,String> m_popFreqMap;
  //-- variables after this point are used by NamedAlleleMatcher --//
  /** The set of positions that are missing from this copy of the NamedAllele **/
  private SortedSet<VariantLocus> m_missingPositions;
  // generated by initialize()
  private SortedMap<VariantLocus, String> m_alleleMap;
  private int m_score;
  private transient Pattern m_permutations;


  /**
   * Primary constructor.
   * Use this when reading in allele definitions.
   */
  public NamedAllele(String id, String name, String[] alleles) {
    this(id, name, alleles, Collections.emptySortedSet());
  }

  /**
   * Constructor for duplicating/modifying a {@link NamedAllele}.
   */
  public NamedAllele(String id, String name, String[] alleles,
      SortedSet<VariantLocus> missingPositions) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(name);
    Preconditions.checkNotNull(alleles);
    Preconditions.checkNotNull(missingPositions);
    m_id = id;
    m_name = name;
    m_alleles = alleles;
    m_missingPositions = missingPositions;
  }


  /**
   * Call this to initialize this object for use.
   */
  public void initialize(VariantLocus[] refVariants) {

    Preconditions.checkNotNull(refVariants);
    Preconditions.checkNotNull(m_alleles);
    Preconditions.checkState(refVariants.length == m_alleles.length);

    m_alleleMap = new TreeMap<>();
    for (int x = 0; x < refVariants.length; x += 1) {
      m_alleleMap.put(refVariants[x], m_alleles[x]);
      if (m_alleles[x] != null) {
        m_score++;
      }
    }
    calculatePermutations(refVariants);
  }

  /**
   * Call this to initialize this object for use.
   * This variant of {@link #initialize} allows an arbitrary {@code score} to be set.
   *
   * @param score the score for this {@link NamedAllele}
   */
  public void initialize(VariantLocus[] refVariants, int score) {

    initialize(refVariants);
    m_score = score;
  }


  /**
   * The name of this named allele (e.g. *1, Foo123Bar)
   */
  public String getName() {
    return m_name;
  }

  /**
   * The CPIC identifier for this named allele (e.g. CA10000.1)
   */
  public String getId() {
    return m_id;
  }


  /**
   * The array of alleles that define this allele.
   *
   * <em>Note:</em> use this in conjunction with {@link DefinitionFile#getVariants()} to get the name of the variant
   */
  public String[] getAlleles() {
    return m_alleles;
  }

  public String getAllele(VariantLocus variantLocus) {
    Preconditions.checkState(m_alleleMap != null, "This NamedAllele has not been initialized()");
    return m_alleleMap.get(variantLocus);
  }


  /**
   * Gets the score (the number of alleles that matched) for this allele if it is matched.
   * It is usually the same as the number of non-null alleles, but can be set to anything via
   * {@link #initialize(VariantLocus[], int)}.
   */
  public int getScore() {
    return m_score;
  }


  /**
   * Gets the positions that are missing from this NamedAllele.
   */
  public SortedSet<VariantLocus> getMissingPositions() {
    if (m_missingPositions == null) {
      // this is possible if marshalled via GSON
      m_missingPositions = Collections.emptySortedSet();
    }
    return m_missingPositions;
  }


  /**
   * A mapping of population name to allele frequency
   */
  public Map<String, String> getPopFreqMap() {
    return m_popFreqMap;
  }

  public void setPopFreqMap(Map<String, String> popFreqMap) {
    m_popFreqMap = popFreqMap;
  }


  @Override
  public String toString() {
    return m_name + " [" + m_id + "]";
  }

  @Override
  public int compareTo(NamedAllele o) {
    int rez = HaplotypeNameComparator.getComparator().compare(m_name, o.m_name);
    if (rez != 0) {
      return rez;
    }
    return ObjectUtils.compare(m_id, o.m_id);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NamedAllele)) {
      return false;
    }
    NamedAllele that = (NamedAllele)o;
    return Objects.equal(m_name, that.getName()) &&
        Objects.equal(m_id, that.getId()) &&
        Arrays.equals(m_alleles, that.getAlleles()) &&
        Objects.equal(m_popFreqMap, that.getPopFreqMap());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(m_name, m_id, m_alleles, m_popFreqMap);
  }



  //-- permutation code --//

  public Pattern getPermutations() {
    return m_permutations;
  }


  private void calculatePermutations(VariantLocus[] refVariants) {

    List<VariantLocus> sortedRefVariants = Arrays.stream(refVariants).sorted().collect(Collectors.toList());
    StringBuilder builder = new StringBuilder();
    for (VariantLocus variant : sortedRefVariants) {
      builder.append(variant.getVcfPosition())
          .append(":");
      String allele = m_alleleMap.get(variant);
      if (allele != null) {
        if (variant.getType() == VariantType.REPEAT) {
          allele = allele
              .replaceAll("\\(", "\\\\\\(")
              .replaceAll("\\)", "\\\\\\)");
        } else if (variant.getType() == VariantType.SNP && allele.length() == 1) {
          allele = Iupac.lookup(allele).getRegex();
        }
        builder.append(allele);
      } else {
        builder.append(".*?");
      }
      builder.append(";");
    }
    m_permutations = Pattern.compile(builder.toString());
  }
}
