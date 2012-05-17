package test.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import test.lib.NanoHTTPD;
import winstone.Launcher;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import aQute.bnd.service.url.TaggedData;
import aQute.lib.deployer.http.DefaultURLConnector;
import aQute.lib.deployer.http.HttpBasicAuthURLConnector;
import aQute.lib.io.IO;

public class HttpConnectorTest extends TestCase {

	private static final int HTTP_PORT       = 18081;
	private static final int HTTP_PORT_ALT   = 18082;
	private static final int HTTPS_PORT      = 18443;
	
	private static final String HTTP_URL_PREFIX       = "http://127.0.0.1:"  + HTTP_PORT     + "/";
	private static final String HTTP_URL_PREFIX_AUTH   = "http://127.0.0.1:"  + HTTP_PORT_ALT + "/";
	private static final String HTTPS_URL_PREFIX      = "https://127.0.0.1:" + HTTPS_PORT    + "/";
	
	private static final String EXPECTED_ETAG = "64035a95";

	private NanoHTTPD httpd;
	
	private static Launcher winstoneHttp = null;
	private static Launcher winstoneHttps = null;
	
	private boolean createdWinstoneHttp = false;
	private boolean createdWinstoneHttps = false;
	
	private static Launcher launchWinstone(boolean ssl) throws Exception {
		Map<String, String> args = new HashMap<String, String>();
		args.put("webroot", new File("testdata/winstone").getPath());
		args.put("debug", "5");
		
		if (ssl) {
			args.put("httpPort", "-1");
			args.put("httpsPort", ""+ HTTPS_PORT);
			args.put("httpsKeyStore", "example.keystore ");
			args.put("httpsKeyStorePassword", "opensesame");
		} else {
			args.put("httpPort", "" + HTTP_PORT_ALT);
		}
		args.put("ajp13Port", "-1");
		args.put("argumentsRealm.passwd.AliBaba", "OpenSesame");
		args.put("argumentsRealm.roles.AliBaba", "users");
		
		Launcher.initLogger(args);
		Launcher launcher = new Launcher(args);
		return launcher;
	}

	public static Test suite() {
		TestSuite suite = new TestSuite(HttpConnectorTest.class);
		return new TestSetup(suite) {
			
			@Override
			protected void setUp() throws Exception {
				winstoneHttp = launchWinstone(false);
				winstoneHttps = launchWinstone(true);
				Thread.sleep(2000);
			}
			@Override
			protected void tearDown() throws Exception {
				winstoneHttp.shutdown();
				winstoneHttps.shutdown();
				Thread.sleep(2000);
			}
		};
	}

	@Override
	protected void setUp() throws Exception {
		File tmpFile = File.createTempFile("cache", ".tmp");
		tmpFile.deleteOnExit();

		httpd = new NanoHTTPD(HTTP_PORT, new File("testdata/http"));
		
		if (winstoneHttp == null) {
			System.err.println("Creating Winstone HTTP for each test!?");
			winstoneHttp = launchWinstone(false);
			createdWinstoneHttp = true;
			Thread.sleep(2000);
		}
		if (winstoneHttps == null) {
			System.err.println("Creating Winstone HTTPS for each test!?");
			winstoneHttps = launchWinstone(true);
			createdWinstoneHttps = true;
			Thread.sleep(2000);
		}
	}

	@Override
	protected void tearDown() throws Exception {
		httpd.stop();
		if (createdWinstoneHttp) {
			winstoneHttp.shutdown();
			Thread.sleep(2000);
		}
		if (createdWinstoneHttps) {
			winstoneHttps.shutdown();
			Thread.sleep(2000);
		}
	}

	public void testConnectTagged() throws Exception {
		DefaultURLConnector connector = new DefaultURLConnector();

		TaggedData data = connector.connectTagged(new URL(HTTP_URL_PREFIX + "bundles/dummybundle.jar"));
		assertNotNull("Data should be non-null because ETag not provided", data);
		data.getInputStream().close();
		assertEquals("ETag is incorrect", EXPECTED_ETAG, data.getTag());
	}
	
	public void testConnectKnownTag() throws Exception {
		DefaultURLConnector connector = new DefaultURLConnector();

		TaggedData data = connector.connectTagged(new URL(HTTP_URL_PREFIX + "bundles/dummybundle.jar"), EXPECTED_ETAG);
		assertNull("Data should be null since ETag not modified.", data);
	}

	public void testConnectTagModified() throws Exception {
		DefaultURLConnector connector = new DefaultURLConnector();

		TaggedData data = connector.connectTagged(new URL(HTTP_URL_PREFIX + "bundles/dummybundle.jar"), "00000000");
		assertNotNull("Data should be non-null because ETag was different", data);
		data.getInputStream().close();
		assertEquals("ETag is incorrect", EXPECTED_ETAG, data.getTag());
	}
	
	public void testConnectHTTPS() throws Exception {
		DefaultURLConnector connector = new DefaultURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("disableServerVerify", "true");
		connector.setProperties(config);
		
		InputStream stream = connector.connect(new URL(HTTPS_URL_PREFIX + "bundles/dummybundle.jar"));
		assertNotNull(stream);
		stream.close();
	}
	
