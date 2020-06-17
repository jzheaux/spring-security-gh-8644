package org.springframework.security.firewall;

/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.util.CollectionUtils;

/**
 * <p>
 * A strict implementation of {@link HttpFirewall} that rejects any suspicious requests
 * with a {@link RequestRejectedException}.
 * </p>
 * <p>
 * The following rules are applied to the firewall:
 * </p>
 * <ul>
 * <li>
 * Rejects HTTP methods that are not allowed. This specified to block
 * <a href="https://www.owasp.org/index.php/Test_HTTP_Methods_(OTG-CONFIG-006)">HTTP Verb tampering and XST attacks</a>.
 * See {@link #setAllowedHttpMethods(Collection)}
 * </li>
 * <li>
 * Rejects URLs that are not normalized to avoid bypassing security constraints. There is
 * no way to disable this as it is considered extremely risky to disable this constraint.
 * A few options to allow this behavior is to normalize the request prior to the firewall
 *
 * request is fragile and why requests are rejected rather than normalized.
 * </li>
 * <li>
 * Rejects URLs that contain characters that are not printable ASCII characters. There is
 * no way to disable this as it is considered extremely risky to disable this constraint.
 * </li>
 * <li>
 * Rejects URLs that contain semicolons. See {@link #setAllowSemicolon(boolean)}
 * </li>
 * <li>
 * Rejects URLs that contain a URL encoded slash. See
 * {@link #setAllowUrlEncodedSlash(boolean)}
 * </li>
 * <li>
 * Rejects URLs that contain a backslash. See {@link #setAllowBackSlash(boolean)}
 * </li>
 * <li>
 * Rejects URLs that contain a null character. See {@link #setAllowNull(boolean)}
 * </li>
 * <li>
 * Rejects URLs that contain a URL encoded percent. See
 * {@link #setAllowUrlEncodedPercent(boolean)}
 * </li>
 * <li>
 * Rejects hosts that are not allowed. See
 * {@link #setAllowedHostnames(Predicate)}
 * </li>
 * <li>
 * Reject headers names that are not allowed. See
 * {@link #setAllowedHeaderNames(Predicate)}
 * </li>
 * <li>
 * Reject headers values that are not allowed. See
 * {@link #setAllowedHeaderValues(Predicate)}
 * </li>
 * <li>
 * Reject parameter names that are not allowed. See
 * {@link #setAllowedParameterNames(Predicate)}
 * </li>
 * <li>
 * Reject parameter values that are not allowed. See
 * {@link #setAllowedParameterValues(Predicate)}
 * </li>
 * </ul>
 * @author Rob Winch
 * @author Eddú Meléndez
 * @since 4.2.4
 */
public class Gh8644StrictHttpFirewall implements HttpFirewall {
	/**
	 * Used to specify to {@link #setAllowedHttpMethods(Collection)} that any HTTP method should be allowed.
	 */
	private static final Set<String> ALLOW_ANY_HTTP_METHOD = Collections.unmodifiableSet(Collections.emptySet());

	private static final String ENCODED_PERCENT = "%25";

	private static final String PERCENT = "%";

	private static final List<String> FORBIDDEN_ENCODED_PERIOD = Collections.unmodifiableList(Arrays.asList("%2e", "%2E"));

	private static final List<String> FORBIDDEN_SEMICOLON = Collections.unmodifiableList(Arrays.asList(";", "%3b", "%3B"));

	private static final List<String> FORBIDDEN_FORWARDSLASH = Collections.unmodifiableList(Arrays.asList("%2f", "%2F"));

	private static final List<String> FORBIDDEN_DOUBLE_FORWARDSLASH = Collections.unmodifiableList(Arrays.asList("//", "%2f%2f", "%2f%2F", "%2F%2f", "%2F%2F"));

	private static final List<String> FORBIDDEN_BACKSLASH = Collections.unmodifiableList(Arrays.asList("\\", "%5c", "%5C"));

	private static final List<String> FORBIDDEN_NULL = Collections.unmodifiableList(Arrays.asList("\0", "%00"));

	private Set<String> encodedUrlBlocklist = new HashSet<>();

	private Set<String> decodedUrlBlocklist = new HashSet<>();

	private Set<String> allowedHttpMethods = createDefaultAllowedHttpMethods();

	private Predicate<String> allowedHostnames = hostname -> true;

	private Predicate<Iterable<String>> allowedHeaderNames = names -> true;

