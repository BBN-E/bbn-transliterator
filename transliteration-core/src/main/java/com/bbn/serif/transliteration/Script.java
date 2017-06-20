package com.bbn.serif.transliteration;


import com.bbn.bue.common.AbstractParameterizedModule;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.collections.MultimapUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.strings.offsets.CharOffset;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import com.google.inject.Provides;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static com.bbn.bue.common.StringUtils.pipeJoiner;
import static com.bbn.bue.common.StringUtils.trimFunction;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Lists.transform;

/**
 * Structure to store information about a writing script.  Based on the information in URoman's
 * script data file.
 */
@Value.Immutable
@TextGroupImmutable
@Value.Enclosing
@Functional
abstract class Script {

  /**
   * The primary name for the script.
   */
  public abstract String primaryName();

  /**
   * All names which may be used for the script (including the primary name).
   */
  public abstract ImmutableSet<String> allNames();

  /**
   * If the script is an abugida, what its primary default vowel is. An abugida is a writing
   * system where consonants have a default vowel associated with them which is used unless
   * overriden by a diacritic.
   */
  public abstract Optional<String> primaryAbugidaDefaultVowel();
  public abstract ImmutableSet<String> allAbugidaDefaultVowels();

  /**
   * What languages is this script used for?
   */
  public abstract ImmutableSet<String> associatedLanguages();

  @Value.Check
  protected void check() {
    checkArgument(allNames().contains(primaryName()));
  }

  static class Builder extends ImmutableScript.Builder {}

  private static final String SCRIPT_NAME_FIELD = "script-name";
  private static final String ALTERNATE_SCRIPT_NAME_FIELD = "alt-script-name";
  private static final String LANGUAGE_FIELD = "language";
  private static final String ABUGIDA_DEFAULT_VOWEL_FIELD = "abugida-default-vowel";

  public static ImmutableSet<Script> loadUromanScriptData(CharSource source) throws IOException {
    final ImmutableSet.Builder<Script> ret = ImmutableSet.builder();
    for (final String line : source.readLines()) {
      if (!URomanFileFormat.isCommentLine(line)) {
        final ImmutableListMultimap<String, String> parts =
            URomanFileFormat.parseColonDelimitedLine(line);
        // sample lines:
        // ::script-name Khmer ::abugida-default-vowel a, o
        // ::script-name CJK ::alt-script-name Chinese, Kanji ::language Chinese, Japanese, Korean, Mandarin
        final String primaryName = getOnlyElement(parts.get(SCRIPT_NAME_FIELD)).trim();
        final List<String> abugidaDefaultVowels =
            transform(parts.get(ABUGIDA_DEFAULT_VOWEL_FIELD), trimFunction());

        final Script.Builder scriptB = new Script.Builder()
            .primaryName(primaryName)
            .addAllNames(primaryName)
            .addAllAllNames(transform(parts.get(ALTERNATE_SCRIPT_NAME_FIELD), trimFunction()))
            .addAllAssociatedLanguages(transform(parts.get(LANGUAGE_FIELD), trimFunction()))
            .addAllAllAbugidaDefaultVowels(abugidaDefaultVowels);

        if (!abugidaDefaultVowels.isEmpty()) {
          scriptB.primaryAbugidaDefaultVowel(abugidaDefaultVowels.get(0));
        }
        ret.add(scriptB.build());
      }
    }
    return ret.build();
  }


  /**
   * A mapping from positions in a string to the possible scripts the character at that
   * position may come from.
   */
  public interface ScriptMapping {
    UnicodeFriendlyString string();
    ImmutableSet<Script> scriptsForOffset(CharOffset offset);

    /**
     * Whether the given {@code vowel} could be an abugida default vowel associated with
     * the chaarcter at {@code offset}.
     */
    boolean isAbugidaVowelForSomeScript(CharOffset offset, String vowel);

    /**
     * Whether the character at {@code offset} definitely begins a new script.
     */
    boolean startsNewScript(CharOffset offset);

