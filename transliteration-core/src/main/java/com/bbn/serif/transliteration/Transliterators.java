package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringNormalizer;
import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.parameters.ParametersModule;

import com.google.common.collect.SetMultimap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

import org.immutables.value.Value;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.google.common.collect.Iterables.getOnlyElement;

/**
 * Utility methods for use with {@link Transliterator}s.
 */
@Value.Enclosing
public final class Transliterators {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(Transliterators.class);
  private static Transliterator generalTransliterator;


  private static void setupGeneralTransliterator() {
    final Parameters emptyParams = Parameters.builder().build();
    final Injector injector = Guice.createInjector(ParametersModule.createSilently(emptyParams),
        new DefaultTransliterator.FromParamsModule(emptyParams));
    generalTransliterator = injector.getInstance(Key.get(Transliterator.class,
        DefaultTransliterator.GeneralTransliterator.class));
  }

  private Transliterators() {
    throw new UnsupportedOperationException();
  }

  /**
   * Wraps a {@link Transliterator} as a {@link StringNormalizer}
   */
  public static StringNormalizer asStringNormalizer(Transliterator transliterator) {
    return ImmutableTransliterators.TransliteratorAsStringNormalizer.builder()
        .transliterator(transliterator).build();
  }

  @TextGroupImmutable
  @Value.Immutable
  static abstract class TransliteratorAsStringNormalizer implements StringNormalizer {

    abstract Transliterator transliterator();


    @Override
    public String normalize(final String input) {
      return transliterator().transliterate(StringUtils.unicodeFriendly(input))
          .utf16CodeUnits();
    }
  }

  /**
   * Gets the unique transliterator for a given language code, throwing a {@link RuntimeException}
   * if there are no or multiple transliterators registered.
   */
  public static Transliterator requestUniqueTransliteratorForLanguageCode(
      String iso6392LanguageCode,
      SetMultimap<String, Transliterator> iso6392ToLanguageMap) {

    final Set<Transliterator> registeredTransliterators =
        iso6392ToLanguageMap.get(iso6392LanguageCode);
    if (registeredTransliterators.size() == 1) {
      return getOnlyElement(registeredTransliterators);
    } else if (registeredTransliterators.isEmpty()) {
      // we want to return a general transliterator instead of throwing an error
      log.warn("No transliterators registered for {}. Transliterators are registered for {}. Using "
              + "general transliterator instead.", iso6392LanguageCode,
              iso6392ToLanguageMap.keySet());
      setupGeneralTransliterator();
      return generalTransliterator;
//      throw new RuntimeException("No transliterators registered for "
//          + iso6392LanguageCode + ". Transliterators are registered for "
//          + iso6392ToLanguageMap.keySet());
    } else {
      throw new RuntimeException("Multiple transliterators are registered for "
          + iso6392LanguageCode + ". You will need to use a custom module to "
          + "determine which to use");
    }
  }
}
