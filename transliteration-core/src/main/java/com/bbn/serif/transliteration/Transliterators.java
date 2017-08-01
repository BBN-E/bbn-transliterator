package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringNormalizer;
import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.base.Optional;
import com.google.common.collect.SetMultimap;

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
    return requestUniqueTransliteratorForLanguageCode(iso6392LanguageCode, iso6392ToLanguageMap,
        Optional.<Transliterator>absent());
  }

  /**
   * Gets the unique transliterator for a given language code, throwing a {@link RuntimeException}
   * if there are multiple transliterators registered. If no transliterators are registered it
   * returns the provided default transliterator (which should typically be whatever is bound with
   * the annotation {@link Transliterator.GeneralTransliterator}.
   */
  public static Transliterator requestUniqueTransliteratorForLanguageCode(
      String iso6392LanguageCode,
      SetMultimap<String, Transliterator> iso6392ToLanguageMap,
      Transliterator defaultTransliterator) {
    return requestUniqueTransliteratorForLanguageCode(iso6392LanguageCode, iso6392ToLanguageMap,
        Optional.of(defaultTransliterator));
  }

  /**
   * Shared implementation to {@link #requestUniqueTransliteratorForLanguageCode(String,
   * SetMultimap)} and {@link #requestUniqueTransliteratorForLanguageCode(String, SetMultimap,
   * Transliterator)}.
   */
  private static Transliterator requestUniqueTransliteratorForLanguageCode(
      String iso6392LanguageCode,
      SetMultimap<String, Transliterator> iso6392ToLanguageMap,
      Optional<Transliterator> defaultTransliterator) {
    final Set<Transliterator> registeredTransliterators =
        iso6392ToLanguageMap.get(iso6392LanguageCode);
    if (registeredTransliterators.size() == 1) {
      return getOnlyElement(registeredTransliterators);
    } else if (registeredTransliterators.isEmpty()) {
      if (defaultTransliterator.isPresent()) {
        log.info("No custom transliterator registered for {}, using default transliterator",
            iso6392LanguageCode);
        return defaultTransliterator.get();
      } else {
        throw new RuntimeException("No transliterators registered for "
            + iso6392LanguageCode + ". Transliterators are registered for "
            + iso6392ToLanguageMap.keySet());
      }
    } else {
      throw new RuntimeException("Multiple transliterators are registered for "
          + iso6392LanguageCode + ". You will need to use a custom module to "
          + "determine which to use");
    }
  }
}
