package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.strings.offsets.CharOffset;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transliterate source characters by looking up their Unicode names and
 * applying various transformations.
 */
enum TransliterateByUnicodeCharacterName implements DefaultTransliterator.TransliterationRuleBlock {
  INSTANCE;

  private static final Logger log =
      LoggerFactory.getLogger(TransliterateByUnicodeCharacterName.class);

  final LoadingCache<Integer, Optional<String>> cache = CacheBuilder.newBuilder()
      .build(new CacheLoader<Integer, Optional<String>>() {
        @Override
        public Optional<String> load(final Integer codepoint) throws Exception {
          return codepointToStringInternal(codepoint);
        }
      });

  @Override
  public void applyToChart(final TransliterationChart chart) {
    chart.string().processCodePoints(new UnicodeFriendlyString.NoResultCodePointProcessor() {
      @Override
      public void processCodepoint(final UnicodeFriendlyString s, final CharOffset codePointOffset,
          final int codePoint) {
        if (Character.isLetter(codePoint) || Character.isDigit(codePoint)
            || Character.isIdeographic(codePoint) || isPunctuation(codePoint) || isOtherHandled(
            codePoint)) {
          final Optional<String> codepointTransliteration = codepointToString(codePoint);

          if (codepointTransliteration.isPresent()) {
            chart.addEdge(new ChartEdge.Builder().startPosition(codePointOffset.asInt())
                .endPosition(codePointOffset.asInt() + 1)
                .spanTransliteration(codepointTransliteration.get())
                .score(CHARACTER_NAME_SCORE).build(), DERIVED_BY_CHARACTER_NAME);
          }
        }
      }
    });
  }

  static boolean isPunctuation(int codepoint) {
    switch (Character.getType(codepoint)) {
      case Character.CONNECTOR_PUNCTUATION:
      case Character.ENCLOSING_MARK:
      case Character.END_PUNCTUATION:
      case Character.FINAL_QUOTE_PUNCTUATION:
      case Character.INITIAL_QUOTE_PUNCTUATION:
      case Character.START_PUNCTUATION:
      case Character.OTHER_PUNCTUATION:
        return true;
      default:
        return false;
    }
  }

  private boolean isOtherHandled(int codePoint) {
    if (isPunctuation(codePoint)) {
      return true;
    }

    switch (Character.getType(codePoint)) {
      case Character.FORMAT:
      case Character.COMBINING_SPACING_MARK:
      case Character.NON_SPACING_MARK:
        return true;
      default:
        return false;
    }
  }

  private static final double CHARACTER_NAME_SCORE = 1.0;
  private static final String DERIVED_BY_CHARACTER_NAME = "by character name";