	public void testConnectHTTPSBadCerficate() throws Exception {
		DefaultURLConnector connector = new DefaultURLConnector();
		
		InputStream stream = null;
		try {
			stream = connector.connect(new URL(HTTPS_URL_PREFIX + "bundles/dummybundle.jar"));
			fail ("Expected SSLHandsakeException");
		} catch (SSLHandshakeException e) {
			// expected
		} finally {
			if (stream != null) IO.close(stream);
		}
	}
	
	public void testConnectTaggedHTTPS() throws Exception {
		DefaultURLConnector connector = new DefaultURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("disableServerVerify", "true");
		connector.setProperties(config);
		
		TaggedData data = connector.connectTagged(new URL(HTTPS_URL_PREFIX + "bundles/dummybundle.jar"));
		assertNotNull(data);
		data.getInputStream().close();
	}
	
	public void testConnectTaggedHTTPSBadCerficate() throws Exception {
		DefaultURLConnector connector = new DefaultURLConnector();
		
		InputStream stream = null;
		try {
			connector.connectTagged(new URL(HTTPS_URL_PREFIX + "bundles/dummybundle.jar"));
			fail ("Expected SSLHandsakeException");
		} catch (SSLHandshakeException e) {
			// expected
		} finally {
			if (stream != null) IO.close(stream);
		}
	}
	public void testConnectNoUserPass() throws Exception {
		HttpBasicAuthURLConnector connector = new HttpBasicAuthURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("configs", "");
		connector.setProperties(config);
		
		try {
			connector.connect(new URL(HTTP_URL_PREFIX_AUTH + "securebundles/dummybundle.jar"));
			fail("Should have thrown IOException due to missing auth");
		} catch (IOException e) {
			// expected
			assertTrue(e.getMessage().startsWith("Server returned HTTP response code: 401"));
		}
	}
	
	public void testConnectWithUserPass() throws Exception {
		HttpBasicAuthURLConnector connector = new HttpBasicAuthURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("configs", "testdata/http_auth.properties");
		connector.setProperties(config);
		
		InputStream stream = connector.connect(new URL(HTTP_URL_PREFIX_AUTH + "securebundles/dummybundle.jar"));
		assertNotNull(stream);
	}
	
	public static void testConnectHTTPSBadCertificate() throws Exception {
		HttpBasicAuthURLConnector connector = new HttpBasicAuthURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("configs", "testdata/http_auth.properties");
		connector.setProperties(config);
		
		try {
			connector.connect(new URL(HTTPS_URL_PREFIX + "securebundles/dummybundle.jar"));
			fail ("Should have thrown error: invalid server certificate");
		} catch (IOException e) {
			// expected
			assertTrue(e instanceof SSLHandshakeException);
		}
	}
	
	public static void testConnectWithUserPassHTTPS() throws Exception {
		HttpBasicAuthURLConnector connector = new HttpBasicAuthURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("configs", "testdata/http_auth.properties");
		config.put("disableServerVerify", "true");
		connector.setProperties(config);
		
		try {
			InputStream stream = connector.connect(new URL(HTTPS_URL_PREFIX + "securebundles/dummybundle.jar"));
			assertNotNull(stream);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	public void testConnectWithWrongUserPass() throws Exception {
		HttpBasicAuthURLConnector connector = new HttpBasicAuthURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("configs", "testdata/http_auth_wrong.properties");
		connector.setProperties(config);
		
		try {
			connector.connect(new URL(HTTP_URL_PREFIX_AUTH + "securebundles/dummybundle.jar"));
			fail("Should have thrown IOException due to incorrect auth");
		} catch (IOException e) {
			// expected
			assertTrue(e.getMessage().startsWith("Server returned HTTP response code: 401"));
		}
	}
	
	public void testConnectWithWrongUserPassHTTPS() throws Exception {
		HttpBasicAuthURLConnector connector = new HttpBasicAuthURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("configs", "testdata/http_auth_wrong.properties");
		config.put("disableServerVerify", "true");
		connector.setProperties(config);
		
		try {
			connector.connect(new URL(HTTPS_URL_PREFIX + "securebundles/dummybundle.jar"));
			fail("Should have thrown IOException due to incorrect auth");
		} catch (IOException e) {
			// expected
			assertTrue(e.getMessage().startsWith("Server returned HTTP response code: 401"));
		}
	}

	/**
	 * Can no long do this test because Winstone doesn't support ETags
	 */
	public void XXXtestConnectWithUserPassAndTag() throws Exception {
		HttpBasicAuthURLConnector connector = new HttpBasicAuthURLConnector();
		Map<String, String> config = new HashMap<String, String>();
		config.put("configs", "testdata/http_auth.properties");
		connector.setProperties(config);
		
		TaggedData data = connector.connectTagged(new URL(HTTP_URL_PREFIX_AUTH + "securebundles/dummybundle.jar"), EXPECTED_ETAG);
		assertNull("Data should be null because resource not modified", data);
	}
	

}
