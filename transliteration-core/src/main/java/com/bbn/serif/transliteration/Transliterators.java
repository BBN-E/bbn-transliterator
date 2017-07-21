package com.bbn.serif.transliteration;

import com.bbn.bue.common.StringNormalizer;
import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.TextGroupImmutable;

import org.immutables.value.Value;

/**
 * Utility methods for use with {@link Transliterator}s.
 */
@Value.Enclosing
public final class Transliterators {

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
}
