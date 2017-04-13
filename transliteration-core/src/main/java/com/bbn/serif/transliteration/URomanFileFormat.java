package com.bbn.serif.transliteration;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableListMultimap;

import java.util.List;

/**
 * Utility methods for dealing with some of URoman's file formats.
 */
final class URomanFileFormat {
  private URomanFileFormat() {
    throw new UnsupportedOperationException();
  }

  public static boolean isCommentLine(String line) {
    return line.isEmpty() || line.startsWith("#");
  }

  private static final Splitter ON_DOUBLE_COLONS = Splitter.on("::").omitEmptyStrings();
  private static final Splitter ON_SPACES_ONCE = Splitter.onPattern("\\s+").limit(2);

  public static ImmutableListMultimap<String, String> parseColonDelimitedLine(final String line)
      throws SubstringMapper.BadMappingsFileException {
    final ImmutableListMultimap.Builder<String, String> ret = ImmutableListMultimap.builder();

    for (final String component : ON_DOUBLE_COLONS.split(line)) {
      final List<String> parts = ON_SPACES_ONCE.splitToList(component);
      if (parts.size() == 2) {
        ret.put(parts.get(0).trim(), parts.get(1));
      } else {
        throw new SubstringMapper.BadMappingsFileException("Cannot parse line: " + line);
      }
    }

    return ret.build();
  }
}
