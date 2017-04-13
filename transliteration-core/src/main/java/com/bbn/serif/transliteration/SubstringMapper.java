package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.UnicodeFriendlyString;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSource;

import org.immutables.value.Value;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bbn.bue.common.StringUtils.unicodeFriendly;
import static com.bbn.bue.common.UnicodeFriendlyStrings.lengthInCodePointsFunction;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * A transliteration rule which matches source characters against defined mappings
 * of source substrings to target strings.
 */
@Value.Immutable
@TextGroupImmutable
@Value.Enclosing
public abstract class SubstringMapper implements DefaultTransliterator.TransliterationRuleBlock {
  public abstract ImmutableMultimap<UnicodeFriendlyString, SubstringMapping> stringMappings();

  /**
   * Indexes all our mappings by the first character of the substring to match.
   * We could use a full trie for this, but most of our patterns are one or two characters,
   * so this simple approach is good enough for now.
   */
  @Value.Derived
  ImmutableMultimap<Integer, UnicodeFriendlyString> mappingsByFirstCharacter() {
    final ImmutableMultimap.Builder<Integer, UnicodeFriendlyString> ret = ImmutableMultimap.builder();

    for (final Map.Entry<UnicodeFriendlyString, SubstringMapping> e : stringMappings().entries()) {
      // we need this check because derived attributes will be computed before check() is called
      if (!e.getKey().isEmpty()) {
        // we know the keys are never empty by our check method
        ret.put(e.getKey().utf16CodeUnits().codePointAt(0), e.getKey());
      }
    }

    return ret.build();
  }

  @Value.Derived
  public int maxPatternLength() {
    if (!stringMappings().isEmpty()) {
      return Ordering.natural().max(
          FluentIterable.from(stringMappings().keySet())
              .transform(lengthInCodePointsFunction()));
    } else {
      return 0;
    }
  }

  @Value.Check
  protected void check() {
    for (final UnicodeFriendlyString pattern : stringMappings().keySet()) {
      checkArgument(!pattern.isEmpty(), "Cannot have empty substring mapper pattern");
    }
  }

  public void applyToChart(TransliterationChart chart) {
    final UnicodeFriendlyString ufs = chart.string();
    // simple but slow implementation
    for (int codeUnitOffset = 0, codepointOffset = 0; codeUnitOffset < ufs.lengthInUtf16CodeUnits(); ++codepointOffset) {
      final int codePoint = ufs.utf16CodeUnits().codePointAt(codeUnitOffset);
      for (final UnicodeFriendlyString possibleMatch : mappingsByFirstCharacter().get(codePoint)) {
        if (ufs.utf16CodeUnits().startsWith(possibleMatch.utf16CodeUnits(), codeUnitOffset)) {
          for (final SubstringMapping mapping : stringMappings().get(possibleMatch)) {
            chart.addEdge(new ChartEdge.Builder()
                    .startPosition(codepointOffset)
                    .endPosition(codepointOffset + possibleMatch.lengthInCodePoints())
                    .spanTransliteration(mapping.transliteration().utf16CodeUnits())
                    .score(mapping.score()).build(),
                mapping.comment().or("substring mapper (no comment)"));
          }
        }
      }
      codeUnitOffset += Character.charCount(codePoint);
    }
  }


  static class Builder extends ImmutableSubstringMapper.Builder {}

  @Value.Immutable
  @TextGroupImmutable
  public abstract static class SubstringMapping {
    public abstract UnicodeFriendlyString transliteration();
    public abstract double score();
    public abstract Optional<String> comment();

    static class Builder extends ImmutableSubstringMapper.SubstringMapping.Builder {}
  }

  @Value.Immutable
  @TextGroupImmutable
  public abstract static class LoadSubstringMappingsResult {
    public abstract SubstringMapper generalMapper();
    public abstract ImmutableMap<String, SubstringMapper> languageSpecificMappers();


    static class Builder extends ImmutableSubstringMapper.LoadSubstringMappingsResult.Builder {}
  }

  private static final Pattern STRIP_DOUBLE_QUOTES = Pattern.compile("^\"(.*)\"$");
  private static final Pattern STRIP_SINGLE_QUOTES = Pattern.compile("^'(.*)'$");

  private static final String SOURCE_FIELD = "s";
  private static final String TARGET_FIELD = "t";
  // currently ignored
  private static final String NUMERIC_FIELD = "num";
  private static final String LANGUAGE_CODE_FIELD = "lcode";
  private static final String COMMENT_FIELD = "comment";

