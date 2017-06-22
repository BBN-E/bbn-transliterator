package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringUtils;

/**
 * To ensure it is always possible to find a path through the chart, we can add low scoring
 * edges which simply copy the input characters without transliteration.
 */
enum BackoffTransliterationRules implements DefaultTransliterator.TransliterationRuleBlock {
  INSTANCE;

  // this should generally be the last rule applied, so we give it a sequence number close to the end,
  // but we leave a little room in case the user wants to add something else
  public static final int DEFAULT_SEQUENCE_NUMBER = Integer.MAX_VALUE - 100000;

  private static final double IDENTITY_TRANSLITERATION_SCORE = 0.1;

  @Override
  public void applyToChart(TransliterationChart chart) {
    for (int codePointPos = 0, codeUnitPos = 0;
         codeUnitPos < chart.string().utf16CodeUnits().length(); ++codePointPos) {
      final int codePoint = Character.codePointAt(chart.string().utf16CodeUnits(), codeUnitPos);
      chart.addEdge(new ChartEdge.Builder()
          .startPosition(codePointPos)
          .endPosition(codePointPos + 1)
          .spanTransliteration(StringUtils.codepointToString(codePoint))
          .score(IDENTITY_TRANSLITERATION_SCORE).build(), "backoff-identity");
      codeUnitPos += Character.charCount(codePoint);
    }
  }
}
