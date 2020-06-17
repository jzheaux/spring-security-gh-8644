package org.springframework.security.web.firewall;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.firewall.Gh8644StrictHttpFirewall;

@State(Scope.Benchmark)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
public class Gh8644StrictHttpFirewallTests {
	private static final int MAX_HEADER_SIZE = 8192;

	private Map<String, HttpServletRequest> requests = new HashMap<String, HttpServletRequest>()
	{{
		put("largeBody", largeBody());
		put("largeHeader", largeHeader());
		put("largeBodyAndHeader", largeBodyAndHeader());
	}};

	// To remove a use case, comment it out in the @Param annotation

	@Param({
			"largeBody",
			"largeHeader",
			"largeBodyAndHeader"
	})
	private String which;

	private StrictHttpFirewall firewall = new StrictHttpFirewall();
	private Gh8644StrictHttpFirewall gh8644Firewall = new Gh8644StrictHttpFirewall();

	@Benchmark
	public HttpServletRequest checkingNoChars() {
		return firewall.getFirewalledRequest(requests.get(which));
	}

	@Benchmark
	public HttpServletRequest checkingAllChars() {
		return gh8644Firewall.getFirewalledRequest(requests.get(which));
	}

	private static MockHttpServletRequest largeBodyAndHeader() {
		MockHttpServletRequest large = new MockHttpServletRequest();
		MockHttpServletRequest largeBody = largeBody();
		MockHttpServletRequest largeHeader = largeHeader();
		large.setMethod("GET");
		Enumeration<String> parameterNames = largeBody.getParameterNames();
		while (parameterNames.hasMoreElements()) {
			String parameterName = parameterNames.nextElement();
			large.setParameter(parameterName, largeBody.getParameter(parameterName));
		}
		large.setServerName(largeHeader.getServerName());
		large.addHeader("header", largeHeader.getHeader("header"));
		large.setRequestURI(largeHeader.getRequestURI());
		return large;
	}

	private static MockHttpServletRequest largeBody() {
		try {
			// a large request body
			byte[] body = Files.readAllBytes(Paths.get("two-megabyte-request.log"));

			MockHttpServletRequest request = new MockHttpServletRequest();
			for (int i = 0; i < 10000; i++) {
				byte[] parameterName = new byte[body.length / 10000];
				System.arraycopy(body, i * parameterName.length, parameterName, 0, parameterName.length);
				request.setParameter(new String(parameterName), "v");
			}

			request.setMethod("GET");
			request.setServerName("host");
			request.setRequestURI("/uri");

			// NOTE: This is actually a slightly larger body than is possible in Tomcat, but
			// it is still of the same order

			return request;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static MockHttpServletRequest largeHeader() {
		try {
			// a large request body
			byte[] parameterName = Files.readAllBytes(Paths.get("two-megabyte-request.log"));

			// a large header, containing a long URI, a long Host header, and a long additional header
			byte[] hostHeader = new byte[MAX_HEADER_SIZE / 3];
			System.arraycopy(parameterName, 0, hostHeader, 0, hostHeader.length);

			byte[] anotherHeader = new byte[MAX_HEADER_SIZE / 3];
			System.arraycopy(parameterName, 0, anotherHeader, 0, hostHeader.length);

			byte[] uri = new byte[MAX_HEADER_SIZE / 3];
			System.arraycopy(parameterName, 0, uri, 0, hostHeader.length);

			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setMethod("GET");
			request.setServerName(new String(hostHeader));
			request.addHeader("header", new String(anotherHeader));
			request.setRequestURI("/" + new String(uri));

			request.setParameter("p", "v");

			// NOTE: This is actually a slightly larger header than is possible in Tomcat, but
			// it is still of the same order

			return request;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws Exception {
		org.openjdk.jmh.Main.main(args);
	}
}