	private Predicate<Iterable<String>> allowedHeaderValues = values -> true;

	private Predicate<Iterable<String>> allowedParameterNames = names -> true;

	private Predicate<Iterable<String>> allowedParameterValues = value -> true;

	public Gh8644StrictHttpFirewall() {
		urlBlocklistsAddAll(FORBIDDEN_SEMICOLON);
		urlBlocklistsAddAll(FORBIDDEN_FORWARDSLASH);
		urlBlocklistsAddAll(FORBIDDEN_DOUBLE_FORWARDSLASH);
		urlBlocklistsAddAll(FORBIDDEN_BACKSLASH);
		urlBlocklistsAddAll(FORBIDDEN_NULL);

		this.encodedUrlBlocklist.add(ENCODED_PERCENT);
		this.encodedUrlBlocklist.addAll(FORBIDDEN_ENCODED_PERIOD);
		this.decodedUrlBlocklist.add(PERCENT);
	}

	/**
	 * Sets if any HTTP method is allowed. If this set to true, then no validation on the HTTP method will be performed.
	 * This can open the application up to <a href="https://www.owasp.org/index.php/Test_HTTP_Methods_(OTG-CONFIG-006)">
	 * HTTP Verb tampering and XST attacks</a>
	 * @param unsafeAllowAnyHttpMethod if true, disables HTTP method validation, else resets back to the defaults. Default is false.
	 * @see #setAllowedHttpMethods(Collection)
	 * @since 5.1
	 */
	public void setUnsafeAllowAnyHttpMethod(boolean unsafeAllowAnyHttpMethod) {
		this.allowedHttpMethods = unsafeAllowAnyHttpMethod ? ALLOW_ANY_HTTP_METHOD : createDefaultAllowedHttpMethods();
	}

	/**
	 * <p>
	 * Determines which HTTP methods should be allowed. The default is to allow "DELETE", "GET", "HEAD", "OPTIONS",
	 * "PATCH", "POST", and "PUT".
	 * </p>
	 *
	 * @param allowedHttpMethods the case-sensitive collection of HTTP methods that are allowed.
	 * @see #setUnsafeAllowAnyHttpMethod(boolean)
	 * @since 5.1
	 */
	public void setAllowedHttpMethods(Collection<String> allowedHttpMethods) {
		if (allowedHttpMethods == null) {
			throw new IllegalArgumentException("allowedHttpMethods cannot be null");
		}
		if (allowedHttpMethods == ALLOW_ANY_HTTP_METHOD) {
			this.allowedHttpMethods = ALLOW_ANY_HTTP_METHOD;
		} else {
			this.allowedHttpMethods = new HashSet<>(allowedHttpMethods);
		}
	}

	/**
	 * <p>
	 * Determines if semicolon is allowed in the URL (i.e. matrix variables). The default
	 * is to disable this behavior because it is a common way of attempting to perform
	 * <a href="https://www.owasp.org/index.php/Reflected_File_Download">Reflected File Download Attacks</a>.
	 * It is also the source of many exploits which bypass URL based security.
	 * </p>
	 * <p>For example, the following CVEs are a subset of the issues related
	 * to ambiguities in the Servlet Specification on how to treat semicolons that
	 * led to CVEs:
	 * </p>
	 * <ul>
	 *     <li><a href="https://pivotal.io/security/cve-2016-5007">cve-2016-5007</a></li>
	 *     <li><a href="https://pivotal.io/security/cve-2016-9879">cve-2016-9879</a></li>
	 *     <li><a href="https://pivotal.io/security/cve-2018-1199">cve-2018-1199</a></li>
	 * </ul>
	 *
	 * <p>
	 * If you are wanting to allow semicolons, please reconsider as it is a very common
	 * source of security bypasses. A few common reasons users want semicolons and
	 * alternatives are listed below:
	 * </p>
	 * <ul>
	 * <li>Including the JSESSIONID in the path - You should not include session id (or
	 * any sensitive information) in a URL as it can lead to leaking. Instead use Cookies.
	 * </li>
	 * <li>Matrix Variables - Users wanting to leverage Matrix Variables should consider
	 * using HTTP parameters instead.
	 * </li>
	 * </ul>
	 *
	 * @param allowSemicolon should semicolons be allowed in the URL. Default is false
	 */
	public void setAllowSemicolon(boolean allowSemicolon) {
		if (allowSemicolon) {
			urlBlocklistsRemoveAll(FORBIDDEN_SEMICOLON);
		} else {
			urlBlocklistsAddAll(FORBIDDEN_SEMICOLON);
		}
	}

