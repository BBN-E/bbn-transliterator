package com.bbn.serif.transliteration;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.UnicodeFriendlyString;

import org.immutables.value.Value;

/**
 * A transliterator which returns its input unaltered.
 */
@TextGroupImmutable
@Value.Immutable
abstract class IdentityTransliterator implements Transliterator {

  public static Transliterator create() {
    return ImmutableIdentityTransliterator.builder().build();
  }

  @Override
  public UnicodeFriendlyString transliterate(final UnicodeFriendlyString s) {
    return s;
  }
}