    /**
     * The primary default abugida vowel, if any, associated with the character at the given offset.
     */
    Optional<String> primaryDefaultVowel(CharOffset offset);

    /**
     * Whether or not the character at the given position is from the Devangari script. This is a
     * bit of a hack and schould get cleaned up by issue #11.
     */
    boolean isDevanagari(CharOffset codePointOffset);
  }

  /**
   * Something which knows what scripts Unicode code points may come from.
   */
  interface CodePointToScriptMapper {
    ImmutableSet<Script> scriptsForCodepoint(int codePoint);
    ScriptMapping mapStringToScripts(UnicodeFriendlyString s);
  }

  static abstract class AbstractCodePointToScriptMapper implements CodePointToScriptMapper {
    @Override
    public ScriptMapping mapStringToScripts(UnicodeFriendlyString s) {
      final ImmutableScript.DefaultScriptMapping.Builder ret =
          ImmutableScript.DefaultScriptMapping.builder()
          .string(s);

      s.processCodePoints(new UnicodeFriendlyString.NoResultCodePointProcessor() {
        @Override
        public void processCodepoint(final UnicodeFriendlyString s,
            final CharOffset codePointOffset, final int codePoint) {
          ret.putAllData(codePointOffset.asInt(), scriptsForCodepoint(codePoint));
        }
      });
      return ret.build();
    }
  }

  @TextGroupImmutable
  @Value.Immutable
  static abstract class URomanCodePointToScriptMapper extends AbstractCodePointToScriptMapper
      implements CodePointToScriptMapper {
    public abstract ImmutableSetMultimap<String, Script> scriptsByName();

    @Value.Check
    protected URomanCodePointToScriptMapper check() {
      boolean needsCaseNormalization = false;

      final ImmutableSetMultimap.Builder<String, Script> scriptsByUppercaseName =
          ImmutableSetMultimap.builder();

      for (final Map.Entry<String, Script> e : scriptsByName().entries()) {
        final String uppercasedName = e.getKey().toUpperCase(Locale.ENGLISH);
        if (!e.getKey().equals(uppercasedName)) {
          needsCaseNormalization = true;
        }
        scriptsByUppercaseName.put(uppercasedName, e.getValue());
      }

      if (needsCaseNormalization) {
        return ImmutableScript.URomanCodePointToScriptMapper.builder()
            .from(this)
            .scriptsByName(scriptsByUppercaseName.build())
            .build();
      } else {
        return this;
      }
    }


    public final ImmutableSet<Script> scriptsForCodepoint(int codePoint) {
      try {
        return cache.get(codePoint);
      } catch (ExecutionException e) {
        throw new RuntimeException(e.getCause());
      }
    }

    /**
     * Gets a {@link CodePointToScriptMapper} based on URoman's heuristics and data files.
     */
    static CodePointToScriptMapper forScripts(Iterable<Script> scripts) {
      final ImmutableSetMultimap.Builder<String, Script> uppercaseNameToScripts =
          ImmutableSetMultimap.builder();

      for (final Script script : scripts) {
        for (final String scriptName : script.allNames()) {
          // we normalize all names to uppercase because that's how they will appear
          // in the code point names
          uppercaseNameToScripts.put(scriptName.toUpperCase(Locale.ENGLISH), script);
        }
      }

      return ImmutableScript.URomanCodePointToScriptMapper.builder()
          .scriptsByName(MultimapUtils.indexToSetMultimapWithMultipleKeys(scripts, ScriptFunctions.allNames()))
          .build();

    }

    private final LoadingCache<Integer, ImmutableSet<Script>> cache = CacheBuilder.newBuilder()
        .maximumSize(10000).build(new CacheLoader<Integer, ImmutableSet<Script>>() {
          @Override
          public ImmutableSet<Script> load(final Integer codePoint) throws Exception {
            return scriptsForCodepointInternal(codePoint);
          }
        });

    private static final ImmutableSet<String> SUFFIXES_TO_STRIP = ImmutableSet.of(
        "CONSONANT", "LETTER", "LIGATURE", "SIGN", "SYLLABLE", "SYLLABICS", "VOWEL");
    private static final Pattern STRIP_SUFFIXES_PATTERN = Pattern.compile("\\s+("
        + pipeJoiner().join(SUFFIXES_TO_STRIP) + ")\\b.*");
    private static final Pattern DELETE_FINAL_WORD_PATTERN = Pattern.compile("\\s*\\S+\\s*$");

    private ImmutableSet<Script> scriptsForCodepointInternal(final int codePoint) {
      String charName = Character.getName(codePoint);
      if (charName == null) {
        return ImmutableSet.of();
      }
      charName = STRIP_SUFFIXES_PATTERN.matcher(charName).replaceAll("");
      while (!charName.isEmpty()) {
        if (scriptsByName().containsKey(charName)) {
          return scriptsByName().get(charName);
        } else {
          charName = DELETE_FINAL_WORD_PATTERN.matcher(charName).replaceAll("");
        }
      }
      return ImmutableSet.of();
    }
  }

