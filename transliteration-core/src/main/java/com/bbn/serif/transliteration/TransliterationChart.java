package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.scoring.Scored;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A "chart" for transliteration.  This allows representation of alternative transliterations for
 * various spans of the original text and determining a "best transliteration" based on these.
 *
 * "Positions" in the chart are between character, so the first source character is between
 * positions zero and one.  Therefore an edge always corresponds to one or more source characters.
 *
 * This object is mutable.
 */
final class TransliterationChart {

  private final UnicodeFriendlyString string;
  private final Script.ScriptMapping scriptMapping;

  /**
   * how did this edge come to be? never iterated, for debugging purposes only
   */
  private final Map<ChartEdge, String> derivations = new HashMap<>();
  private final List<ChartEdge> allEdges = new ArrayList<>();
  // we index the edges in various ways for convenience when writing transliteration rules
  private final ListMultimap<Integer, ChartEdge> edgesStartingAt = ArrayListMultimap.create();
  private final ListMultimap<Integer, ChartEdge> edgesEndingAt = ArrayListMultimap.create();
  private final ListMultimap<Integer, ChartEdge> edgesIncluding = ArrayListMultimap.create();


  private TransliterationChart(UnicodeFriendlyString string, Script.ScriptMapping scriptMapping) {
    this.string = checkNotNull(string);
    this.scriptMapping = checkNotNull(scriptMapping);
    checkArgument(scriptMapping.string().equals(string));
  }

  /**
   * Creates a chart for transliterating {@code s}.
   */
  public static TransliterationChart createForLength(UnicodeFriendlyString s,
      Script.ScriptMapping scriptMapping) {
    return new TransliterationChart(s, scriptMapping);
  }

  public UnicodeFriendlyString string() {
    return string;
  }

  public Script.ScriptMapping scriptMapping() {
    return scriptMapping;
  }

  /**
   * Adds a possible transliteration of the characters covered by {@code edge}.
   *
   * @param derivation why the edge was added
   */
  public void addEdge(ChartEdge edge, String derivation) {
    checkArgument(edge.endPosition() <= string.lengthInCodePoints());
    allEdges.add(edge);
    edgesStartingAt.put(edge.startPosition(), edge);
    edgesEndingAt.put(edge.endPosition(), edge);
    for (int i = edge.startPosition(); i<edge.endPosition(); ++i) {
      edgesIncluding.put(i, edge);
    }
    derivations.put(edge, derivation);
  }

  /**
   * Gets all edges starting at {@code startPosition}
   */
  public ImmutableList<ChartEdge> edgesStartingAt(final int startPosition) {
    return ImmutableList.copyOf(edgesStartingAt.get(startPosition));
  }

  /**
   * Gets all edges ending at {@code endPosition}
   */
  public ImmutableList<ChartEdge> edgesEndingAt(int endPosition) {
    // we make an immutable copy to prevent ConcurrentModificationExceptions
    return ImmutableList.copyOf(edgesEndingAt.get(endPosition));
  }

  /**
   * Gets all edges starting at {@code start} and ending at {@code end}.
   */
  public ImmutableList<ChartEdge> edgesFromTo(final int start, final int end) {
    checkArgument(start >= 0);
    checkArgument(end > start);

    final ImmutableList.Builder<ChartEdge> ret = ImmutableList.builder();
    for (final ChartEdge chartEdge : edgesStartingAt.get(start)) {
      if (chartEdge.endPosition() == end) {
        ret.add(chartEdge);
      }
    }
    return ret.build();
  }

  /**
   * Gets all edges which start at or before {@code position} and end after {@code position}.
   * This means they transliterate the character at {@code position} in the source string.
   */
  public ImmutableList<ChartEdge> edgesIncluding(final int position) {
    return ImmutableList.copyOf(edgesIncluding.get(position));
  }

  /**
   * Makes a new chart edge which starts at the start position of the existing edge {@code left}
   * and extends to a new position {@code newEndPosition} which is at or beyond {@code left}'s
   * end position. The new edge will have its own transliteration {@code newTransliteration}
   * and score {@code newScore}. The resulting derivation will be a combination of
   * {@code left}'s derivation and {@code reason}.
   *
   * This is provided for more convenient derivation tracking than making {@link ChartEdge}s
   * and calling {@link #addEdge(ChartEdge, String)} by hand.
   */
  public void addExtended(final ChartEdge left, final int newEndPosition,
      final String newTransliteration,
      final double newScore, final String reason) {
    checkArgument(newEndPosition >= left.endPosition());

    addEdge(new ChartEdge.Builder()
        .startPosition(left.startPosition())
        .endPosition(newEndPosition)
        .spanTransliteration(newTransliteration)
        .score(newScore)
        .build(), extendReason(left, reason));
  }

