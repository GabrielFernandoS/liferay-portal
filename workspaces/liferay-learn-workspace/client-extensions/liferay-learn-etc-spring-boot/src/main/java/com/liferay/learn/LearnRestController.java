/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn;

import com.google.auth.oauth2.GoogleCredentials;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;
import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.net.URI;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import java.util.*;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Nilton Vieira
 */
@RequestMapping("/learn")
@RestController
public class LearnRestController extends BaseRestController {

	@GetMapping("/lesson/{lessonId}/audio/base64")
	@ResponseBody
	public ResponseEntity<Object> getLessonAudioBase64(
		@PathVariable long lessonId, @RequestParam String languageCode,
		@RequestParam String voiceName, @RequestParam String voiceType) {

		try {
			JSONObject lessonJSONObject = new JSONObject(
				get(
					_getAuthorization(),
					UriComponentsBuilder.fromPath(
						"/o/c/p2s3lessons/" + lessonId
					).queryParam(
						"fields", "content,dateModified"
					).build(
					).toUri()));

			String content = lessonJSONObject.getString("content");

			if (Validator.isNull(content)) {
				return ResponseEntity.status(
					HttpStatus.NOT_FOUND
				).body(
					"Lesson " + lessonId + " is missing readable text."
				);
			}

			String fileName = StringBundler.concat(
				"lesson-", lessonId, "-", voiceType, ".mp3");

			JSONObject jsonObject = null;

			try {
				jsonObject = new JSONObject(
					get(
						"",
						UriComponentsBuilder.fromPath(
							StringBundler.concat(
								"/o/headless-delivery/v1.0/sites/",
								_siteGroupId,
								"/documents/by-external-reference-code/",
								StringUtil.toUpperCase(fileName))
						).build(
						).toUri()));
			}
			catch (WebClientResponseException webClientResponseException) {
				if (webClientResponseException.getStatusCode() !=
						HttpStatus.NOT_FOUND) {

					throw webClientResponseException;
				}

				return ResponseEntity.ok(
					_generateAudioResource(
						content, 0, fileName, languageCode, voiceName));
			}

			OffsetDateTime offsetDateTime = OffsetDateTime.parse(
				lessonJSONObject.getString("dateModified")
			).truncatedTo(
				ChronoUnit.MINUTES
			);

			if (offsetDateTime.isAfter(
					OffsetDateTime.parse(
						jsonObject.getString("dateModified")
					).truncatedTo(
						ChronoUnit.MINUTES
					))) {

				return ResponseEntity.ok(
					_generateAudioResource(
						content, _documentFolderId, fileName, languageCode,
						voiceName));
			}

			return ResponseEntity.ok(
				Collections.singletonMap(
					"contentUrl", jsonObject.getString("contentUrl")));
		}
		catch (Exception exception) {
			return ResponseEntity.status(
				500
			).body(
				"Error: " + exception.getMessage()
			);
		}
	}