	/**
	 * <p>
	 * Determines if a slash "/" that is URL encoded "%2F" should be allowed in the path
	 * or not. The default is to not allow this behavior because it is a common way to
	 * bypass URL based security.
	 * </p>
	 * <p>
	 * For example, due to ambiguities in the servlet specification, the value is not
	 * parsed consistently which results in different values in {@code HttpServletRequest}
	 * path related values which allow bypassing certain security constraints.
	 * </p>
	 *
	 * @param allowUrlEncodedSlash should a slash "/" that is URL encoded "%2F" be allowed
	 * in the path or not. Default is false.
	 */
	public void setAllowUrlEncodedSlash(boolean allowUrlEncodedSlash) {
		if (allowUrlEncodedSlash) {
			urlBlocklistsRemoveAll(FORBIDDEN_FORWARDSLASH);
		} else {
			urlBlocklistsAddAll(FORBIDDEN_FORWARDSLASH);
		}
	}

	/**
	 * <p>
	 * Determines if double slash "//" that is URL encoded "%2F%2F" should be allowed in the path or
	 * not. The default is to not allow.
	 * </p>
	 *
	 * @param allowUrlEncodedDoubleSlash should a slash "//" that is URL encoded "%2F%2F" be allowed
	 *        in the path or not. Default is false.
	 */
	public void setAllowUrlEncodedDoubleSlash(boolean allowUrlEncodedDoubleSlash) {
		if (allowUrlEncodedDoubleSlash) {
			urlBlocklistsRemoveAll(FORBIDDEN_DOUBLE_FORWARDSLASH);
		} else {
			urlBlocklistsAddAll(FORBIDDEN_DOUBLE_FORWARDSLASH);
		}
	}

	/**
	 * <p>
	 * Determines if a period "." that is URL encoded "%2E" should be allowed in the path
	 * or not. The default is to not allow this behavior because it is a frequent source
	 * of security exploits.
	 * </p>
	 * <p>
	 * For example, due to ambiguities in the servlet specification a URL encoded period
	 * might lead to bypassing security constraints through a directory traversal attack.
	 * This is because the path is not parsed consistently which results  in different
	 * values in {@code HttpServletRequest} path related values which allow bypassing
	 * certain security constraints.
	 * </p>
	 *
	 * @param allowUrlEncodedPeriod should a period "." that is URL encoded "%2E" be
	 * allowed in the path or not. Default is false.
	 */
	public void setAllowUrlEncodedPeriod(boolean allowUrlEncodedPeriod) {
		if (allowUrlEncodedPeriod) {
			this.encodedUrlBlocklist.removeAll(FORBIDDEN_ENCODED_PERIOD);
		} else {
			this.encodedUrlBlocklist.addAll(FORBIDDEN_ENCODED_PERIOD);
		}
	}

	/**
	 * <p>
	 * Determines if a backslash "\" or a URL encoded backslash "%5C" should be allowed in
	 * the path or not. The default is not to allow this behavior because it is a frequent
	 * source of security exploits.
	 * </p>
	 * <p>
	 * For example, due to ambiguities in the servlet specification a URL encoded period
	 * might lead to bypassing security constraints through a directory traversal attack.
	 * This is because the path is not parsed consistently which results  in different
	 * values in {@code HttpServletRequest} path related values which allow bypassing
	 * certain security constraints.
	 * </p>
	 *
	 * @param allowBackSlash a backslash "\" or a URL encoded backslash "%5C" be allowed
	 * in the path or not. Default is false
	 */
	public void setAllowBackSlash(boolean allowBackSlash) {
		if (allowBackSlash) {
			urlBlocklistsRemoveAll(FORBIDDEN_BACKSLASH);
		} else {
			urlBlocklistsAddAll(FORBIDDEN_BACKSLASH);
		}
	}

	/**
	 * <p>
	 * Determines if a null "\0" or a URL encoded nul "%00" should be allowed in
	 * the path or not. The default is not to allow this behavior because it is a frequent
	 * source of security exploits.
	 * </p>
	 *
	 * @param allowNull a null "\0" or a URL encoded null "%00" be allowed
	 * in the path or not. Default is false
	 */
	public void setAllowNull(boolean allowNull) {
		if (allowNull) {
			urlBlocklistsRemoveAll(FORBIDDEN_NULL);
		} else {
			urlBlocklistsAddAll(FORBIDDEN_NULL);
		}
	}

