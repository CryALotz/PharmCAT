package org.pharmgkb.pharmcat.haplotype.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.pharmgkb.common.comparator.HaplotypeNameComparator;
import org.pharmgkb.pharmcat.definition.model.NamedAllele;
import org.pharmgkb.pharmcat.definition.model.VariantLocus;
import org.pharmgkb.pharmcat.haplotype.MatchData;


/**
 * This is the base class for a {@link NamedAllele} match on a single strand.
 *
 * @author Mark Woon
 */
public class BaseMatch implements Comparable<BaseMatch> {
  @Expose
  @SerializedName("name")
  private String m_name;
  @Expose
  @SerializedName("haplotype")
  private NamedAllele m_haplotype;
  @Expose
  @SerializedName("sequences")
  private final SortedSet<String> m_sequences = new TreeSet<>();


  public String getName() {
    return m_name;
  }

  void setName(String name) {
    m_name = name;
  }


  public NamedAllele getHaplotype() {
    return m_haplotype;
  }

  void setHaplotype(NamedAllele haplotype) {
    m_haplotype = haplotype;
  }


  /**
   * Gets sequences of sample alleles that match this haplotype.
   * May have multiple matches due to unphased data.
   */
  public SortedSet<String> getSequences() {
    return m_sequences;
  }

  public void addSequence(String seq) {
    m_sequences.add(seq);
  }



  /**
   * Checks if haplotype matches reference or has partials.
   * Only applicaable when working with combinations and partials.
   */
  public void finalizeCombinationHaplotype(MatchData matchData) {
    if (getSequences().size() > 1) {
      throw new IllegalStateException("Can only finalize if there is only 1 sequence");
    }

    SortedMap<Long, String> alleleMap = new TreeMap<>();
    VariantLocus[] refVariants = matchData.getPositions();
    String sequence = getSequences().first();
    for (int x = 0; x < refVariants.length; x += 1) {
      alleleMap.put(refVariants[x].getPosition(), matchData.getAllele(sequence, x));
    }

    List<String> partials = new ArrayList<>();
    NamedAllele hap = getHaplotype();
    for (int x = 0; x < hap.getAlleles().length; x += 1) {
      long pos = refVariants[x].getPosition();
      if (hap.getAlleles()[x] == null) {
        if (!refVariants[x].getRef().equals(alleleMap.get(pos))) {
          VariantLocus vl = refVariants[x];
          partials.add(vl.getHgvsForVcfAllele(alleleMap.get(pos)));
        }
      }
    }
    if (partials.size() > 0) {
      if (hap.isReference()) {
        throw new IllegalStateException("Cannot create partial based on reference!");
      }

      StringBuilder builder = new StringBuilder()
          .append(getName());
      for (String partial : partials) {
        builder.append(CombinationMatch.COMBINATION_JOINER)
            .append(partial);
      }
      setName(builder.toString());

      NamedAllele partialHap = new NamedAllele(hap.getId(), builder.toString(), hap.getAlleles(),
          hap.getCpicAlleles(), hap.getMissingPositions(), false, hap.getNumCombinations(), partials.size());
      partialHap.setPopFreqMap(hap.getPopFreqMap());
      partialHap.initialize(refVariants);
      m_haplotype = partialHap;

    } else {
      NamedAllele newHap = new NamedAllele(hap.getId(), hap.getName(), hap.getAlleles(),
          hap.getCpicAlleles(), hap.getMissingPositions(), hap.isReference(), hap.getNumCombinations(), 0);
      newHap.setPopFreqMap(hap.getPopFreqMap());
      newHap.initialize(refVariants);
      m_haplotype = newHap;
    }
  }


  @Override
  public int compareTo(BaseMatch o) {
    String name1 = getName();
    String name2 = o.getName();
    if (name1.equals(name2)) {
      return 0;
    }

    Iterator<String> it1 = CombinationMatch.COMBINATION_NAME_SPLITTER.splitToList(name1).iterator();
    Iterator<String> it2 = CombinationMatch.COMBINATION_NAME_SPLITTER.splitToList(name2).iterator();
    while (it1.hasNext()) {
      String n1 = it1.next();
      if (it2.hasNext()) {
        String n2 = it2.next();
        int rez = HaplotypeNameComparator.getComparator().compare(n1, n2);
        if (rez != 0) {
          return rez;
        }
      } else {
        return 1;
      }
    }
    if (it2.hasNext()) {
      return -1;
    }

    return compareSequences(o);
  }

  int compareSequences(BaseMatch o) {

    int rez = Integer.compare(getSequences().size(), o.getSequences().size());
    if (rez != 0) {
      return rez;
    }
    Iterator<String> it1 = getSequences().iterator();
    Iterator<String> it2 = o.getSequences().iterator();
    while (it1.hasNext()) {
      String n1 = it1.next();
      String n2 = it2.next();
      rez = n1.compareTo(n2);
      if (rez != 0) {
        return rez;
      }
    }
    return 0;
  }


  @Override
  public String toString() {
    return getName();
  }
}
