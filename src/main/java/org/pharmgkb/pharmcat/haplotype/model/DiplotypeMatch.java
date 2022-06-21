package org.pharmgkb.pharmcat.haplotype.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.google.common.base.Preconditions;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.ObjectUtils;
import org.pharmgkb.pharmcat.haplotype.MatchData;


/**
 * This represents a diplotype and the sequences that matched it.
 *
 * @author Mark Woon
 */
public class DiplotypeMatch implements Comparable<DiplotypeMatch> {
  @Expose
  @SerializedName("name")
  private final String m_name;
  @Expose
  @SerializedName("haplotype1")
  private final BaseMatch m_haplotype1;
  @Expose
  @SerializedName("haplotype2")
  private final BaseMatch m_haplotype2;
  @Expose
  @SerializedName("score")
  private int m_score;
  private final Set<String[]> m_sequences = new HashSet<>();
  private final MatchData m_dataset;



  public DiplotypeMatch(BaseMatch hm1, BaseMatch hm2, MatchData dataset) {
    BaseMatch[] sortedHaps = new BaseMatch[] {hm1, hm2};
    Arrays.sort(sortedHaps);
    m_haplotype1 = sortedHaps[0];
    m_haplotype2 = sortedHaps[1];
    m_name = m_haplotype1.getName() + "/" + m_haplotype2.getName();
    m_score = m_haplotype1.getHaplotype().getScore() + m_haplotype2.getHaplotype().getScore();
    m_dataset = dataset;
  }

  public String getName() {
    return m_name;
  }

  public int getScore() {
    return m_score;
  }

  public void setScore(int score) {
    m_score = score;
  }

  public BaseMatch getHaplotype1() {
    return m_haplotype1;
  }

  public BaseMatch getHaplotype2() {
    return m_haplotype2;
  }

  public Set<String[]> getSequences() {
    return m_sequences;
  }

  public void addSequencePair(String[] pair) {
    Preconditions.checkNotNull(pair);
    Preconditions.checkArgument(pair.length == 2, "Sequence pair must have 2 sequences");
    m_sequences.add(pair);
  }

  public MatchData getDataset() {
    return m_dataset;
  }


  @Override
  public String toString() {
    return m_name;
  }

  @Override
  public int compareTo(DiplotypeMatch o) {

    int rez = ObjectUtils.compare(o.getScore(), m_score);
    if (rez != 0) {
      return rez;
    }
    rez = ObjectUtils.compare(m_haplotype1, o.getHaplotype1());
    if (rez != 0) {
      return rez;
    }
    return ObjectUtils.compare(m_haplotype2, o.getHaplotype2());
  }
}