	/**
	 * <p>
	 * Determines if a percent "%" that is URL encoded "%25" should be allowed in the path
	 * or not. The default is not to allow this behavior because it is a frequent source
	 * of security exploits.
	 * </p>
	 * <p>
	 * For example, this can lead to exploits that involve double URL encoding that lead
	 * to bypassing security constraints.
	 * </p>
	 *
	 * @param allowUrlEncodedPercent if a percent "%" that is URL encoded "%25" should be
	 * allowed in the path or not. Default is false
	 */
	public void setAllowUrlEncodedPercent(boolean allowUrlEncodedPercent) {
		if (allowUrlEncodedPercent) {
			this.encodedUrlBlocklist.remove(ENCODED_PERCENT);
			this.decodedUrlBlocklist.remove(PERCENT);
		} else {
			this.encodedUrlBlocklist.add(ENCODED_PERCENT);
			this.decodedUrlBlocklist.add(PERCENT);
		}
	}

	/**
	 * <p>
	 * Determines which hostnames should be allowed. The default is to allow any hostname.
	 * </p>
	 *
	 * @param allowedHostnames the predicate for testing hostnames
	 * @since 5.2
	 */
	public void setAllowedHostnames(Predicate<String> allowedHostnames) {
		if (allowedHostnames == null) {
			throw new IllegalArgumentException("allowedHostnames cannot be null");
		}
		this.allowedHostnames = allowedHostnames;
	}

	private void urlBlocklistsAddAll(Collection<String> values) {
		this.encodedUrlBlocklist.addAll(values);
		this.decodedUrlBlocklist.addAll(values);
	}

	/**
	 * <p>
	 * Determines which header names should be allowed.
	 * The default is to reject header names that contain ISO control characters
	 * and characters that are not defined.
	 * </p>
	 *
	 * @param allowedHeaderNames the predicate for testing header names
	 * @see Character#isISOControl(int)
	 * @see Character#isDefined(int)
	 * @since 5.4
	 */
	public void setAllowedHeaderNames(Predicate<Iterable<String>> allowedHeaderNames) {
		if (allowedHeaderNames == null) {
			throw new IllegalArgumentException("allowedHeaderNames cannot be null");
		}
		this.allowedHeaderNames = allowedHeaderNames;
	}

	/**
	 * <p>
	 * Determines which header values should be allowed.
	 * The default is to reject header values that contain ISO control characters
	 * and characters that are not defined.
	 * </p>
	 *
	 * @param allowedHeaderValues the predicate for testing hostnames
	 * @see Character#isISOControl(int)
	 * @see Character#isDefined(int)
	 * @since 5.4
	 */
	public void setAllowedHeaderValues(Predicate<Iterable<String>> allowedHeaderValues) {
		if (allowedHeaderValues == null) {
			throw new IllegalArgumentException("allowedHeaderValues cannot be null");
		}
		this.allowedHeaderValues = allowedHeaderValues;
	}

	private void urlBlocklistsRemoveAll(Collection<String> values) {
		this.encodedUrlBlocklist.removeAll(values);
		this.decodedUrlBlocklist.removeAll(values);
	}

	/**
	 * <p>
	 * Determines which parameter names should be allowed.
	 * The default is to reject header names that contain ISO control characters
	 * and characters that are not defined.
	 * </p>
	 *
	 * @param allowedParameterNames the predicate for testing parameter names
	 * @see Character#isISOControl(int)
	 * @see Character#isDefined(int)
	 * @since 5.4
	 */
	public void setAllowedParameterNames(Predicate<Iterable<String>> allowedParameterNames) {
		if (allowedParameterNames == null) {
			throw new IllegalArgumentException("allowedParameterNames cannot be null");
		}
		this.allowedParameterNames = allowedParameterNames;
	}

	/**
	 * <p>
	 * Determines which parameter values should be allowed.
	 * The default is to any parameter value.
	 * </p>
	 *
	 * @param allowedParameterValues the predicate for testing parameter values
	 * @since 5.4
	 */
	public void setAllowedParameterValues(Predicate<Iterable<String>> allowedParameterValues) {
		if (allowedParameterValues == null) {
			throw new IllegalArgumentException("allowedParameterValues cannot be null");
		}
		this.allowedParameterValues = allowedParameterValues;
	}