  @TextGroupImmutable
  @Value.Immutable
  static abstract class DefaultScriptMapping implements ScriptMapping {
    @Override
    public abstract UnicodeFriendlyString string();
    abstract ImmutableSetMultimap<Integer, Script> data();

    public static ScriptMapping uniform(UnicodeFriendlyString s, final Script script) {
      final ImmutableScript.DefaultScriptMapping.Builder ret =
          ImmutableScript.DefaultScriptMapping.builder()
              .string(s);

      s.processCodePoints(new UnicodeFriendlyString.NoResultCodePointProcessor() {
        @Override
        public void processCodepoint(final UnicodeFriendlyString s,
            final CharOffset codePointOffset, final int codePoint) {
          ret.putData(codePointOffset.asInt(), script);
        }
      });
      return ret.build();
    }

    @Override
    public final ImmutableSet<Script> scriptsForOffset(CharOffset offset) {
      return data().get(offset.asInt());
    }

    public final boolean startsNewScript(CharOffset offset) {
      return offset.asInt() == 0 || Sets
          .intersection(data().get(offset.asInt()), data().get(offset.shiftedCopy(-1).asInt()))
          .isEmpty();
    }

    @Override
    public Optional<String> primaryDefaultVowel(CharOffset offset) {
      for (final Script script : scriptsForOffset(offset)) {
        if (script.primaryAbugidaDefaultVowel().isPresent()) {
          return script.primaryAbugidaDefaultVowel();
        }
      }
      return Optional.absent();
    }

    @Override
    public boolean isDevanagari(CharOffset offset) {
      for (final Script script : scriptsForOffset(offset)) {
        if (script.primaryName().equals("Devanagari")) {
          return true;
        }
      }
      return false;
    }

    @Value.Check
    protected void check() {
      if (!data().isEmpty()) {
        // not strict equality because some characters might not be mapped to code points and
        // therefore will have no entries in the data map
        checkArgument(Ordering.natural().min(data().keys()) >= 0);
        checkArgument(Ordering.natural().max(data().keys()) <= string().lengthInCodePoints()-1);
      }
    }

    public boolean isAbugidaVowelForSomeScript(CharOffset offset, String vowel) {
      final String lowercaseVowel = vowel.toLowerCase(Locale.ENGLISH);
      for (final Script script : scriptsForOffset(offset)) {
        if (script.allAbugidaDefaultVowels().contains(lowercaseVowel)) {
          return true;
        }
      }
      return false;
    }
  }

  static class FromParamsModule extends AbstractParameterizedModule {

    public FromParamsModule(final Parameters params) {
      super(params);
    }

    @Provides
    Set<Script> getScripts() throws IOException {
      return Script.loadUromanScriptData(Resources.asCharSource(
          Resources.getResource(Script.class, "scripts.txt"), Charsets.UTF_8));
    }

    @Provides
    CodePointToScriptMapper getMapper(Set<Script> scripts) {
      return URomanCodePointToScriptMapper.forScripts(scripts);
    }
  }
}
