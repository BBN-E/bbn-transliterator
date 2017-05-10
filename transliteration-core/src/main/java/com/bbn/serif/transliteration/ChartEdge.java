package com.bbn.serif.transliteration;

import com.bbn.bue.common.TextGroupImmutable;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents an edge in a transliteration decoding chart.  Each chart edge means that
 * "the characters from position {@link #startPosition()} to position {@link #endPosition()}
 * should be transformed to the string {@link #spanTransliteration()}.  Since there may be
 * multiple possible transliterations, chart edges carry scores. Higher is better.
 *
 * Positions are defined as points between characters. So position 0 precedes the first character,
 * position 1 is between the first and second characters, etc.
 */
@Value.Immutable
@TextGroupImmutable
@Functional
abstract class ChartEdge implements WithChartEdge {
  public abstract int startPosition();
  public abstract int endPosition();
  public abstract String spanTransliteration();
  public abstract double score();

  @Value.Check
  protected void check() {
    checkArgument(endPosition() > startPosition());
  }

  public static class Builder extends ImmutableChartEdge.Builder {}
}