	@Override
	public FirewalledRequest getFirewalledRequest(HttpServletRequest request) throws RequestRejectedException {
		rejectForbiddenHttpMethod(request);
		rejectedBlocklistedUrls(request);
		rejectedUntrustedHosts(request);
		rejectDisallowedHeaders(request);
		rejectDisallowedParameters(request);

		if (!isNormalized(request)) {
			throw new RequestRejectedException("The request was rejected because the URL was not normalized.");
		}

		String requestUri = request.getRequestURI();
		if (!containsOnlyPrintableAsciiCharacters(requestUri)) {
			throw new RequestRejectedException("The requestURI was rejected because it can only contain printable ASCII characters.");
		}
		return new FirewalledRequest(request) {
			@Override
			public void reset() {
			}
		};
	}

	private void rejectForbiddenHttpMethod(HttpServletRequest request) {
		if (this.allowedHttpMethods == ALLOW_ANY_HTTP_METHOD) {
			return;
		}
		if (!this.allowedHttpMethods.contains(request.getMethod())) {
			throw new RequestRejectedException("The request was rejected because the HTTP method \"" +
					request.getMethod() +
					"\" was not included within the list of allowed HTTP methods " +
					this.allowedHttpMethods);
		}
	}

	private void rejectedBlocklistedUrls(HttpServletRequest request) {
		for (String forbidden : this.encodedUrlBlocklist) {
			if (encodedUrlContains(request, forbidden)) {
				throw new RequestRejectedException("The request was rejected because the URL contained a potentially malicious String \"" + forbidden + "\"");
			}
		}
		for (String forbidden : this.decodedUrlBlocklist) {
			if (decodedUrlContains(request, forbidden)) {
				throw new RequestRejectedException("The request was rejected because the URL contained a potentially malicious String \"" + forbidden + "\"");
			}
		}
	}

	private void rejectedUntrustedHosts(HttpServletRequest request) {
		String serverName = request.getServerName();
		if (serverName != null && !this.allowedHostnames.test(serverName)) {
			throw new RequestRejectedException("The request was rejected because the domain " + serverName + " is untrusted.");
		}
	}

	private void rejectDisallowedHeaders(HttpServletRequest request) {
		Iterable<String> names = () -> CollectionUtils.toIterator(request.getHeaderNames());
		if (!this.allowedHeaderNames.test(names)) {
			throw new RequestRejectedException("The request was rejected because one of the header names is not allowed.");
		}
		Iterable<String> values = () -> new HeaderValuesIterable(request);
		if (!this.allowedHeaderValues.test(values)) {
			throw new RequestRejectedException("The request was rejected because one of the header values is not allowed.");
		}
	}

	private void rejectDisallowedParameters(HttpServletRequest request) {
		Iterable<String> names = () -> CollectionUtils.toIterator(request.getParameterNames());
		if (!this.allowedParameterNames.test(names)) {
			throw new RequestRejectedException("The request was rejected because one of the parameter names is not allowed.");
		}
		Iterable<String> values = () -> new ParameterValuesIterable(request);
		if (!this.allowedParameterValues.test(values)) {
			throw new RequestRejectedException("The request was rejected because one of the parameter values is not allowed.");
		}
	}

	@Override
	public HttpServletResponse getFirewalledResponse(HttpServletResponse response) {
		return null;
	}

	private static Set<String> createDefaultAllowedHttpMethods() {
		Set<String> result = new HashSet<>();
		result.add(HttpMethod.DELETE.name());
		result.add(HttpMethod.GET.name());
		result.add(HttpMethod.HEAD.name());
		result.add(HttpMethod.OPTIONS.name());
		result.add(HttpMethod.PATCH.name());
		result.add(HttpMethod.POST.name());
		result.add(HttpMethod.PUT.name());
		return result;
	}

	private static boolean isNormalized(HttpServletRequest request) {
		if (!isNormalized(request.getRequestURI())) {
			return false;
		}
		if (!isNormalized(request.getContextPath())) {
			return false;
		}
		if (!isNormalized(request.getServletPath())) {
			return false;
		}
		if (!isNormalized(request.getPathInfo())) {
			return false;
		}
		return true;
	}

	private static boolean encodedUrlContains(HttpServletRequest request, String value) {
		if (valueContains(request.getContextPath(), value)) {
			return true;
		}
		return valueContains(request.getRequestURI(), value);
	}

