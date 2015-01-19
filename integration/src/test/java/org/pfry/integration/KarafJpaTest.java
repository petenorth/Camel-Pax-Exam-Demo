package org.pfry.integration;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Dictionary;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.features.FeaturesService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.pfry.service.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.nierbeck.camel.exam.demo.testutil.TestUtility;

@RunWith(PaxExam.class)
public class KarafJpaTest {

	protected transient Logger log = LoggerFactory.getLogger(getClass());

	ExecutorService executor = Executors.newCachedThreadPool();

	static final Long COMMAND_TIMEOUT = 10000L;
	static final Long DEFAULT_TIMEOUT = 20000L;
	static final Long SERVICE_TIMEOUT = 30000L;

	@Inject
	protected FeaturesService featuresService;

	@Inject
	protected BundleContext bundleContext;

	@Inject
	protected DataSource dataSource;
	
	@Inject
	protected PersonService personService;

	@Configuration
	public static Option[] configure() throws Exception {
		return new Option[] {
				karafDistributionConfiguration()
						.frameworkUrl(
								maven().groupId("org.apache.karaf").artifactId("apache-karaf").type("zip")
										.versionAsInProject()).useDeployFolder(false).karafVersion("3.0.0")
						.unpackDirectory(new File("target/paxexam/unpack/")),
				
				logLevel(LogLevel.WARN),
				
				features(
						maven().groupId("org.apache.karaf.features").artifactId("standard").type("xml")
								.classifier("features").versionAsInProject(), "http-whiteboard"),
				features(
						maven().groupId("org.apache.karaf.features").artifactId("enterprise").type("xml")
								.classifier("features").versionAsInProject(), "transaction", "jpa", "jndi"),
				features(
				maven().groupId("org.apache.camel.karaf").artifactId("apache-camel").type("xml")
								.classifier("features").versionAsInProject(), "camel-jpa"),								
				
				KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg",
						"org.ops4j.pax.url.mvn.proxySupport", "true"),
				keepRuntimeFolder(),
				
				mavenBundle().groupId("com.h2database").artifactId("h2").version("1.3.167"),
				mavenBundle().groupId("org.pfry").artifactId("entities").versionAsInProject(),
				mavenBundle().groupId("org.ops4j.pax.tipi").artifactId("org.ops4j.pax.tipi.hamcrest.core")
						.versionAsInProject(),
				streamBundle(
						bundle().add("OSGI-INF/blueprint/datasource.xml",
								new File("src/sample/resources/datasource.xml").toURL())
								.set(Constants.BUNDLE_SYMBOLICNAME, "org.pfry.integration.datasource")
								.set(Constants.DYNAMICIMPORT_PACKAGE, "*").build()).start()};
	}
	
	@Test
	public void testPfry() throws Exception {
		assertTrue(featuresService.isInstalled(featuresService
				.getFeature("jpa")));
		assertTrue(featuresService.isInstalled(featuresService
				.getFeature("transaction")));
		assertTrue(featuresService.isInstalled(featuresService
				.getFeature("jndi")));
		personService.addPerson(1, "David");
		org.pfry.entities.Person person = personService.getPerson(1);
		assertEquals("David",person.getName());
	}	

	// Below are methods used for testing --> should be moved outside of
	// testclass

	/**
	 * Executes a shell command and returns output as a String. Commands have a
	 * default timeout of 10 seconds.
	 * 
	 * @param command
	 * @return
	 */
	protected String executeCommand(final String command) {
		return executeCommand(command, COMMAND_TIMEOUT, false);
	}

	/**
	 * Executes a shell command and returns output as a String. Commands have a
	 * default timeout of 10 seconds.
	 * 
	 * @param command
	 *            The command to execute.
	 * @param timeout
	 *            The amount of time in millis to wait for the command to
	 *            execute.
	 * @param silent
	 *            Specifies if the command should be displayed in the screen.
	 * @return
	 */
	protected String executeCommand(final String command, final Long timeout, final Boolean silent) {
		String response;
		final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		final PrintStream printStream = new PrintStream(byteArrayOutputStream);
		final CommandProcessor commandProcessor = getOsgiService(CommandProcessor.class);
		final CommandSession commandSession = commandProcessor.createSession(System.in, printStream, System.err);
		FutureTask<String> commandFuture = new FutureTask<String>(new Callable<String>() {
			public String call() {
				try {
					if (!silent) {
						System.err.println(command);
					}
					commandSession.execute(command);
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
				printStream.flush();
				return byteArrayOutputStream.toString();
			}
		});

		try {
			executor.submit(commandFuture);
			response = commandFuture.get(timeout, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			e.printStackTrace(System.err);
			response = "SHELL COMMAND TIMED OUT: ";
		}

		return response;
	}

	/**
	 * Executes multiple commands inside a Single Session. Commands have a
	 * default timeout of 10 seconds.
	 * 
	 * @param commands
	 * @return
	 */
	protected String executeCommands(final String... commands) {
		String response;
		final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		final PrintStream printStream = new PrintStream(byteArrayOutputStream);
		final CommandProcessor commandProcessor = getOsgiService(CommandProcessor.class);
		final CommandSession commandSession = commandProcessor.createSession(System.in, printStream, System.err);
		FutureTask<String> commandFuture = new FutureTask<String>(new Callable<String>() {
			public String call() {
				try {
					for (String command : commands) {
						System.err.println(command);
						commandSession.execute(command);
					}
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
				return byteArrayOutputStream.toString();
			}
		});

		try {
			executor.submit(commandFuture);
			response = commandFuture.get(COMMAND_TIMEOUT, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			e.printStackTrace(System.err);
			response = "SHELL COMMAND TIMED OUT: ";
		}

		return response;
	}

	protected <T> T getOsgiService(Class<T> type, long timeout) {
		return getOsgiService(type, null, timeout);
	}

	protected <T> T getOsgiService(Class<T> type) {
		return getOsgiService(type, null, SERVICE_TIMEOUT);
	}

	protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
		ServiceTracker tracker = null;
		try {
			String flt;
			if (filter != null) {
				if (filter.startsWith("(")) {
					flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
				} else {
					flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
				}
			} else {
				flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
			}
			Filter osgiFilter = FrameworkUtil.createFilter(flt);
			tracker = new ServiceTracker(bundleContext, osgiFilter, null);
			tracker.open(true);
			// Note that the tracker is not closed to keep the reference
			// This is buggy, as the service reference may change i think
			Object svc = type.cast(tracker.waitForService(timeout));
			if (svc == null) {
				Dictionary dic = bundleContext.getBundle().getHeaders();
				System.err.println("Test bundle headers: " + TestUtility.explode(dic));

				for (ServiceReference ref : TestUtility.asCollection(bundleContext.getAllServiceReferences(null, null))) {
					System.err.println("ServiceReference: " + ref);
				}

				for (ServiceReference ref : TestUtility.asCollection(bundleContext.getAllServiceReferences(null, flt))) {
					System.err.println("Filtered ServiceReference: " + ref);
				}

				throw new RuntimeException("Gave up waiting for service " + flt);
			}
			return type.cast(svc);
		} catch (InvalidSyntaxException e) {
			throw new IllegalArgumentException("Invalid filter", e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}