  public static LoadSubstringMappingsResult loadURomanSubstringMappings(final Iterable<CharSource> substringMappingsFiles)
      throws IOException {
    final SubstringMapper.Builder generalMapper = new SubstringMapper.Builder();
    final SortedMap<String, SubstringMapper.Builder> languageSpecificMappers = new TreeMap<>();

    for (final CharSource substringMappingsFile : substringMappingsFiles) {
      // sample line
      // ::s Ҥ ::t Ng ::comment Cyrillic capital ligature EN GHE
      int lineNo = 0;
      for (final String line : substringMappingsFile.readLines()) {
        ++lineNo;

        try {
          parseLine(line, generalMapper, languageSpecificMappers);
        } catch (Exception e) {
          throw new BadMappingsFileException("Exception while parsing line " + lineNo + " of "
              + "custom mappings file " + substringMappingsFile + ". Cannot parse line:" + line);
        }
      }
    }

    final ImmutableMap.Builder<String, SubstringMapper> languageSpecificMappersBuilt =
        ImmutableMap.builder();
    for (final Map.Entry<String, Builder> e : languageSpecificMappers.entrySet()) {
      final Builder languageSpecificMapperBuilder = e.getValue();
      languageSpecificMappersBuilt.put(e.getKey(), languageSpecificMapperBuilder.build());
    }
    return new LoadSubstringMappingsResult.Builder()
        .generalMapper(generalMapper.build())
        .languageSpecificMappers(languageSpecificMappersBuilt.build())
        .build();
  }

  public static SubstringMapper loadURomanCJKMappings(final CharSource source) throws IOException {
    final SubstringMapper.Builder cjkBuilder = new SubstringMapper.Builder();
    for (final String line : source.readLines()) {
      // comment or blank line
      if (line.startsWith("#") || line.isEmpty()) {
        continue;
      }

      final List<String> parts = StringUtils.onTabs().splitToList(line);

      if (parts.size() != 2) {
        throw new RuntimeException("Bad line in CJK mappings file: " + line);
      }

      cjkBuilder.putStringMappings(unicodeFriendly(parts.get(0)),
          new SubstringMapping.Builder()
              .transliteration(unicodeFriendly(parts.get(1))).score(1.0)
              .comment("CJK").build());
    }
    return cjkBuilder.build();
  }


  private static final double PER_CHARACTER_CUSTOM_MAPPING_SCORE = 1.1;
  private static final double LANGAUGE_SPECIFIC_BOOST = 1.1;

  private static void parseLine(final String line, final Builder generalMapper,
      final SortedMap<String, Builder> languageSpecificMappers)
      throws BadMappingsFileException {
    if (line.isEmpty() || line.startsWith("#")) {
      return;
    }

    final ImmutableMultimap<String, String> parts = URomanFileFormat.parseColonDelimitedLine(line);
    final String source = Iterables.getOnlyElement(parts.get(SOURCE_FIELD)).replaceAll("\\s*$", "");

    if (!parts.containsKey(TARGET_FIELD)) {
      // there are some entries with no target which only specify numeric mappings. We
      // currently skip these
      return;
    }

    String target = Iterables.getOnlyElement(parts.get(TARGET_FIELD)).replaceAll("\\s*$", "");

    final Matcher stripDoubleQuotesMatcher = STRIP_DOUBLE_QUOTES.matcher(target);
    if (stripDoubleQuotesMatcher.matches()) {
      target = stripDoubleQuotesMatcher.group(1);
    }
    final Matcher stripSingleQuotesMatcher = STRIP_SINGLE_QUOTES.matcher(target);
    if (stripSingleQuotesMatcher.matches()) {
      target = stripSingleQuotesMatcher.group(1);
    }

    if (parts.containsKey(NUMERIC_FIELD) && target.isEmpty()) {
      // this is a temporary hack - until we have proper handling of numbers we do a direct
      // string translation to the provided numeric value, which will sometimes be wrong
      // see issue #13
      target = Iterables.getOnlyElement(parts.get(NUMERIC_FIELD));
    }

    final Collection<String> comments = parts.get(COMMENT_FIELD);
    final Optional<String> comment;
    if (comments.isEmpty()) {
      comment = Optional.absent();
    } else {
      comment = Optional.of(StringUtils.SemicolonJoiner.join(comments));
    }

    final Collection<String> languageCodes = parts.get(LANGUAGE_CODE_FIELD);

    // this is a temporary hack to encourage longer matches - see issue #12
    final double score =
        Math.pow(1.1, unicodeFriendly(source).lengthInCodePoints()) *
            PER_CHARACTER_CUSTOM_MAPPING_SCORE * StringUtils.unicodeFriendly(source)
            .lengthInCodePoints();

    if (!languageCodes.isEmpty()) {
      for (final String languageCode : languageCodes) {
        final String trimmedLanguageCode = languageCode.trim();
        final Builder langSpecificMapper;
        if (!languageSpecificMappers.containsKey(trimmedLanguageCode)) {
          languageSpecificMappers .put(trimmedLanguageCode, langSpecificMapper = new Builder());
        } else {
          langSpecificMapper = languageSpecificMappers.get(trimmedLanguageCode);
        }
        langSpecificMapper.putStringMappings(unicodeFriendly(source),
            new SubstringMapping.Builder()
                .transliteration(unicodeFriendly(target)).score(
                LANGAUGE_SPECIFIC_BOOST * score)
                .comment(comment + " [lang: " + trimmedLanguageCode + "]").build());
      }
    } else {
      generalMapper.putStringMappings(unicodeFriendly(source),
          new SubstringMapping.Builder()
              .transliteration(unicodeFriendly(target)).score(score).comment(comment).build());
    }
  }