	private Map<String, Object> _generateAudioResource(
			String content, long documentFolderId, String fileName,
			String languageCode, String voiceName)
		throws Exception {

		ByteArrayOutputStream byteArrayOutputStream =
			new ByteArrayOutputStream();

		content = content.replaceAll("(?i)<[^>]+>", ". ");

		String cleanedContent = _prepareContentForTTS(
			content.replaceAll("\\bLiferay\\b", "Life-ray"));

		List<String> ssmls = _splitSsml(cleanedContent, 5000);

		for (String ssml : ssmls) {
			try {
				String response = post(
					_getGoogleAccessToken(),
					new JSONObject(
						HashMapBuilder.<String, Object>put(
							"audioConfig",
							HashMapBuilder.<String, Object>put(
								"audioEncoding", "MP3"
							).build()
						).put(
							"input",
							HashMapBuilder.<String, Object>put(
								"text", ssml
							).build()
						).put(
							"voice",
							HashMapBuilder.<String, Object>put(
								"languageCode", languageCode
							).put(
								"name", voiceName
							).build()
						).build()
					).toString(),
					UriComponentsBuilder.fromUriString(
						"https://texttospeech.googleapis.com/v1beta1" +
							"/text:synthesize"
					).build(
					).toUri());

				byteArrayOutputStream.write(
					Base64.getDecoder(
					).decode(
						new JSONObject(
							response
						).getString(
							"audioContent"
						)
					));
			}
			catch (WebClientResponseException webClientResponseException) {
				System.err.println(
					"❌ Erro no Google TTS: " +
						webClientResponseException.getResponseBodyAsString());

				throw new Exception(
					"Erro ao gerar áudio com Google TTS: " +
						webClientResponseException.getResponseBodyAsString());
			}
		}

		ByteArrayResource fileResource = new ByteArrayResource(
			byteArrayOutputStream.toByteArray()) {

			@Override
			public String getFilename() {
				return fileName;
			}

		};

		MultipartBodyBuilder builder = new MultipartBodyBuilder();

		builder.part(
			"document",
			new JSONObject(
			).put(
				"documentFolderId", _documentFolderId
			).put(
				"externalReferenceCode", StringUtil.toUpperCase(fileName)
			).put(
				"fileName", fileName
			).put(
				"title", fileName
			).put(
				"viewableBy", "Anyone"
			).toString(),
			MediaType.APPLICATION_JSON);

		builder.part("file", fileResource, MediaType.APPLICATION_OCTET_STREAM);

		HttpMethod method;
		URI uri;

		if (documentFolderId != 0) {
			method = HttpMethod.PUT;
			uri = UriComponentsBuilder.fromPath(
				"/o/headless-delivery/v1.0/sites/{siteGroupId}/documents" +
					"/by-external-reference-code/{fileName}"
			).build(
				_siteGroupId, StringUtil.toUpperCase(fileName)
			);
		}
		else {
			method = HttpMethod.POST;
			uri = UriComponentsBuilder.fromPath(
				"/o/headless-delivery/v1.0/document-folders" +
					"/{documentFolderId}/documents"
			).build(
				_documentFolderId
			);
		}

		try {
			String response = _webClientBuilder.baseUrl(
				_lxcDXPServerProtocol + "://" + _lxcDXPMainDomain
			).build(
			).method(
				method
			).uri(
				uri.toString()
			).contentType(
				MediaType.MULTIPART_FORM_DATA
			).header(
				HttpHeaders.AUTHORIZATION, _getAuthorization()
			).body(
				BodyInserters.fromMultipartData(builder.build())
			).retrieve(
			).bodyToMono(
				String.class
			).block();

			JSONObject jsonResponseJSONObject = new JSONObject(response);

			return Collections.singletonMap(
				"contentUrl",
				jsonResponseJSONObject.optString("contentUrl", null));
		}
		catch (WebClientResponseException webClientResponseException) {
			String responseBody =
				webClientResponseException.getResponseBodyAsString();

			System.err.println("❌ Erro ao enviar documento para Liferay:");
			System.err.println(
				"Status: " + webClientResponseException.getStatusCode());
			System.err.println("Corpo do erro: " + responseBody);

			Map<String, Object> errorDetails = new HashMap<>();

			errorDetails.put("details", responseBody);
			errorDetails.put("documentFolderId", documentFolderId);
			errorDetails.put("fileName", fileName);
			errorDetails.put(
				"message", "Falha ao enviar documento para o Liferay.");
			errorDetails.put("method", method.toString());
			errorDetails.put("siteGroupId", _siteGroupId);
			errorDetails.put(
				"status",
				webClientResponseException.getStatusCode(
				).value());
			errorDetails.put("uri", uri.toString());

			throw new Exception(
				"Erro ao enviar arquivo para o Liferay: " +
					new JSONObject(
						errorDetails
					).toString(
						2
					));
		}
	}

	private String _getAuthorization() {
		return _liferayOAuth2AccessTokenManager.getAuthorization(
			"liferay-learn-etc-spring-boot-oahs");
	}

	private String _getGoogleAccessToken() throws Exception {
		GoogleCredentials googleCredentials = GoogleCredentials.fromStream(
			new ByteArrayInputStream(_googleCredentials.getBytes())
		).createScoped(
			Collections.singletonList(
				"https://www.googleapis.com/auth/cloud-platform")
		);

		googleCredentials.refresh();

		String accessTokenValue = googleCredentials.getAccessToken(
		).getTokenValue();

		return "Bearer " + accessTokenValue;
	}

