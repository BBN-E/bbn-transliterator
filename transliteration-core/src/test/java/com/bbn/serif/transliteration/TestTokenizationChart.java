package com.bbn.serif.transliteration;

import com.bbn.bue.common.UnicodeFriendlyString;

import com.google.common.base.Optional;

import org.junit.Test;

import static com.bbn.bue.common.StringUtils.unicodeFriendly;
import static org.junit.Assert.assertEquals;

public class TestTokenizationChart {
  private static final Script LATIN = new Script.Builder()
      .primaryName("Latin")
      .addAllNames("Latin")
      .addAssociatedLanguages("English")
      .build();

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void testTokenizationChart() {
    final UnicodeFriendlyString abcde = unicodeFriendly("abcde");
    // if you don't have any chart edges, you shouldn't be able to get a decoding
    final TransliterationChart transliterationChart = TransliterationChart.createForLength(
        abcde, Script.DefaultScriptMapping.uniform(abcde, LATIN));
    assertEquals(Optional.absent(), transliterationChart.bestDecoding());

    final UnicodeFriendlyString abc = unicodeFriendly("abc");
    final TransliterationChart simpleChart = TransliterationChart.createForLength(abc,
        Script.DefaultScriptMapping.uniform(abc, LATIN));
    simpleChart.addEdge(new ChartEdge.Builder().startPosition(0)
    .endPosition(1).spanTransliteration("a").score(1.0).build(), "foo");
    simpleChart.addEdge(new ChartEdge.Builder().startPosition(1)
        .endPosition(2).spanTransliteration("b").score(1.0).build(), "foo");
    simpleChart.addEdge(new ChartEdge.Builder().startPosition(2)
        .endPosition(3).spanTransliteration("c").score(1.0).build(), "foo");
    assertEquals("abc", simpleChart.bestDecoding().get().utf16CodeUnits());

    final UnicodeFriendlyString abcd = unicodeFriendly("abcd");
    final TransliterationChart testLongArc = TransliterationChart.createForLength(abcd,
        Script.DefaultScriptMapping.uniform(abcd, LATIN));
    testLongArc.addEdge(new ChartEdge.Builder().startPosition(0)
        .endPosition(1).spanTransliteration("a").score(1.0).build(), "foo");
    testLongArc.addEdge(new ChartEdge.Builder().startPosition(1)
        .endPosition(2).spanTransliteration("b").score(1.0).build(), "foo");
    testLongArc.addEdge(new ChartEdge.Builder().startPosition(2)
        .endPosition(3).spanTransliteration("c").score(1.0).build(), "foo");
    testLongArc.addEdge(new ChartEdge.Builder().startPosition(3)
        .endPosition(4).spanTransliteration("d").score(1.0).build(), "foo");
    testLongArc.addEdge(new ChartEdge.Builder().startPosition(1)
        .endPosition(3).spanTransliteration("foo").score(2.5).build(), "foo");
    assertEquals("afood", testLongArc.bestDecoding().get().utf16CodeUnits());

  }
}
