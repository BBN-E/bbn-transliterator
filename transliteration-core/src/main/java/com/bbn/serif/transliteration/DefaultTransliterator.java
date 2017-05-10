package com.bbn.serif.transliteration;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.collections.MapUtils;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Ordering;

import org.immutables.value.Value;

/**
 * Default implementation of a {@link Transliterator} which applies a sequence of
 * {@link TransliterationRuleBlock}s in the order given by their sequence numbers. In case of a
 * tie by sequence number, rule blocks are applied in the order they were supplied to the builder.
 */
@TextGroupImmutable
@Value.Immutable
abstract class DefaultTransliterator implements Transliterator {
  /**
   * Sequence number for steps which only add things to the chart without examining its contents
   * and which should be applid early in processing.
   */
  public static final int INDEPENDENT_INITIAL_STEP = 10000;

  abstract Script.CodePointToScriptMapper scriptMapper();
  abstract ImmutableMultimap<Integer, TransliterationRuleBlock> ruleBlocksBySequenceNumber();


  @Value.Derived
  ImmutableList<TransliterationRuleBlock> ruleBlocksInOrder() {
    return FluentIterable.from(MapUtils.<Integer, TransliterationRuleBlock>byKeyOrdering(Ordering.<Integer>natural())
            .immutableSortedCopy(ruleBlocksBySequenceNumber().entries()))
        .transform(MapUtils.<Integer, TransliterationRuleBlock>getEntryValue())
        .toList();
  }

  /**
   * Any transformation of the transliteration chart.
   */
  interface TransliterationRuleBlock {
    void applyToChart(TransliterationChart chart);
  }

  @Override
  public UnicodeFriendlyString transliterate(final UnicodeFriendlyString s) {
    final TransliterationChart chart =
        TransliterationChart.createForLength(s, scriptMapper().mapStringToScripts(s));

    for (final TransliterationRuleBlock ruleBlock : ruleBlocksInOrder()) {
      ruleBlock.applyToChart(chart);
    }

    return chart.bestDecoding().or(s);
  }

  static class Builder extends ImmutableDefaultTransliterator.Builder {

  }

}