  /**
   * Takes two adjacent {@link ChartEdge}s and makes a new {@link ChartEdge} covering the union
   * of their source positions. The new edge will have its own transliteration
   * {@code combinedTransliteration} and score {@code newScore}. The resulting derivation will be a
   * combination of original edge derivations and {@code reason}.
   *
   * This is provided for more convenient derivation tracking than making {@link ChartEdge}s
   * and calling {@link #addEdge(ChartEdge, String)} by hand.
   */
  public void addMerged(final ChartEdge left, final ChartEdge right,
      final String combinedTransliteration, final double score, String reason) {
    checkArgument(left.endPosition() == right.startPosition());

    if (derivations.containsKey(left)) {
      reason += " [left:" + derivations.get(left) + "]";
    }

    if (derivations.containsKey(right)) {
      reason += "[right: " + derivations.get(right) + "]";
    }

    addEdge(new ChartEdge.Builder()
        .startPosition(left.startPosition())
        .endPosition(right.endPosition())
        .spanTransliteration(combinedTransliteration)
        .score(score)
        .build(), reason);
  }

  /**
   * Adds an edge to the chart while tracking for derivation purposes that it came from
   * {@code sourceEdges}.
   */
  public void addDerived(final Iterable<ChartEdge> sourceEdges, final ChartEdge newEdge,
      final String derivationReason) {
    addEdge(newEdge, extendReason(sourceEdges, derivationReason));
  }

  // warnings suppressed because IntelliJ worries getRowKey() and getColumnKey() may return null,
  // but we know they won't because we populate them

  /**
   * Gets the highest scoring decoding of this chart.  If no decoding can be found (i.e. there is
   * no path from the start position to the end position), {@link Optional#absent()} is returned.
   * We typically avoid this by adding low-scoring backoff edges.
   *
   * Higher scores are considered better.
   */
  @SuppressWarnings("ConstantConditions")
  public Optional<UnicodeFriendlyString> bestDecoding() {
    // backpointers - what is the most recent edge on our best path up to the given position?
    final List<Scored<ChartEdge>> bestStepToPosition = new ArrayList<>();
    for (int i = 0; i<=string.lengthInCodePoints(); ++i) {
      bestStepToPosition.add(Scored.<ChartEdge>from(null, i == 0 ? 0 : Double.NEGATIVE_INFINITY));
    }

    for (final ChartEdge edge : EARLIER_THEN_SHORTER.immutableSortedCopy(allEdges)) {
      final double pathScore = bestStepToPosition.get(edge.startPosition()).score()
          + edge.score();
      if (bestStepToPosition.get(edge.endPosition()).score() < pathScore) {
        bestStepToPosition.set(edge.endPosition(), Scored.from(edge, pathScore));
      }
    }

    final Scored<ChartEdge> finalStep = bestStepToPosition.get(string.lengthInCodePoints());
    if (finalStep.score() > Double.NEGATIVE_INFINITY) {
      final List<String> parts = new ArrayList<>();
      Scored<ChartEdge> curStep = finalStep;
      try {
        while (curStep.item().startPosition() > 0) {
          parts.add(curStep.item().spanTransliteration());
          curStep = bestStepToPosition.get(curStep.item().startPosition());
        }
      }catch(NullPointerException exc){
        // In certain cases curStep.item().startPosition() will result in a NullPointerException.
        // This is dependent on the input file, but the cause has not yet been identified. Until
        // the problem has been discovered, this catch acts as a sort of hacked together solution.
        return Optional.absent();
      }
      parts.add(curStep.item().spanTransliteration());

      Collections.reverse(parts);

      final StringBuilder sb = new StringBuilder();
      for (final String part : parts) {
        sb.append(part);
      }
      return Optional.of(StringUtils.unicodeFriendly(sb.toString()));
    } else {
      return Optional.absent();
    }
  }

  // private implementation

  private static final Ordering<ChartEdge> EARLIER_THEN_SHORTER = Ordering.natural()
      .onResultOf(ChartEdgeFunctions.startPosition())
      .compound(Ordering.natural().onResultOf(ChartEdgeFunctions.endPosition()));


  private String extendReason(ChartEdge baseEdge, String derivationReason) {
    return extendReason(ImmutableList.of(baseEdge), derivationReason);
  }

  private String extendReason(Iterable<ChartEdge> baseEdges, String derivationReason) {
    final StringBuilder ret = new StringBuilder();
    ret.append(derivationReason);
    for (final ChartEdge baseEdge : baseEdges) {
      final String baseEdgeDerivation = derivations.get(baseEdge);
      if (baseEdgeDerivation != null) {
        ret.append(" [left: ").append(baseEdgeDerivation) .append("]");
      }
    }
    return ret.toString();
  }
}
