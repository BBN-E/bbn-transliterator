package com.bbn.serif.transliteration;

import com.bbn.bue.common.UnicodeFriendlyString;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.parameters.ParametersModule;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static com.bbn.bue.common.StringUtils.unicodeFriendly;
import static org.junit.Assert.assertEquals;

/**
 * Tests are from URoman's per-language example files. Reference output is
 * typically URoman's output on those files, except for a few cases when we
 * choose to deviate.
 */
public class TestTransliterators {
  private static Transliterator generalTransliterator;
  private static ImmutableSetMultimap<String, Transliterator> languageSpecificTransliterator;

  @BeforeClass
  public static void setupTransliterators() {
    final Parameters emptyParams = Parameters.builder().build();
    final Injector injector = Guice.createInjector(ParametersModule.createSilently(emptyParams),
        new DefaultTransliterator.FromParamsModule(emptyParams));
    generalTransliterator = injector.getInstance(Key.get(Transliterator.class,
        DefaultTransliterator.GeneralTransliterator.class));
    languageSpecificTransliterator = ImmutableSetMultimap.copyOf(
        injector.getInstance(Key.get(new TypeLiteral<SetMultimap<String, Transliterator>>() {
        }, DefaultTransliterator.Iso6392ToTransliterator.class)));
  }

  private Transliterator transliteratorFor(String iso6392) {
    final ImmutableSet<Transliterator> ret = languageSpecificTransliterator.get(iso6392);
    if (ret.isEmpty()) {
      return generalTransliterator;
    } else {
      // while we allow for multiple transliterators for a language, right now we only
      // have one in practice, so it is safe to choose arbitrarily
      return ret.asList().get(0);
    }
  }

  @Test
  public void testIdentityTransliterator() {
    final UnicodeFriendlyString s = unicodeFriendly("foo");
    assertEquals(s, IdentityTransliterator.create().transliterate(s));
  }

  // missing: numbers. Issue #8
  @Test
  @Ignore
  public void amharicTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("amh"), "amh");
  }

  @Test
  public void arabicTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("ara"), "ara");
  }

  @Test
  public void bengaliTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("ben"), "ben");
  }

  // missing vowelization, issue #7
  @Ignore
  @Test
  public void tibetanTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("bod"), "bod");
  }

  // BMP issue, issue #6
  @Ignore
  @Test
  public void ancientEgyptianTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("egy"), "egy");
  }

  @Test
  public void modernGreekTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("ell"), "ell");
  }

  @Test
  public void farsiTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("fas"), "fas");
  }

  @Test
  public void hebrewTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("heb"), "heb");
  }

  @Test
  public void hindiTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("hin"), "hin");
  }

  // for fast debugging on single words
  @Ignore
  @Test
  public void shortTest() throws IOException {
    assertEquals("chottomattekudasai", transliteratorFor("jpn")
        .transliterate(unicodeFriendly("ちょっとまってください")).utf16CodeUnits());
  }

  // need to handle various Japanese special cases (e.g. consonant-doubling), issue #5
  @Test
  @Ignore
  public void japaneseTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("jpn"), "jpn");
  }

  // hangul
  @Test
  @Ignore
  public void koreanTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("kor"), "kor");
  }

  // overzealous schwa-deletion, issue #4
  @Ignore
  @Test
  public void marathiTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("mar"), "mar");
  }

  // issue #9 to fix
  @Ignore
  @Test
  public void multipleLanguageTest() throws IOException {
    testAgainstURomanOutput(generalTransliterator, "multiple");
  }

  @Test
  public void burmeseTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("mya"), "mya");
  }

  @Test
  public void nepaliTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("nep"), "nep");
  }

  @Test
  public void russianTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("rus"), "rus");
  }

  // some sort of devoicing rule, issue #3
  @Ignore
  @Test
  public void tamilTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("tam"), "tam");
  }

  // needs Thai order switch, issue #2
  @Test
  @Ignore
  public void thaiTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("tha"), "tha");
  }

  @Test
  public void turkishTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("tur"), "tur");
  }

  @Test
  public void uyghurTest() throws IOException {
    final Transliterator uigTransliterator = transliteratorFor("uig");
    testAgainstURomanOutput(uigTransliterator, "uig");
    // we need to *not* transliterate certain punctuation for Arabic in order to
    // match our old training data :-(
    assertTransliterationEquals("،", "،", uigTransliterator);
    assertTransliterationEquals("؛", "؛", uigTransliterator);
    assertTransliterationEquals("؟", "؟", uigTransliterator);
    assertTransliterationEquals("٪", "٪", uigTransliterator);
    assertTransliterationEquals("٫", "٫", uigTransliterator);
    assertTransliterationEquals("٬", "٬", uigTransliterator);
    assertTransliterationEquals("ـ", "ـ", uigTransliterator);
  }

  // needs handling of numbers, issue #3
  @Test
  @Ignore
  public void chineseTest() throws IOException {
    testAgainstURomanOutput(transliteratorFor("zho"), "zho");
  }

  @Test
  public void punctuationTest() throws IOException {
    // test _ things are preserved
    assertEquals("_-",
        transliteratorFor("").transliterate(unicodeFriendly("_-")).utf16CodeUnits());
  }

  private static void assertTransliterationEquals(String reference, String toTransliterate,
      Transliterator transliterator) {
    assertEquals(reference, transliterator.transliterate(
        unicodeFriendly(toTransliterate)).utf16CodeUnits());
  }

  public void testAgainstURomanOutput(Transliterator transliterator, String testName) throws IOException {
    final UnicodeFriendlyString input =
        unicodeFriendly(Resources.asCharSource(Resources.getResource(TestTransliterators.class,
            testName + ".txt"), Charsets.UTF_8).read());
    final UnicodeFriendlyString reference = unicodeFriendly(Resources.asCharSource(
        Resources.getResource(TestTransliterators.class,
            testName + ".transliterated.txt"), Charsets.UTF_8).read());

    assertEquals(reference, transliterator.transliterate(input));
  }
}