	private static boolean decodedUrlContains(HttpServletRequest request, String value) {
		if (valueContains(request.getServletPath(), value)) {
			return true;
		}
		if (valueContains(request.getPathInfo(), value)) {
			return true;
		}
		return false;
	}

	private static boolean containsOnlyPrintableAsciiCharacters(String uri) {
		int length = uri.length();
		for (int i = 0; i < length; i++) {
			char c = uri.charAt(i);
			if (c < '\u0020' || c > '\u007e') {
				return false;
			}
		}

		return true;
	}

	private static boolean valueContains(String value, String contains) {
		return value != null && value.contains(contains);
	}

	/**
	 * Checks whether a path is normalized (doesn't contain path traversal
	 * sequences like "./", "/../" or "/.")
	 *
	 * @param path
	 *            the path to test
	 * @return true if the path doesn't contain any path-traversal character
	 *         sequences.
	 */
	private static boolean isNormalized(String path) {
		if (path == null) {
			return true;
		}

		for (int j = path.length(); j > 0;) {
			int i = path.lastIndexOf('/', j - 1);
			int gap = j - i;

			if (gap == 2 && path.charAt(i + 1) == '.') {
				// ".", "/./" or "/."
				return false;
			} else if (gap == 3 && path.charAt(i + 1) == '.' && path.charAt(i + 2) == '.') {
				return false;
			}

			j = i;
		}

		return true;
	}

	private static boolean containsISOControlCharacter(String s) {
		return s.codePoints().anyMatch(Character::isISOControl);
	}

	/**
	 * Provides the existing encoded url blocklist which can add/remove entries from
	 *
	 * @return the existing encoded url blocklist, never null
	 */
	public Set<String> getEncodedUrlBlocklist() {
		return this.encodedUrlBlocklist;
	}

	/**
	 * Provides the existing decoded url blocklist which can add/remove entries from
	 *
	 * @return the existing decoded url blocklist, never null
	 */
	public Set<String> getDecodedUrlBlocklist() {
		return this.decodedUrlBlocklist;
	}

	/**
	 * Provides the existing encoded url blocklist which can add/remove entries from
	 *
	 * @return the existing encoded url blocklist, never null
	 * @deprecated Use {@link #getEncodedUrlBlocklist()} instead
	 */
	@Deprecated
	public Set<String> getEncodedUrlBlacklist() {
		return getEncodedUrlBlocklist();
	}

	/**
	 * Provides the existing decoded url blocklist which can add/remove entries from
	 *
	 * @return the existing decoded url blocklist, never null
	 *
	 */
	public Set<String> getDecodedUrlBlacklist() {
		return getDecodedUrlBlocklist();
	}


	private static class HeaderValuesIterable implements Iterator<String> {
		private final HttpServletRequest request;
		private final Enumeration<String> headerNames;

		private String currentHeaderName;
		private Enumeration<String> headerValues;

		public HeaderValuesIterable(HttpServletRequest request) {
			this.request = request;
			this.headerNames = request.getHeaderNames();
		}

		@Override
		public boolean hasNext() {
			return this.headerNames.hasMoreElements() || hasMoreValues();
		}

		@Override
		public String next() {
			if (this.currentHeaderName == null || !hasMoreValues()) {
				this.currentHeaderName = this.headerNames.nextElement();
				this.headerValues = this.request.getHeaders(this.currentHeaderName);
			}
			return this.headerValues.nextElement();
		}

		private boolean hasMoreValues() {
			return this.headerValues != null && headerValues.hasMoreElements();
		}
	}

	private static class ParameterValuesIterable implements Iterator<String> {
		private final HttpServletRequest request;
		private final Enumeration<String> parameterNames;

		private String currentParameterName;
		private String[] parameterValues;
		int current;

		public ParameterValuesIterable(HttpServletRequest request) {
			this.request = request;
			this.parameterNames = request.getParameterNames();
		}

		@Override
		public boolean hasNext() {
			return this.parameterNames.hasMoreElements() || hasMoreValues();
		}

		@Override
		public String next() {
			if (this.currentParameterName == null || !hasMoreValues()) {
				this.currentParameterName = this.parameterNames.nextElement();
				this.parameterValues = this.request.getParameterValues(this.currentParameterName);
				this.current = 0;
			}
			return this.parameterValues[current++];
		}

		private boolean hasMoreValues() {
			return this.parameterValues != null && current < this.parameterValues.length;
		}
	}
}
