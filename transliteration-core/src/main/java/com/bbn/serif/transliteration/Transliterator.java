package com.bbn.serif.transliteration;

import com.bbn.bue.common.AbstractParameterizedModule;
import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.parameters.Parameters;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.inject.Provides;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Qualifier;
import javax.inject.Singleton;

import static com.google.common.base.Charsets.UTF_8;

/**
 * An object which can transliterate a string from one writing system to another.
 *
 * If you want to use the default {@link Transliterator} in an application,
 * install {@link FromParamsModule} and bind {@link Iso6392ToTransliterator} or
 * {@link GeneralTransliterator}.
 */
public interface Transliterator {
  UnicodeFriendlyString transliterate(UnicodeFriendlyString s);

  /**
   * Annotation for injecting a  {@link com.google.common.collect.SetMultimap} from
   * ISO 639-2 language code {@link String}s to {@link Transliterator}s for that language code.
   * This is not a map because there may be multiple transliteration schemes between languages.
   */
  @Qualifier
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Iso6392ToTransliterator {}

  /**
   * Annotation for injecting a {@link Transliterator} which acts in a generic, language-agnostic
   * manner.  If you know what language you are working with, prefer to use
   * {@link Iso6392ToTransliterator}
   */
  @Qualifier
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @interface GeneralTransliterator {}

  /**
   * Binds a default {@link Transliterator} implementation based on URoman.  By default this uses
   * a number of data files from URoman giving transliteration "hints". You can block the use of
   * these by specifying {@link #SUPPRESS_DEFAULT_MANUAL_MAPPINGS_PARAM}.  It will not additionally
   * block the use of URoman's mapping of CJK characters unless you specify
   * {@link #SUPPRESS_DEFAULT_CJK_MAPPINGS_PARAM}.  If you would like to include your own custom
   * mappings in addition, point {@link #CUSTOM_MAPPINGS_PARAM} to a file in the same format
   * as {@code customMappings.txt} found in {@code src/main/resources/com/bbn/serif/transliteration}
   * in this module.
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  final class FromParamsModule extends AbstractParameterizedModule {
    private static final Logger log = LoggerFactory.getLogger(FromParamsModule.class);
    public static final String SUPPRESS_DEFAULT_MANUAL_MAPPINGS_PARAM =
        "com.bbn.serif.transliterator.suppressManualCustomMappings";
    public static final String SUPPRESS_DEFAULT_CJK_MAPPINGS_PARAM =
        "com.bbn.serif.transliterator.suppressDefaultCJKMappings";
    public static final String CUSTOM_MAPPINGS_PARAM =
        "com.bbn.serif.transliterator.customMappings";

    protected FromParamsModule(final Parameters parameters) {
      super(parameters);
    }

    @Override
    public void configure() {
      log.info("Using the BBN Transliterator.  It draws heavily in its code and data files from:\n"
          + "the universal romanizer software 'uroman' written by Ulf Hermjakob, USC Information\n"
          + "Sciences Institute (2015-2016).  If you publish something which relies on this code, "
          + "please cite him.");
      // some transliteration steps rely on script identification
      install(new Script.FromParamsModule(params()));
    }

    @Provides
    @Singleton
    @GeneralTransliterator
    Transliterator getGeneralTransliterator(Optional<SubstringMapper.LoadSubstringMappingsResult> customMappings,
        @UnicodeDataOverwriteMappings SubstringMapper unicodeOverwriteMappings,
        Script.CodePointToScriptMapper scriptMapper) throws IOException {
      final DefaultTransliterator.Builder generalTransliterator = new DefaultTransliterator.Builder()
          .scriptMapper(scriptMapper)
          .putRuleBlocksBySequenceNumber(DefaultTransliterator.INDEPENDENT_INITIAL_STEP,
              TransliterateByUnicodeCharacterName.INSTANCE)
          .putRuleBlocksBySequenceNumber(DiacriticDeletion.DEFAULT_SEQUENCE_NUMBER,
              DiacriticDeletion.INSTANCE)
          .putRuleBlocksBySequenceNumber(AbugidaRules.DEFAULT_SEQUENCE_NUMBER,
              AbugidaRules.INSTANCE)
          .putRuleBlocksBySequenceNumber(SchwaDeletion.DEFAULT_SEQUENCE_NUMBER,
              SchwaDeletion.INSTANCE)
          .putRuleBlocksBySequenceNumber(BackoffTransliterationRules.DEFAULT_SEQUENCE_NUMBER,
              BackoffTransliterationRules.INSTANCE);

      if (customMappings.isPresent()) {
        generalTransliterator.putRuleBlocksBySequenceNumber(
            DefaultTransliterator.INDEPENDENT_INITIAL_STEP,
            customMappings.get().generalMapper());
      }
      generalTransliterator.putRuleBlocksBySequenceNumber(
          DefaultTransliterator.INDEPENDENT_INITIAL_STEP, unicodeOverwriteMappings);
      if (!params().getOptionalBoolean(SUPPRESS_DEFAULT_CJK_MAPPINGS_PARAM).or(false)) {
        log.info("Using default CJK transliterations");
        generalTransliterator.putRuleBlocksBySequenceNumber(
            DefaultTransliterator.INDEPENDENT_INITIAL_STEP,
            SubstringMapper.loadURomanCJKMappings(Resources.asCharSource(
            Resources.getResource(Transliterator.class, "pinyin.txt"), UTF_8)));
      }
      return generalTransliterator.build();
    }

    @Provides
    @Singleton
    @Iso6392ToTransliterator
    SetMultimap<String, Transliterator> getLanguageSpecificTransliterators(
        @GeneralTransliterator Transliterator generalTransliterator,
        Optional<SubstringMapper.LoadSubstringMappingsResult> customMappings) {
      final ImmutableMap.Builder<String, Transliterator> ret = ImmutableMap.builder();

      if (customMappings.isPresent()) {
        for (final Map.Entry<String, SubstringMapper> e : customMappings
            .get().languageSpecificMappers().entrySet()) {
          ret.put(e.getKey(), new DefaultTransliterator.Builder()
              // cast is safe because we bind generalTransliterator in this same module
              .from((DefaultTransliterator)generalTransliterator)
              .putRuleBlocksBySequenceNumber(DefaultTransliterator.INDEPENDENT_INITIAL_STEP, e.getValue()).build());
        }
      }

      return ImmutableSetMultimap.copyOf(ret.build().asMultimap());
    }

    /**
     * This is one of URoman's custom mapping information files.
     */
    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface UnicodeDataOverwriteMappings {}

