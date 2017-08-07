package com.bbn.serif.transliteration;

import com.bbn.bue.common.EvalHack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

@EvalHack(eval = "lorelei-Y2")
@Deprecated
public class BuildTigranyaTransliterartionChart {

  private static final ImmutableMap<Character, String>
      BASE_TRANSLITERATION = ImmutableMap.<Character, String>builder()
      .put('\u1350', "p")
      .put('\u1260', "b")
      .put('\u1330', "p'")
      .put('\u1218', "m")
      .put('\u1348', "f")
      .put('\u1268', "v")
      .put('\u12C8', "w")
      .put('\u1270', "t")
      .put('\u12F0', "d")
      .put('\u1320', "t'")
      .put('\u1290', "n")
      .put('\u1230', "s")
      .put('\u12D8', "z")
      .put('\u1228', "r")
      .put('\u1208', "l")
      .put('\u1298', "ň")
      .put('\u1238', "š")
      .put('\u12E0', "ž")
      .put('\u12E8', "y")
      .put('\u12A8', "k")
      .put('\u12B0', "kw")
      .put('\u1308', "g")
      .put('\u1310', "gw")
      .put('\u1240', "k'")
      .put('\u1248', "k'w")
      .put('\u12B8', "k")
      .put('\u12C0', "kw")
      .put('\u1250', "q")
      .put('\u1258', "qw")
      .put('\u1210', "ḥ")
      .put('\u12D0', "‛")
      .put('\u12A0', "’")
      .put('\u1200', "h")
      .put('\u1280', "ḫ")
      .put('\u1288', "ḫw")
      // the following four characters are not used
// in the Semiticist transliteration but just deleting
// them seems to be a bad idea, so we use the LDC
// transliteration
      .put('\u1338', "ts'")
      .put('\u1278', "ch")
      .put('\u1300', "j")
      .put('\u1328', "ch'").build();

  private static final ImmutableList<String> ORDER_VOWELS =
      // we transcribe the sixth order here with "" instead
      // of ə, see LDC grammar
      ImmutableList.of("ä", "u", "i", "a", "e", "", //"ə",
          "o");

  public static void main(String[] args) {
    for (final Map.Entry<Character, String> e : BASE_TRANSLITERATION.entrySet()) {
      for (int i = 0; i < ORDER_VOWELS.size(); ++i) {
        final char source = (char) (e.getKey() + i);
        final String target = e.getValue() + ORDER_VOWELS.get(i);
        System.out.println("::s " + source + " ::t " + target + " ::lcode tir");
      }
    }
  }
}
