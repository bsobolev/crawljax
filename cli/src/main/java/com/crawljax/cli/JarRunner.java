package com.crawljax.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.validator.routines.UrlValidator;

import com.crawljax.browser.EmbeddedBrowser.BrowserType;
import com.crawljax.core.CrawljaxController;
import com.crawljax.core.configuration.BrowserConfiguration;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.CrawljaxConfiguration.CrawljaxConfigurationBuilder;
import com.crawljax.plugins.crawloverview.CrawlOverview;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;

public class JarRunner {

	static final String HELP_MESSAGE =
	        "java -jar crawljax-cli-version.jar theUrl theOutputDir";

	static final String VERSION = "version";
	static final String HELP = "help";
	static final String MAXSTATES = "maxstates";
	static final String DEPTH = "depth";
	static final String BROWSER = "browser";
	static final String PARALLEL = "parallel";
	static final String OVERRIDE = "override";

	private static final int SPACES_AFTER_OPTION = 3;
	private static final int SPACES_BEFORE_OPTION = 5;
	private static final int ROW_WIDTH = 80;

	private final CommandLine commandLine;

	private Options options;

	private CrawljaxConfiguration config;

	/**
	 * Main executable method of Crawljax CLI.
	 * 
	 * @param args
	 *            the arguments.
	 */
	public static void main(String[] args) {
		try {
			JarRunner runner = new JarRunner(args);
			runner.runIfConfigured();
		} catch (NumberFormatException e) {
			System.err.println("Could not parse number " + e.getMessage());
			System.exit(1);
		} catch (RuntimeException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	@VisibleForTesting
	JarRunner(String args[]) {
		options = getOptions();
		try {
			commandLine = new GnuParser().parse(options, args);
		} catch (ParseException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
		if (commandLine.hasOption(VERSION)) {
			System.out.println(getCrawljaxVersion());
		} else if (args.length >= 2) {
			String url = commandLine.getArgs()[0];
			String outputDir = commandLine.getArgs()[1];
			if (urlIsInvalid(url)) {
				throw new IllegalArgumentException("provide a valid URL like http://example.com");
			} else {
				checkOutDir(outputDir);
				this.config = readConfig(url, outputDir);
			}
		} else {
			printHelp();
		}
	}

	/**
	 * Create the CML Options.
	 * 
	 * @return Options expected from command-line.
	 */
	private Options getOptions() {
		Options options = new Options();
		options.addOption("h", HELP, false, "print this message");
		options.addOption("v", VERSION, false, "print the version information and exit");

		options.addOption("b", "browser", true,
		        "browser type: " + availableBrowsers() + ". Default is Firefox");

		options.addOption("d", DEPTH, true, "crawl depth level. Default is 2");

		options.addOption("s", MAXSTATES, true,
		        "max number of states to crawl. Default is 0 (unlimited)");

		options.addOption("p", PARALLEL, true,
		        "Number of browsers to use for crawling. Default is 1");
		options.addOption("o", OVERRIDE, false, "Override the output directory if non-empty");
		return options;
	}

	private String availableBrowsers() {
		return Joiner.on(", ").join(BrowserType.values());
	}

	/**
	 * Write "help" to the provided OutputStream.
	 * 
	 * @param options
	 * @throws IOException
	 */
	public void printHelp() {
		String cmlSyntax = HELP_MESSAGE;
		final PrintWriter writer = new PrintWriter(System.out);
		final HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.printHelp(writer, ROW_WIDTH, cmlSyntax, "", options, SPACES_AFTER_OPTION,
		        SPACES_BEFORE_OPTION, "");
		writer.flush();
	}

	private void checkOutDir(String outputDir) {
		File out = new File(outputDir);
		if (out.exists() && out.list().length > 0) {
			if (commandLine.hasOption(OVERRIDE)) {
				System.out.println("Overriding output directory...");
				try {
					FileUtils.deleteDirectory(out);
				} catch (IOException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			} else {
				throw new IllegalStateException(
				        "Output directory is not empty. If you want to override, use the -override option");
			}
		}
	}

	private String getCrawljaxVersion() {
		try {
			return Resources
			        .toString(JarRunner.class.getResource("/project.version"), Charsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private boolean urlIsInvalid(String urlValue) {
		final String[] schemes = { "http", "https" };
		return urlValue == null || !new UrlValidator(schemes).isValid(urlValue);
	}

	private CrawljaxConfiguration readConfig(String urlValue, String outputDir) {
		CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor(urlValue);

		BrowserType browser = BrowserType.firefox;
		if (commandLine.hasOption(BROWSER)) {
			String browserString = commandLine.getOptionValue(BROWSER);
			browser = getBrowserTypeFromStr(browserString);
		}

		int browsers = 1;
		if (commandLine.hasOption(PARALLEL)) {
			browsers = Integer.parseInt(commandLine.getOptionValue(PARALLEL));
		}
		builder.setBrowserConfig(new BrowserConfiguration(browser, browsers));

		if (commandLine.hasOption(DEPTH)) {
			String depth = commandLine.getOptionValue(DEPTH);
			builder.setMaximumDepth(Integer.parseInt(depth));
		}

		if (commandLine.hasOption(MAXSTATES)) {
			String maxstates = commandLine.getOptionValue(MAXSTATES);
			builder.setMaximumStates(Integer.parseInt(maxstates));
		}

		builder.addPlugin(new CrawlOverview(new File(outputDir)));

		builder.crawlRules().clickDefaultElements();

		return builder.build();
	}

	private BrowserType getBrowserTypeFromStr(String browser) {
		if (browser != null) {
			for (BrowserType b : BrowserType.values()) {
				if (browser.equalsIgnoreCase(b.toString())) {
					return b;
				}
			}
		}
		throw new IllegalArgumentException("Unrecognized browser: '" + browser
		        + "'. Available browsers are: " + availableBrowsers());
	}

	private void runIfConfigured() {
		if (config != null) {
			CrawljaxController crawljax = new CrawljaxController(config);
			crawljax.run();
		}
	}

	@VisibleForTesting
	CrawljaxConfiguration getConfig() {
		return config;
	}
}
