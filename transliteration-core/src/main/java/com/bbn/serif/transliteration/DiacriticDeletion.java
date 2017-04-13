package com.bbn.serif.transliteration;

import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.strings.offsets.CharOffset;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import java.util.regex.Pattern;

/**
 * Many diacritic marks can simply be deleted.
 */
enum DiacriticDeletion implements DefaultTransliterator.TransliterationRuleBlock {
  INSTANCE;

  public static final int DEFAULT_SEQUENCE_NUMBER = 20000;

  @Override
  public void applyToChart(final TransliterationChart chart) {
    chart.string().processCodePoints(new UnicodeFriendlyString.NoResultCodePointProcessor() {
      @Override
      public void processCodepoint(final UnicodeFriendlyString s, final CharOffset codePointOffset,
          final int codePoint) {
        if (isDiacriticToDelete(codePoint)) {
          chart.addEdge(new ChartEdge.Builder().startPosition(codePointOffset.asInt())
              .endPosition(codePointOffset.asInt() + 1)
              .spanTransliteration("")
              .score(DELETE_DIACRITIC_SCORE).build(), DELETE_DIACRITIC_DERIVATION);
        }
      }
    });
  }

  private static final double DELETE_DIACRITIC_SCORE = 1.0;
  private static final String DELETE_DIACRITIC_DERIVATION = "delete diacritic";

  private static final ImmutableSet<String> DIACRITIC_WORDS = ImmutableSet.of("ACCENT",
      "TONE", "COMBINING DIAERESIS", "COMBINING DIAERESIS BELOW", "COMBINING MACRON",
      "COMBINING VERTICAL LINE ABOVE", "COMBINING DOT ABOVE RIGHT", "COMBINING TILDE",
      "COMBINING CYRILLIC", "MUUSIKATOAN", "TRIISAP");
  private static final Joiner OR_JOINER = Joiner.on("|");
  private static final Pattern DIACRITIC_PATTERN = Pattern.compile("\\b("
      + OR_JOINER.join(DIACRITIC_WORDS) + ")\\b");

  private boolean isDiacriticToDelete(final int codePoint) {
    if (Character.getType(codePoint) == Character.NON_SPACING_MARK) {
      final String codePointName = Character.getName(codePoint);
      return codePointName != null && DIACRITIC_PATTERN.matcher(codePointName).find();
    } else {
      return false;
    }
  }
}
