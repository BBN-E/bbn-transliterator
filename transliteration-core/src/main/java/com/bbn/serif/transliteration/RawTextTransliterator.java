package com.bbn.serif.transliteration;

import com.bbn.bue.common.parameters.Parameters;

import com.google.common.base.Charsets;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.annotation.Nonnull;

import static com.bbn.bue.common.StringUtils.unicodeFriendly;
import static com.bbn.bue.common.parameters.Parameters.joinNamespace;

/**
 * Transliterates plain text files. Run with no arguments to see usage.
 *
 * For safety, this will refuse to fall back on the default transliterator unless
 * {@code com.bbn.nlp.transliteration.fallbackToDefaultTransliterator} is set to
 * {@code true}.
 *
 * Note you can usually also use this on XML because the default transliterator does not
 * alter the special characters used in XML tags and entities.
 */
public final class RawTextTransliterator {

  private static final Logger log = LoggerFactory.getLogger(RawTextTransliterator.class);

  private static final String NAMESPACE = "com.bbn.nlp.transliteration";
  private static final String DEFAULT_TRANSLITERATOR_ALLOWED =
      joinNamespace(NAMESPACE, "fallbackToDefaultTransliterator");

  private RawTextTransliterator() {
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
    if (args.length == 2 && args[1].equals("-")) {
      interactiveMode(args[1]);
    } else if (args.length == 1 || args.length == 3) {
      nonInteractiveMode(args);
    } else {
      System.out.println("Usage:\n"
          + "rawTextTransliterator params\n"
          + "rawTextTransliterator [langCode] infile outfile\n"
          + "rawTextTransliterator [langCode] -\n"
          + "Use - for stdin/stdout, which works interactively");
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

  private static void interactiveMode(final String langCode) throws IOException {
    final Transliterator transliterator = transliteratorForLanguage(langCode,
        Parameters.builder().build());
    // STDIN/STDOUT mode
    final BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    final Writer output = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));
    String line;
    while ((line = input.readLine()) != null) {
      output.write(transliterator.transliterate(unicodeFriendly(line)) + "\n");
      // This degrades performance but allows it to run interactively
      output.flush();
    }
  }

  private static Transliterator transliteratorForLanguage(final String langCode,
      final Parameters additionalParameters) {

    final Injector transliteratorInjector =
        Guice.createInjector(new Transliterator.FromParamsModule(additionalParameters));

    final SetMultimap<String, Transliterator> langCodeToTransliterators =
        transliteratorInjector.getInstance(
            Key.get(new TypeLiteral<SetMultimap<String, Transliterator>> () {},
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
      output.write(transliterator.transliterate(unicodeFriendly(line)).utf16CodeUnits());
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

