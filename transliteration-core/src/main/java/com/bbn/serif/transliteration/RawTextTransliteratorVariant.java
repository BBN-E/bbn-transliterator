package com.bbn.serif.transliteration;

import com.bbn.bue.common.parameters.Parameters;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import javax.annotation.Nonnull;

import static com.bbn.bue.common.StringUtils.unicodeFriendly;
import static com.bbn.bue.common.parameters.Parameters.joinNamespace;

/**
 * Variant of RawTextTransliterator that process a file and do one of two things for each line:
 *
 * (1) if it has two or more columns (tab-separated), fill the first column with the transliteration
 * of the second
 *
 * (2) if it has only one column, move its contents to the second column and then apply (1) plain
 * text files. Run with no arguments to see usage..
 */
public final class RawTextTransliteratorVariant {

  private static final Logger log = LoggerFactory.getLogger(RawTextTransliteratorVariant.class);

  private static final String NAMESPACE = "com.bbn.nlp.transliteration";
  private static final String DEFAULT_TRANSLITERATOR_ALLOWED =
      joinNamespace(NAMESPACE, "fallbackToDefaultTransliterator");

  private RawTextTransliteratorVariant() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] args) {
    // Wrap the main method to ensure a non-zero return value on failure
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void trueMain(String[] args) throws IOException {
    if (args.length == 1 || args.length == 3) {
      nonInteractiveMode(args);
    } else {
      System.out.println("Usage:\n"
          + "rawTextTransliterator params\n"
          + "rawTextTransliterator [langCode] infile outfile\n");
    }
  }

  private static void nonInteractiveMode(final String[] args) throws IOException {
    final File inputFile;
    final File outputFile;
    final String langCode;
    final Parameters additionalParameters;

    if (args.length == 1) {
      final Parameters params =
          Parameters.loadSerifStyle(new File(args[0])).copyNamespace(NAMESPACE);
      log.info("Run on parameters:\n{}", params.dump());
      langCode = params.getString("iso6392Code");
      inputFile = params.getExistingFile("inputFile");
      outputFile = params.getCreatableFile("outputFile");
      additionalParameters = params;
    } else {
      langCode = args[0];
      inputFile = new File(args[1]);
      outputFile = new File(args[2]);
      additionalParameters = Parameters.builder().build();
    }

    log.info("Loading input from {}", inputFile);
    log.info("Writing output to {}", outputFile);
    log.info("Transliterating for language {}", langCode);

    final Transliterator transliterator = transliteratorForLanguage(langCode, additionalParameters);

    // Open output
    final CharSink output = Files.asCharSink(outputFile, Charsets.UTF_8);
    try (final Writer writer = output.openBufferedStream()) {
      // Process input
      Files.asCharSource(inputFile, Charsets.UTF_8)
          .readLines(new LineNormalizer(transliterator, writer));
    }
  }

  private static Transliterator transliteratorForLanguage(final String langCode,
      final Parameters additionalParameters) {

    final Injector transliteratorInjector =
        Guice.createInjector(new Transliterator.FromParamsModule(additionalParameters));

    final SetMultimap<String, Transliterator> langCodeToTransliterators =
        transliteratorInjector.getInstance(
            Key.get(new TypeLiteral<SetMultimap<String, Transliterator>>() {
                    },
                Transliterator.Iso6392ToTransliterator.class));

    if (additionalParameters.getOptionalBoolean(DEFAULT_TRANSLITERATOR_ALLOWED).or(false)) {
      final Transliterator generalTransliterator = transliteratorInjector.getInstance(
          Key.get(Transliterator.class, Transliterator.GeneralTransliterator.class));
      return Transliterators.requestUniqueTransliteratorForLanguageCode(langCode,
          langCodeToTransliterators, generalTransliterator);
    } else {
      return Transliterators.requestUniqueTransliteratorForLanguageCode(langCode,
          langCodeToTransliterators);
    }
  }


  /**
   * A non-standard use of {@link LineProcessor} to process without returning anything.
   */
  private static class LineNormalizer implements LineProcessor {

    private final Transliterator transliterator;
    private final Writer output;

    private LineNormalizer(final Transliterator transliterator, final Writer output) {
      this.transliterator = transliterator;
      this.output = output;
    }

    @Override
    public boolean processLine(@Nonnull final String line) throws IOException {
      final List<String> input = Splitter.on('\t').splitToList(line);
      final ImmutableList.Builder<String> builder = ImmutableList.builder();

      if (input.size() == 1) {
        builder.add(transliterator.transliterate(unicodeFriendly(input.get(0))).utf16CodeUnits());
        builder.add(input.get(0));
      } else {
        builder.add(transliterator.transliterate(unicodeFriendly(input.get(1))).utf16CodeUnits());
        builder.addAll(input.subList(1, input.size()));
      }
      output.write(Joiner.on('\t').join(builder.build()));
      output.write("\n");
      return true;
    }

    /**
     * The result should never be used.
     */
    @Override
    public Object getResult() {
      return null;
    }
  }
}