    @Provides
    @UnicodeDataOverwriteMappings
    SubstringMapper getUnicodeDataOverwrite() throws IOException {
      return SubstringMapper.loadUromanUnicodeDataOverwriteMappings(
          Resources.asCharSource(Resources.getResource(Transliterator.class, "UnicodeDataOverwrite.txt"),
          UTF_8));
    }

    /**
     * Loads various files giving hints on how to map from one writing system to another. See
     * the module's class Javadoc for details.
     */
    @Provides
    Optional<SubstringMapper.LoadSubstringMappingsResult> getCustomMappings() throws IOException {
      final List<CharSource> mappingsFiles = new ArrayList<>();

      if (!params().getOptionalBoolean(SUPPRESS_DEFAULT_MANUAL_MAPPINGS_PARAM).or(false)) {
        log.info("Using default manual transliterations");
        // both of these come from uRoman, where they were called romanization-table.txt
        // and romanization-table-arabic-block.txt
        mappingsFiles.add(Resources.asCharSource(
            Resources.getResource(Transliterator.class, "customMappings.txt"), UTF_8));
        mappingsFiles.add(Resources.asCharSource(
            Resources.getResource(Transliterator.class, "arabicMappings.txt"), UTF_8));
      }

      if (params().isPresent(CUSTOM_MAPPINGS_PARAM)) {
        final File customMappingsFile = params().getExistingFile(CUSTOM_MAPPINGS_PARAM);
        log.info("Loading custom transliterations from {}", customMappingsFile);
        mappingsFiles.add(Files.asCharSource(customMappingsFile, UTF_8));
      }

      if (!mappingsFiles.isEmpty()) {
        return Optional.of(SubstringMapper.loadURomanSubstringMappings(mappingsFiles));
      } else {
        log.info("Using no transliteration table files");
        return Optional.absent();
      }
    }
  }
}