	private String _prepareContentForTTS(String content) {
		if (content == null) {
			return "";
		}

		String cleaned = content;

		cleaned = cleaned.replaceAll("&nbsp;", " ");

		cleaned = cleaned.replaceAll(
			"&lt;speak&gt;", ""
		).replaceAll(
			"&lt;/speak&gt;", ""
		).replaceAll(
			"(?i)less than,?\\s*speak\\s*greater than", ""
		).replaceAll(
			"(?i)less than,?\\s*/speak\\s*greater than", ""
		).replaceAll(
			"(?i)less than,?\\s*slash,?\\s*speak\\s*greater than", ""
		).trim();

		cleaned = cleaned.replaceAll(
			"<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "$2");

		cleaned = cleaned.replaceAll(
			"(?i)<table[^>]*>", "<p>Table information:</p>"
		).replaceAll(
			"(?i)</table>", "<p>End of table.</p>"
		).replaceAll(
			"(?i)<thead>", ""
		).replaceAll(
			"(?i)</thead>", ""
		).replaceAll(
			"(?i)<tbody>", ""
		).replaceAll(
			"(?i)</tbody>", ""
		).replaceAll(
			"(?i)<tr>", "<p>Row:</p>"
		).replaceAll(
			"(?i)</tr>", ""
		).replaceAll(
			"(?i)<th[^>]*>", "<p>Header: "
		).replaceAll(
			"(?i)</th>", ".</p>"
		).replaceAll(
			"(?i)<td[^>]*>", "<p>Information: "
		).replaceAll(
			"(?i)</td>", ".</p>"
		).replaceAll(
			"✔", "supported"
		);

		cleaned = cleaned.replaceAll(
			"(?i)less than,?\\s*slash,?\\s*speak\\s*greater than", ""
		).replaceAll(
			"&lt;/?speak&gt;", ""
		).replaceAll(
			"<speak>|</speak>", ""
		);

		cleaned = cleaned.replaceAll(
			"\\s{2,}", " "
		).trim();

		return cleaned;
	}

	private List<String> _splitSsml(String ssml, int maxLength) {
		List<String> parts = new ArrayList<>();

		if (Validator.isNull(ssml)) {
			return parts;
		}

		ssml = ssml.replaceAll(
			"(?i)&lt;/?speak&gt;", ""
		).replaceAll(
			"(?i)<\\/?speak>", ""
		);

		String ssmlContent = ssml.replaceFirst(
			"^<speak>", ""
		).replaceFirst(
			"</speak>$", ""
		).trim();

		String[] sentences = ssmlContent.split("(?<=[.!?])\\s+");

		StringBuilder currentPart = new StringBuilder();

		for (String sentence : sentences) {
			if ((currentPart.length() + sentence.length()) > maxLength) {
				String part = currentPart.toString(
				).trim();

				String wrappedPart = "<speak>" + part + "</speak>";

				parts.add(wrappedPart);

				currentPart = new StringBuilder();
			}

			currentPart.append(
				sentence
			).append(
				" "
			);
		}

		if (currentPart.length() > 0) {
			String part = currentPart.toString(
			).trim();

			String wrappedPart = "<speak>" + part + "</speak>";

			parts.add(wrappedPart);
		}

		return parts;
	}

	@Value("${liferay.learn.audio.lessons.document.folder.id}")
	private long _documentFolderId;

	@Value("${liferay.learn.google.credentials}")
	private String _googleCredentials;

	@Autowired
	private LiferayOAuth2AccessTokenManager _liferayOAuth2AccessTokenManager;

	@Value("${com.liferay.lxc.dxp.mainDomain}")
	private String _lxcDXPMainDomain;

	@Value("${com.liferay.lxc.dxp.server.protocol}")
	private String _lxcDXPServerProtocol;

	@Value("${liferay.learn.dxp.site.group.id}")
	private String _siteGroupId;

	@Autowired
	private WebClient.Builder _webClientBuilder;

}