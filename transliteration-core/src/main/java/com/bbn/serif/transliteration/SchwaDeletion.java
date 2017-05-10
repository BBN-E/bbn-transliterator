package com.bbn.serif.transliteration;

import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.strings.offsets.CharOffset;

import com.google.common.collect.ImmutableList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Many Indo-Aryan languages delete the final schwa of words.
 * Hindi also appears to do this word-medially in some cases but uroman doesn't appear to handle it,
 * and for the moment neither do we.
 */
enum SchwaDeletion implements DefaultTransliterator.TransliterationRuleBlock {
  INSTANCE;

  public static final int DEFAULT_SEQUENCE_NUMBER = 40000;

  private static final double SCHWA_DELETION_BONUS = 0.25;

  @Override
  public void applyToChart(final TransliterationChart chart) {
    final Script.ScriptMapping scriptMapping = chart.scriptMapping();

    chart.string().processCodePoints(new UnicodeFriendlyString.NoResultCodePointProcessor() {
      @Override
      public void processCodepoint(final UnicodeFriendlyString s, final CharOffset codePointOffset,
          final int codePoint) {
        if (scriptMapping.isDevanagari(codePointOffset)) {
          final CharOffset nextCharacter = codePointOffset.shiftedCopy(1);
          if (nextCharacter.asInt() < s.lengthInCodePoints()) {
            final boolean applySchwaDeletion = !scriptMapping.isDevanagari(nextCharacter)
                || isSchwaDeletionBoundary(chart, nextCharacter);
            if (applySchwaDeletion) {
              applySchwaDeletionToPosition(chart, codePointOffset);
            }
          }
        }
      }
    });
    if (!chart.string().isEmpty()) {
      final CharOffset lastCharacter = CharOffset.asCharOffset(chart.string().lengthInCodePoints() - 1);
      if (scriptMapping.isDevanagari(lastCharacter)) {
        applySchwaDeletionToPosition(chart, lastCharacter);
      }
    }
  }

  private static final Pattern CONSONANT_VOWEL_PATTERN = Pattern.compile(
      "^(.*[bcdfghjklmnpqrstvwxyz])([aeiou]+)$");
  private static final Pattern ENDS_WITH_VOWEL = Pattern.compile(".*[aeiou]$");

  private void applySchwaDeletionToPosition(final TransliterationChart chart,
      final CharOffset codePointOffset) {
    for (final ChartEdge precedingEdge : chart.edgesEndingAt(codePointOffset.asInt() + 1)) {
      final Matcher consonantVowelMatcher =
          CONSONANT_VOWEL_PATTERN.matcher(precedingEdge.spanTransliteration());
      if (consonantVowelMatcher.matches()) {
        final String vowel = consonantVowelMatcher.group(2);
        if (vowel.equals("a")) {
          for (final ChartEdge precedingPrecedingEdge : chart
              .edgesEndingAt(precedingEdge.startPosition())) {
            if (ENDS_WITH_VOWEL.matcher(precedingPrecedingEdge.spanTransliteration()).matches()) {
              final String consonantPart = consonantVowelMatcher.group(1);
              chart.addDerived(ImmutableList.of(precedingPrecedingEdge, precedingEdge),
                  new ChartEdge.Builder()
                      .startPosition(precedingPrecedingEdge.startPosition())
                      .endPosition(precedingEdge.endPosition())
                      .score(precedingPrecedingEdge.score() + precedingEdge.score() + SCHWA_DELETION_BONUS)
                      .spanTransliteration(precedingPrecedingEdge.spanTransliteration() + consonantPart)
                      .build(), "schwa-deletion");
            }
          }
        }
      }
    }
  }

  private static final Pattern ROMAN_LETTERS = Pattern.compile("[a-zA-Z]+");
  private boolean isSchwaDeletionBoundary(final TransliterationChart chart,
      final CharOffset codePointOffset) {
    for (final ChartEdge edge : chart.edgesIncluding(codePointOffset.asInt())) {
      if (ROMAN_LETTERS.matcher(edge.spanTransliteration()).find()) {
        return false;
      }
    }
    return true;
  }
}