  private static final String UNICODE_DATA_OVERWRITE_SOURCE_FIELD = "u";
  private static final String UNICODE_DATA_OVERWRITE_TARGET_FIELD = "r";

  private static final double PER_CHARACTER_OVERWRITE_MAPPING_SCORE = 1.2;

  public static SubstringMapper loadUromanUnicodeDataOverwriteMappings(final CharSource source)
      throws IOException {
    final SubstringMapper.Builder mapper = new SubstringMapper.Builder();

    int lineNo = 0;
    for (final String line : source.readLines()) {
      ++lineNo;

      try {
        parseUnicodeDataOverwriteLine(line, mapper);
      } catch (Exception e) {
        throw new BadMappingsFileException("Exception while parsing line " + lineNo + " of "
            + "Unicode data overwrite file " + source + ". Cannot parse line:" + line);
      }
    }

    return mapper.build();
  }

  public static void parseUnicodeDataOverwriteLine(String line, Builder mapper)
      throws BadMappingsFileException {
    if (line.isEmpty() || line.startsWith("#")) {
      return;
    }

    final ImmutableMultimap<String, String> parts = URomanFileFormat.parseColonDelimitedLine(line);
    final String source = new String(new int[] {Integer.parseInt(Iterables.getOnlyElement(parts.get(UNICODE_DATA_OVERWRITE_SOURCE_FIELD)).replaceAll("\\s*$", ""), 16)}, 0, 1);

    if (!parts.containsKey(UNICODE_DATA_OVERWRITE_TARGET_FIELD)) {
      return;
    }

    String target = Iterables.getOnlyElement(parts.get(UNICODE_DATA_OVERWRITE_TARGET_FIELD)).replaceAll("\\s*$", "");

    final Matcher stripDoubleQuotesMatcher = STRIP_DOUBLE_QUOTES.matcher(target);
    if (stripDoubleQuotesMatcher.matches()) {
      target = stripDoubleQuotesMatcher.group(1);
    }
    final Matcher stripSingleQuotesMatcher = STRIP_SINGLE_QUOTES.matcher(target);
    if (stripSingleQuotesMatcher.matches()) {
      target = stripSingleQuotesMatcher.group(1);
    }

    if (parts.containsKey(NUMERIC_FIELD) && target.isEmpty()) {
      // this is a temporary hack - until we have proper handling of numbers we do a direct
      // string translation to the provided numeric value, which will sometimes be wrong
      // see issue #13
      target = Iterables.getOnlyElement(parts.get(NUMERIC_FIELD));
    }

    final Collection<String> comments = parts.get(COMMENT_FIELD);
    final String comment;
    if (comments.isEmpty()) {
      comment = "[Unicode overwrite]";
    } else {
      comment = StringUtils.SemicolonJoiner.join(comments) + "[Unicode overwrite]";
    }

    final double score =
        // this is a temporary hack to encourage longer matches - see issue #12
        Math.pow(1.1, unicodeFriendly(source).lengthInCodePoints()) *
            PER_CHARACTER_OVERWRITE_MAPPING_SCORE * StringUtils.unicodeFriendly(source)
            .lengthInCodePoints();


      mapper.putStringMappings(unicodeFriendly(source),
          new SubstringMapping.Builder()
              .transliteration(unicodeFriendly(target)).score(score).comment(comment).build());

  }

  static class BadMappingsFileException extends IOException {
    public BadMappingsFileException(String msg) {
      super(msg);
    }
  }
}

