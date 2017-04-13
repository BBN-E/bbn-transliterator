package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.strings.offsets.CharOffset;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules for dealing with Abugida writing systems ( https://en.wikipedia.org/wiki/Abugida ).
 */
enum AbugidaRules implements DefaultTransliterator.TransliterationRuleBlock {
  INSTANCE;

  public static final int DEFAULT_SEQUENCE_NUMBER = 30000;

  private static final double VIRAMA_SCORE = 2.0;
  private static final double ABUGIDA_SYLLABIC_BONUS = 0.5;
  private static final double SUBJOINED_BONUS = 0.25;
  private static final double NUKTA_BONUS = 0.25;

  private static final Pattern CONSONANT_VOWEL_PATTERN = Pattern.compile(
      "^(.*[bcdfghjklmnpqrstvwxyz])([aeiou]+)$");

  @Override
  public void applyToChart(final TransliterationChart chart) {
    final Script.ScriptMapping scriptMapping = chart.scriptMapping();
    chart.string().processCodePoints(new UnicodeFriendlyString.NoResultCodePointProcessor() {
      @Override
      public void processCodepoint(final UnicodeFriendlyString s, final CharOffset codePointOffset,
          final int codePoint) {

        boolean blockDefaultVowel = false;
        // nukta is used to indicate foreign sounds in Devanagari
        if (isNukta(codePoint)) {
          for (final ChartEdge precedingEdge : chart.edgesEndingAt(codePointOffset.asInt())) {
            chart.addExtended(precedingEdge, codePointOffset.asInt() + 1,
                precedingEdge.spanTransliteration(), precedingEdge.score() + NUKTA_BONUS,
                "nukta");
          }
          blockDefaultVowel = true;
        }

        // certain markers can replace the default vowel with themselves
        if (!scriptMapping.startsNewScript(codePointOffset) && isSubjoined(codePoint) ) {
          for (final ChartEdge precedingEdge : chart.edgesEndingAt(codePointOffset.asInt())) {
            final Matcher consonantVowelMatcher =
                CONSONANT_VOWEL_PATTERN.matcher(precedingEdge.spanTransliteration());
            if (consonantVowelMatcher.matches()) {
              final String vowel = consonantVowelMatcher.group(2);
              if (scriptMapping.isAbugidaVowelForSomeScript(codePointOffset, vowel)) {
                for (final ChartEdge curEdge : chart
                    .edgesFromTo(codePointOffset.asInt(), codePointOffset.asInt() + 1)) {
                  final String consonant = consonantVowelMatcher.group(1);
                  chart.addExtended(precedingEdge, codePointOffset.asInt() + 1,
                      consonant + curEdge.spanTransliteration(),
                      precedingEdge.score() + curEdge.score() + SUBJOINED_BONUS,
                      "subjoined");
                }
              }
            }
          }
          blockDefaultVowel = true;
        }

        // Virama indicates the default vowel should be suppressed
        if (isVirama(codePoint) && codePointOffset.asInt() > 0) {
          for (final ChartEdge edge : chart.edgesEndingAt(codePointOffset.asInt())) {
            final Matcher consonantVowelMatcher =
                CONSONANT_VOWEL_PATTERN.matcher(edge.spanTransliteration());
            if (consonantVowelMatcher.matches()) {
              final String vowel = consonantVowelMatcher.group(2);
              if (scriptMapping.isAbugidaVowelForSomeScript(codePointOffset, vowel)) {
                final String consonant = consonantVowelMatcher.group(1);
                chart.addExtended(edge, codePointOffset.asInt() + 1,
                    consonant, edge.score() + VIRAMA_SCORE, "suppress-default-vowel");
              }
            } else {
              chart.addExtended(edge, codePointOffset.asInt() + 1,
                  edge.spanTransliteration(), edge.score() + VIRAMA_SCORE, "no-op-virama");
            }
          }
          blockDefaultVowel = true;
        }

        if (!blockDefaultVowel) {
          // in this scripts, if not blocked, every syllable includes the default vowel
          for (final ChartEdge edge : chart.edgesEndingAt(codePointOffset.asInt() + 1)) {
            final Optional<String> currentPrimaryDefaultVowel =
                scriptMapping.primaryDefaultVowel(codePointOffset);

            if (currentPrimaryDefaultVowel.isPresent()
                && isLatinConsonant(edge.spanTransliteration())) {
              chart.addExtended(edge, codePointOffset.asInt() + 1,
                  edge.spanTransliteration() + currentPrimaryDefaultVowel.get(),
                  edge.score() + DEFAULT_VOWEL_INCREMENT,
                  "default-vowel");
            }
          }
        }

        // deal with certain syllabic consonants
        for (final ChartEdge edge : chart
            .edgesFromTo(codePointOffset.asInt(), codePointOffset.asInt() + 1)) {
          if (isAbugidaSyllabic(edge.spanTransliteration())
              && !scriptMapping.startsNewScript(codePointOffset)
              && scriptMapping.isAbugidaVowelForSomeScript(codePointOffset, "a")) {
            final String suffix = STRIP_INITIAL_PLUS.matcher(edge.spanTransliteration()).replaceAll("");
            for (final ChartEdge maybeVowelEdge : chart.edgesEndingAt(codePointOffset.asInt())) {
              if (endsWithVowel(maybeVowelEdge.spanTransliteration())) {
                chart.addMerged(maybeVowelEdge, edge, maybeVowelEdge.spanTransliteration() + suffix,
                    maybeVowelEdge.score() + edge.score() + ABUGIDA_SYLLABIC_BONUS, "syllable-end-consonant");
              } else {
                chart.addMerged(maybeVowelEdge, edge,
                    maybeVowelEdge.spanTransliteration() + "a" + suffix,
                    maybeVowelEdge.score() + edge.score() + ABUGIDA_SYLLABIC_BONUS, "syllable-end-consonant-with-default-vowel");
              }
            }
          }
        }
      }
    });
  }

  private boolean isNukta(final int codePoint) {
    final String charName = Character.getName(codePoint);
    return charName != null && charName.contains("SIGN NUKTA");
  }

  private static final Pattern SUBJOINED_PATTERN = Pattern.compile(
      "\\b(?:SUBJOINED LETTER|VOWEL SIGN|AU LENGTH MARK|EMPHASIS MARK|CONSONANT SIGN|SIGN VIRAMA"
          + "|SIGN PAMAAEH|SIGN COENG|SIGN ASAT|SIGN ANUSVARA|SIGN ANUSVARAYA|SIGN BINDI|TIPPI"
          + "|SIGN NIKAHIT|SIGN CANDRABINDU|SIGN VISARGA|SIGN REAHMUK|SIGN DOT BELOW"
          + "|ARABIC (?:DAMMA|DAMMATAN|FATHA|FATHATAN|HAMZA|KASRA|KASRATAN|MADDAH|SHADDA"
          + "|SUKUN))\\b");

  private boolean isSubjoined(final int codePoint) {
    final String charName = Character.getName(codePoint);
    return charName != null && SUBJOINED_PATTERN.matcher(charName).find();
  }

  private static final Pattern IS_LATIN_CONSONANT = Pattern.compile("^[bcdfghjklmnpqrstvwxyz]+$");

  private boolean isLatinConsonant(final String s) {
    return IS_LATIN_CONSONANT.matcher(s).matches();
  }

  private static final double DEFAULT_VOWEL_INCREMENT = 0.25;

  private boolean endsWithVowel(final String s) {
    if (s.isEmpty()) {
      return false;
    }
    switch (s.charAt(s.length()-1)) {
      case 'a':
      case 'e':
      case 'i':
      case 'o':
      case 'u':
        return true;
      default:
        return false;
    }
  }

  private static final Pattern STRIP_INITIAL_PLUS = Pattern.compile("^\\+");

  private static final ImmutableSet<String> ABUGIDA_SYLLABICS = ImmutableSet.of("+H", "+M",
      "+N", "+NG", "+h", "+m", "+n", "+ng");
  private static boolean isAbugidaSyllabic(final String s) {
    return ABUGIDA_SYLLABICS.contains(s);
  }

  private static final ImmutableSet<String> VIRAMA_WORDS = ImmutableSet.of(
      "VIRAMA", "AL-LAKUNA", "ASAT", "COENG", "PAMAAEH");
  private static final Pattern VIRAMA_PATTERN = Pattern.compile(
      "\\bSIGN (?:" + StringUtils.pipeJoiner().join(VIRAMA_WORDS) + ")\\b");

  private boolean isVirama(final int codePoint) {
    final String charName = Character.getName(codePoint);
    return charName != null && VIRAMA_PATTERN.matcher(charName).find();
  }
}
