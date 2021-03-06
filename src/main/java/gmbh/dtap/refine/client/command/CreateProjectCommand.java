/*
 * MIT License
 *
 * Copyright (c) 2018-2020 DTAP GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package gmbh.dtap.refine.client.command;

import gmbh.dtap.refine.client.RefineClient;
import gmbh.dtap.refine.client.RefineException;
import gmbh.dtap.refine.client.UploadFormat;
import gmbh.dtap.refine.client.UploadOptions;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static gmbh.dtap.refine.client.util.HttpParser.HTTP_PARSER;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.Validate.notEmpty;
import static org.apache.commons.lang3.Validate.notNull;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_MOVED_TEMPORARILY;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;

/**
 * A command to create a project.
 */
public class CreateProjectCommand implements ResponseHandler<CreateProjectResponse> {

	private static final Charset charset = Charset.forName("UTF-8");

	private final String name;
	private final File file;
	private final UploadFormat format;
	private final UploadOptions options;

	/**
	 * Constructor for {@link Builder}.
	 *
	 * @param name    the project name
	 * @param file    the file containing the data to upload
	 * @param format  the optional upload format
	 * @param options the optional options
	 */
	private CreateProjectCommand(String name, File file, UploadFormat format, UploadOptions options) {
		this.name = name;
		this.file = file;
		this.format = format;
		this.options = options;
	}

	/**
	 * Executes the command.
	 *
	 * @param client the client to execute the command with
	 * @return the result of the command
	 * @throws IOException     in case of a connection problem
	 * @throws RefineException in case the server responses with an error or is not understood
	 */
	public CreateProjectResponse execute(RefineClient client) throws IOException {
		final URL url;
		if (options != null) {
			// https://github.com/dtap-gmbh/refine-java/issues/14
			// https://github.com/OpenRefine/OpenRefine/issues/1757
			// OpenRefine ignores options as form parameter, but accepts them as get parameter
			url = client.createUrl("/command/core/create-project-from-upload?" + urlEncodedOptions());
		} else {
			url = client.createUrl("/command/core/create-project-from-upload");
		}

		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		if (format != null) {
			multipartEntityBuilder.addTextBody("format", format.getValue(),
				TEXT_PLAIN.withCharset(charset));
		}
		if (options != null) {
			multipartEntityBuilder.addTextBody("options", options.asJson(),
				APPLICATION_JSON.withCharset(charset));
		}

		HttpEntity entity = multipartEntityBuilder
			.addBinaryBody("project-file", file)
			.addTextBody("project-name", name, TEXT_PLAIN.withCharset(charset))
			.build();

		HttpUriRequest request = RequestBuilder
			.post(url.toString())
			.setHeader(ACCEPT, APPLICATION_JSON.getMimeType())
			.setEntity(entity)
			.build();

		return client.execute(request, this);
	}

	private String urlEncodedOptions() {
		return URLEncodedUtils.format(singletonList(new BasicNameValuePair("options", options.asJson())), charset);
	}

	/**
	 * Validates the response and extracts necessary data.
	 *
	 * @param response the response to get the location from
	 * @return the response
	 * @throws IOException     in case of an connection problem
	 * @throws RefineException in case of an unexpected response or no location header is present
	 */
	@Override
	public CreateProjectResponse handleResponse(HttpResponse response) throws IOException {
		// TODO: parse errors in refine are returned as HTML
		HTTP_PARSER.assureStatusCode(response, SC_MOVED_TEMPORARILY);
		Header location = response.getFirstHeader("Location");
		if (location == null) {
			throw new RefineException("No location header found.");
		}
		URL url = new URL(location.getValue());
		return new CreateProjectResponse(url);
	}

	/**
	 * The builder for {@link CreateProjectCommand}.
	 */
	public static class Builder {

		private String name;
		private File file;
		private UploadFormat format;
		private UploadOptions options;

		/**
		 * Sets the project name.
		 *
		 * @param name the project name
		 * @return the builder for fluent usage
		 */
		public Builder name(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Sets the file containing the data to upload.
		 *
		 * @param file the file containing the data to upload
		 * @return the builder for fluent usage
		 */
		public Builder file(File file) {
			this.file = file;
			return this;
		}

		/**
		 * Sets the optional upload format.
		 *
		 * @param format the optional upload format
		 * @return the builder for fluent usage
		 */
		public Builder format(UploadFormat format) {
			this.format = format;
			return this;
		}

		/**
		 * Sets the optional options.
		 *
		 * @param options the optional options
		 * @return the builder for fluent usage
		 */
		public Builder options(UploadOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Builds the command after validation.
		 *
		 * @return the command
		 */
		public CreateProjectCommand build() {
			notNull(name, "name");
			notEmpty(name, "name");
			notNull(file, "file");
			return new CreateProjectCommand(name, file, format, options);
		}
	}
}