  public Optional<String> codepointToString(int codepoint) {
    try {
      return cache.get(codepoint);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Joiner OR_JOINER = Joiner.on("|");

  private static final ImmutableSet<String> DELETE_UP_TO_AND_INCLUDING_FROM_BEGINNING =
      ImmutableSet.of("LETTER", "SYLLABLE", "SYLLABICS", "LIGATURE", "VOWEL SIGN",
          "CONSONANT SIGN", "CONSONANT", "VOWEL");
  private static final Pattern DELETE_UP_TO_AND_INCLUDING_FROM_BEGINNING_PATTERN =
      Pattern.compile("^.* (" + OR_JOINER.join(DELETE_UP_TO_AND_INCLUDING_FROM_BEGINNING)
          + ")\\s+");

  private static final Pattern DELETE_SUFFIX_AND_ALL_FOLLOWING_PATTERN =
      Pattern.compile(" (WITH|WITHOUT) .*");

  private static final ImmutableSet<String> PHRASES_TO_DELETE =
      ImmutableSet.<String>builder()
          .add("ABOVE").add("AGUNG").add("BAR").add("BARREE").add("BELOW").add("CEDILLA")
          .add("CEREK").add("DIGRAPH").add("DOACHASHMEE").add("FINAL FORM").add("GHUNNA")
          .add("GOAL")
          .add("INITIAL FORM").add("ISOLATED FORM").add("KAWI").add("LELET").add("LELET RASWADI")
          .add("LONSUM").add("MAHAPRANA").add("MEDIAL FORM").add("MURDA").add("MURDA MAHAPRANA")
          .add("REVERSED").add("ROTUNDA").add("SASAK").add("SUNG").add("TAM").add("TEDUNG")
          .add("TYPE ONE").add("TYPE TWO").add("WOLOSO").build();
  private static final Pattern PHRASES_TO_DELETE_PATTERN = Pattern.compile("\\s+ ("
      + PHRASES_TO_DELETE + ")\\s*");

  private static final ImmutableSet<String> DELETE_UP_TO_AND_INCLUDING_AGAIN =
      ImmutableSet.<String>builder()
          .add("ABKHASIAN").add("ACADEMY").add("AFRICAN").add("AIVILIK").add("AITON")
          .add("AKHMIMIC").add("ALEUT").add("ALI GALI")
          .add("ALPAPRAANA").add("ALTERNATE").add("ALTERNATIVE").add("AMBA").add("ARABIC")
          .add("ARCHAIC").add("ASPIRATED")
          .add("ATHAPASCAN").add("BASELINE").add("BLACKLETTER").add("BARRED").add("BASHKIR")
          .add("BERBER").add("BHATTIPROLU")
          .add("BIBLE-CREE").add("BIG").add("BINOCULAR").add("BLACKFOOT").add("BLENDED")
          .add("BOTTOM").add("BROAD").add("BROKEN")
          .add("CANDRA").add("CAPITAL").add("CARRIER").add("CHILLU").add("CLOSE").add("CLOSED")
          .add("COPTIC").add("CROSSED")
          .add("CRYPTOGRAMMIC").add("CURLY").add("CYRILLIC").add("DANTAJA").add("DENTAL")
          .add("DIALECT-P").add("DIAERESIZED")
          .add("DOTLESS").add("DOUBLE").add("DOUBLE-STRUCK").add("EASTERN PWO KAREN")
          .add("EGYPTOLOGICAL").add("FARSI")
          .add("FINAL").add("FLATTENED").add("GLOTTAL").add("GREAT").add("GREEK").add("HALF")
          .add("HIGH").add("INITIAL").add("INSULAR")
          .add("INVERTED").add("IOTIFIED").add("JONA").add("KANTAJA").add("KASHMIRI")
          .add("KHAKASSIAN").add("KHAMTI").add("KHANDA")
          .add("KIRGHIZ").add("KOMI").add("L-SHAPED").add("LATINATE").add("LITTLE").add("LONG")
          .add("LOOPED").add("LOW")
          .add("MAHAAPRAANA").add("MANCHU").add("MANDAILING").add("MATHEMATICAL").add("MEDIAL")
          .add("MIDDLE-WELSH")
          .add("MON").add("MONOCULAR").add("MOOSE-CREE").add("MULTIOCULAR").add("MUURDHAJA")
          .add("N-CREE").add("NASKAPI")
          .add("NDOLE").add("NEUTRAL").add("NIKOLSBURG").add("NORTHERN").add("NUBIAN")
          .add("NUNAVIK").add("NUNAVUT").add("OJIBWAY")
          .add("OLD").add("OPEN").add("ORKHON").add("OVERLONG").add("PERSIAN").add("PHARYNGEAL")
          .add("PRISHTHAMATRA")
          .add("R-CREE").add("REDUPLICATION").add("REVERSED").add("ROMANIAN").add("ROUND")
          .add("ROUNDED").add("RUDIMENTA")
          .add("RUMAI PALAUNG").add("SANYAKA").add("SARA").add("SAYISI").add("SCRIPT")
          .add("SEBATBEIT").add("SEMISOFT").add("SGAW KAREN")
          .add("SHAN").add("SHARP").add("SHWE PALAUNG").add("SHORT").add("SIBE").add("SIDEWAYS")
          .add("SIMALUNGUN").add("SMALL").add("SOGDIAN")
          .add("SOFT").add("SOUTH-SLAVEY").add("SOUTHERN").add("SPIDERY").add("STIRRUP")
          .add("STRAIGHT").add("STRETCHED").add("SUBSCRIPT")
          .add("SWASH").add("TAILING").add("TAILED").add("TAILLESS").add("TAALUJA").add("TH-CREE")
          .add("TALL").add("TURNED")
          .add("TODO").add("TOP").add("TROKUTASTI").add("TUAREG").add("UKRAINIAN").add("VISIGOTHIC")
          .add("VOCALIC").add("VOICED")
          .add("VOICELESS").add("VOLAPUK").add("WAVY").add("WESTERN PWO KAREN").add("WEST-CREE")
          .add("WESTERN").add("WIDE")
          .add("WOODS-CREE").add("Y-CREE").add("YENISEI").add("YIDDISH").build();
  private static final Pattern DELETE_UP_TO_AND_INCLUDING_AGAIN_PATTERN =
      Pattern.compile("^.*\\b(" + OR_JOINER.join(DELETE_UP_TO_AND_INCLUDING_AGAIN) + ")\\s+");

  private static final Range<Integer> THAI_CODEPOINTS = Range.closed(3585, 3675);
  private static final Range<Integer> THAI_CONSONTANTS = Range.closed(3585, 3630);
  private static final Pattern STRIP_THAI_CHARACTER = Pattern.compile("^THAI CHARACTER\\s+");
  private static final Pattern STRIP_THAI_CONSONANT = Pattern.compile("^([^AEIOU]*).*");
  private static final Pattern THAI_VOWEL = Pattern.compile("^SARA [AEIOU]");

  private static final ImmutableSet<String> LOWERCASE_TRIGGERS = ImmutableSet.of(
      "HIRAGANA LETTER", "KATAKANA LETTER", "SYLLABLE", "LIGATURE");
  private static final Pattern CONTAINS_LOWERCASE_TRIGGER = Pattern.compile("(" +
      OR_JOINER.join(LOWERCASE_TRIGGERS) + ")");

  private static final ImmutableSet<String> PLUS_M = ImmutableSet.of(
      "ANUSVARA", "ANUSVARAYA", "NIKAHIT", "SIGN BINDI", "TIPPI");
  private static final Pattern PLUS_M_PATTERN = Pattern.compile(
      "\\b(" + OR_JOINER.join(PLUS_M) + ")\\b");

  private static final Pattern SCHWA_PATTERN = Pattern.compile("\\bSCHWA\\b");
  private static final Pattern CONTAINS_WHITESPACE = Pattern.compile("\\s");

  private static final Pattern LETTER_PATTERN_1 = Pattern.compile("^[AEIOU]+([^AEIOU]+)$");
  private static final Pattern LETTER_PATTERN_2 = Pattern.compile("^([^-AEIOUY]+)[AEIOU].*");
  private static final Pattern LETTER_PATTERN_3 = Pattern.compile("^Y[AEIOU].*");
  private static final Pattern LETTER_PATTERN_4 = Pattern.compile("^(Y[AEIOU]+)[^AEIOU].*$");
  private static final Pattern LETTER_PATTERN_5 = Pattern.compile("^([AEIOU]+)[^AEIOU]+[AEIOU].*");

  // //    $char_name =~ s/^(Y)[AEIOU].*/$1/i if $orig_char_name =~ /\b(?:BENGALI|DEVANAGARI|GURMUKHI|GUJARATI|KANNADA|MALAYALAM|MODI|MYANMAR|ORIYA|TAMIL|TELUGU|TIBETAN)\b.*\bLETTER YA\b/;
  private static final ImmutableSet<String> YA_WORDS = ImmutableSet.of(
      "BENGALI", "DEVANAGARI", "GURMUKHI", "GUJARATI", "KANNADA", "MALAYALAM", "MODI", "MYANMAR",
      "ORIYA", "TAMIL", "TELUGU", "TIBETAN");
  private static final Pattern YA_PATTERN = Pattern.compile("\\b("
      + OR_JOINER.join(YA_WORDS) + ")\\b.*\\bLETTER YA\\b");


  private static final int SUSPICIOUS_LENGTH = 6;

  private static Optional<String> codepointToStringInternal(int codepoint) {
    if (Character.isDigit(codepoint)) {
      return Optional.of(Integer.toString(Character.digit(codepoint, 10)));
    }

    if (TransliterateByUnicodeCharacterName.isPunctuation(codepoint)) {
      return Optional.of(transliteratePunctuation(codepoint));
    } else if (Character.getType(codepoint) == Character.FORMAT) {
      // all non-punctuation characters in the OTHER_FORMAT block are removed
      return Optional.of("");
    }

    String charName = Character.getName(codepoint);
    if (charName == null) {
      return Optional.absent();
    }

    final String originalCharName = charName;

    charName = DELETE_UP_TO_AND_INCLUDING_FROM_BEGINNING_PATTERN.matcher(charName)
        .replaceAll("");
    charName = DELETE_SUFFIX_AND_ALL_FOLLOWING_PATTERN.matcher(charName)
        .replaceAll("");
    charName = PHRASES_TO_DELETE_PATTERN.matcher(charName).replaceAll("");

    for (int i = 0; i < 3; ++i) {
      charName = DELETE_UP_TO_AND_INCLUDING_AGAIN_PATTERN.matcher(charName).replaceAll("");
    }

    if (THAI_CODEPOINTS.contains(codepoint)) {
      charName = STRIP_THAI_CHARACTER.matcher(charName).replaceAll("");
      if (THAI_CONSONTANTS.contains(codepoint)) {
        final Matcher stripThaiConsonantMatcher = STRIP_THAI_CONSONANT.matcher(charName);
        if (stripThaiConsonantMatcher.matches()) {
          charName = stripThaiConsonantMatcher.group(1);
        }
      } else if (THAI_VOWEL.matcher(charName).find()) {
        charName = THAI_VOWEL.matcher(charName).replaceAll("");
      } // otherwise leave the character name unchanged
    }

    if (CONTAINS_LOWERCASE_TRIGGER.matcher(originalCharName).find()) {
      charName = charName.toLowerCase(Locale.ENGLISH);
    } else if (PLUS_M_PATTERN.matcher(charName).find()) {
      charName = "+m";
    } else if (SCHWA_PATTERN.matcher(charName).find()) {
      charName = "e";
    } else //noinspection StatementWithEmptyBody
      if (CONTAINS_WHITESPACE.matcher(charName).find()) {
        // whitespace apparently blocks further processing?
      } else if (originalCharName.contains("KHMER LETTER")) {
        charName += "-";
      } else //noinspection StatementWithEmptyBody
        if (originalCharName.contains("CHEROKEE LETTER")) {
          // no further processing
        } else if (originalCharName.equals("KHMER INDEPENDENT VOWEL")) {
          charName = charName.replaceAll("q", "");
        } else if (originalCharName.contains("LETTER")) {
          final Matcher letterPattern1Matcher = LETTER_PATTERN_1.matcher(charName);
          if (letterPattern1Matcher.matches()) {
            charName = letterPattern1Matcher.group(1);
          }
          final Matcher letterPattern2Matcher = LETTER_PATTERN_2.matcher(charName);
          if (letterPattern2Matcher.matches()) {
            charName = letterPattern2Matcher.group(1);
          }
          if (YA_PATTERN.matcher(originalCharName).find()) {
            charName = LETTER_PATTERN_3.matcher(charName).replaceFirst("Y");
          }
          final Matcher letterPattern4Matcher = LETTER_PATTERN_4.matcher(charName);
          if (letterPattern4Matcher.find()) {
            charName = letterPattern4Matcher.replaceFirst(letterPattern4Matcher.group(1));
          }
          final Matcher letterPattern5Matcher = LETTER_PATTERN_5.matcher(charName);
          if (letterPattern5Matcher.matches()) {
            charName = letterPattern5Matcher.replaceFirst(letterPattern5Matcher.group(1));
          }
        }

    if (!Character.isUpperCase(codepoint)) {
      charName = charName.toLowerCase(Locale.ENGLISH);
    }

    if (charName.length() >= SUSPICIOUS_LENGTH) {
      log.warn("Codepoint with original name {} transliterates to suspiciously long string {},"
          + " refusing to add transliteration", originalCharName, charName);
      return Optional.absent();
    }

    return Optional.of(charName);
  }


  private static final ImmutableMap<String, String> OTHER_PUNCTUATION_PATTERNS =
      ImmutableMap.<String, String>builder()
          .put("INVERTED EXCLAMATION MARK", "¡")
          .put("EXCLAMATION_MARK", "!")
          .put("QUOTATION MARK", "\"")
          .put("INVERTED QUESTION MARK", "¿")
          .put("QUESTION MARK", "?")
          .put("APOSTROPHE", "'")
          .put("COMMA", ",")
          .put("FULL STOP", ".")
// u2030 is English per-mille
          .put("PER MILLE", "\u2030")
// u2031 is English per-10k
          .put("PER TEN THOUSAND", "\u2031")
          .put("SEMICOLON", ";")
          .put("DECIMAL SEPARATOR", ".")
          .put("THOUSANDS SEPARATOR", ",")
// space to distinguish from semicolon
          .put(" COLON", ":")
          .put("NUMBER SIGN", "#")
          .put("AMPERSAND", "&")
          .put("ASTERISK", "*")
          .put("PERCENT SIGN", "%")
          .put("COMMERCIAL AT", "@")
          .put("REVERSE SOLIDUS", "\\")
          .put("SOLIDUS", "/").build();

  private static final ImmutableMap<String, String> START_PUNCTUATION_PATTERNS =
      ImmutableMap.of(
          "PARENTHESIS", "(",
          "SQUARE BRACKET", "[",
          "CURLY BRACKET", "{");

  private static final ImmutableMap<String, String> END_PUNCTUATION_PATTERNS =
      ImmutableMap.of(
          "PARENTHESIS", ")",
          "SQUARE BRACKET", "]",
          "CURLY BRACKET", "}");

  private static final ImmutableMap<String, String> INITIAL_QUOTE_PATTERNS =
      ImmutableMap.of(
          "SINGLE", "'",
          "DOUBLE", "\"");

  private static final ImmutableMap<String, String> FINAL_QUOTE_PATTERNS =
      ImmutableMap.of(
          "SINGLE", "'",
          "DOUBLE", "\"");

  private static final ImmutableMap<String, String> CONNECTOR_PUNCTUATION_PATTERNS =
      ImmutableMap.of(
          // preserve underscores
          "LOW LINE", "_",
          // empty string matches everything
          // we just use a map for consistency
          "", "-");

  private static final ImmutableMap<String, String> ENCLOSING_MARK_PATTERNS =
      // TODO: we might want to eventually support the Cyrillic combining number signs, issue #14
      // http://www.fileformat.info/info/unicode/category/Me/list.htm
      // empty string matches everything
      // we just use a map for consistency
      ImmutableMap.of("", "");

  private static String transliteratePunctuation(final int codepoint) {
    final Map<String, String> patternToResult;
    switch (Character.getType(codepoint)) {
      case Character.CONNECTOR_PUNCTUATION:
        patternToResult = CONNECTOR_PUNCTUATION_PATTERNS;
        break;
      case Character.INITIAL_QUOTE_PUNCTUATION:
        patternToResult = INITIAL_QUOTE_PATTERNS;
        break;
      case Character.FINAL_QUOTE_PUNCTUATION:
        patternToResult = FINAL_QUOTE_PATTERNS;
        break;
      case Character.START_PUNCTUATION:
        patternToResult = START_PUNCTUATION_PATTERNS;
        break;
      case Character.END_PUNCTUATION:
        patternToResult = END_PUNCTUATION_PATTERNS;
        break;
      case Character.OTHER_PUNCTUATION:
        patternToResult = OTHER_PUNCTUATION_PATTERNS;
        break;
      case Character.ENCLOSING_MARK:
        patternToResult = ENCLOSING_MARK_PATTERNS;
        break;
      default:
        throw new IllegalArgumentException("Codepoint %s is not a punctuation mark");
    }

    final String name = Character.getName(codepoint);
    if (name != null) {
      for (final Map.Entry<String, String> pattern : patternToResult.entrySet()) {
        if (name.contains(pattern.getKey())) {
          return pattern.getValue();
        }
      }
    }

    return StringUtils.codepointToString(codepoint);
  }
}
