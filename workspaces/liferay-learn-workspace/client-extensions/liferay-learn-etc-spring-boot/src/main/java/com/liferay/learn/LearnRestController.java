/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn;

import com.google.auth.oauth2.GoogleCredentials;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;
import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.net.URI;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	@GetMapping("/menu/items")
	@ResponseBody
	public ResponseEntity<Object> getMenuItems(
		@AuthenticationPrincipal Jwt jwt) {

		return new ResponseEntity<>(
			TransformUtil.transform(
				new JSONObject(
					get(
						_getAuthorization(),
						UriComponentsBuilder.fromPath(
							"/o/object-admin/v1.0/object-folders" +
								"/by-external-reference-code" +
									"/P2S3_LEARNING_MANAGEMENT_SYSTEM"
						).build(
						).toUri())
				).getJSONArray(
					"objectFolderItems"
				).toList(),
				this::_toMap),
			HttpStatus.OK);
	}

	@GetMapping("/{quizId}/questions")
	@ResponseBody
	public ResponseEntity<Object> getQuizQuestions(
			@AuthenticationPrincipal Jwt jwt, @PathVariable long quizId)
		throws Exception {

		return new ResponseEntity<>(
			new JSONObject(
				get(
					_getAuthorization(),
					UriComponentsBuilder.fromPath(
						"/o/c/p2s3quizquestions"
					).queryParam(
						"filter", "quizId eq '" + quizId + "'"
					).queryParam(
						"fields",
						StringBundler.concat(
							"id,p2s3QuizQuestionToP2S3QuizAnswers,",
							"p2s3QuizQuestionToP2S3QuizAnswers.answer,",
							"p2s3QuizQuestionToP2S3QuizAnswers.id,",
							"p2s3QuizQuestionToP2S3QuizAnswers.position,",
							"position,question,questionType")
					).queryParam(
						"nestedFields", "p2s3QuizQuestionToP2S3QuizAnswers"
					).queryParam(
						"pageSize", "500"
					).queryParam(
						"sort", "position"
					).build(
					).toUri())
			).getJSONArray(
				"items"
			).toList(),
			HttpStatus.OK);
	}

	@PostMapping("/{quizId}/result")
	@ResponseBody
	public ResponseEntity<Object> postQuizResult(
			@AuthenticationPrincipal Jwt jwt, @PathVariable long quizId,
			@RequestBody String json)
		throws Exception {

		Map<String, Object> quizResultMap = _getQuizResult(
			new JSONObject(json),
			new JSONObject(
				get(
					_getAuthorization(),
					UriComponentsBuilder.fromPath(
						"/o/c/p2s3quizes/" + quizId
					).queryParam(
						"fields",
						StringBundler.concat(
							"durationMinutes,id,isKnowledgeCheck,",
							"p2s3QuizToP2S3QuizQuestions.id,",
							"p2s3QuizToP2S3QuizQuestions.",
							"p2s3QuizQuestionToP2S3QuizAnswers,",
							"p2s3QuizToP2S3QuizQuestions.",
							"p2s3QuizQuestionToP2S3QuizAnswers.answer,",
							"p2s3QuizToP2S3QuizQuestions.",
							"p2s3QuizQuestionToP2S3QuizAnswers.id,",
							"p2s3QuizToP2S3QuizQuestions.",
							"p2s3QuizQuestionToP2S3QuizAnswers.position,",
							"p2s3QuizToP2S3QuizQuestions.",
							"p2s3QuizQuestionToP2S3QuizAnswers.score,",
							"p2s3QuizToP2S3QuizQuestions.position,",
							"p2s3QuizToP2S3QuizQuestions.question,",
							"p2s3QuizToP2S3QuizQuestions.questionTotalScore,",
							"p2s3QuizToP2S3QuizQuestions.questionType,",
							"passingScore,",
							"r_p2s3ModuleToP2S3Quizzes_c_p2s3ModuleId")
					).queryParam(
						"nestedFields",
						"p2s3QuizToP2S3QuizQuestions," +
							"p2s3QuizQuestionToP2S3QuizAnswers"
					).queryParam(
						"nestedFieldsDepth", "2"
					).queryParam(
						"pageSize", "500"
					).build(
					).toUri())));

		if (!GetterUtil.getBoolean(quizResultMap.get("isKnowledgeCheck")) &&
			GetterUtil.getBoolean(quizResultMap.get("passed")) &&
			(jwt != null)) {

			_postUserBadge(
				quizId,
				GetterUtil.getLong(
					jwt.getClaims(
					).get(
						"sub"
					)));
		}

		return ResponseEntity.ok(quizResultMap);
	}

	private void _addSentenceChunk(
		List<String> ssmlParts, StringBundler sb, String sentence,
		int maxLength) {

		if ((sb.length() + sentence.length()) > (maxLength - 15)) {
			if (sb.length() > 0) {
				ssmlParts.add(
					"<speak>" + StringUtil.trim(sb.toString()) + "</speak>");
				sb.setIndex(0); // limpa o buffer
			}
		}

		sb.append(
			sentence
		).append(
			" "
		);
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
						"https://texttospeech.googleapis.com/v1beta1/text:synthesize"
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
			catch (WebClientResponseException e) {
				System.err.println(
					"❌ Erro no Google TTS: " + e.getResponseBodyAsString());

				throw new Exception(
					"Erro ao gerar áudio com Google TTS: " +
						e.getResponseBodyAsString());
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
				"/o/headless-delivery/v1.0/sites/{siteGroupId}/documents/by-external-reference-code/{fileName}"
			).build(
				_siteGroupId, StringUtil.toUpperCase(fileName)
			);
		}
		else {
			method = HttpMethod.POST;
			uri = UriComponentsBuilder.fromPath(
				"/o/headless-delivery/v1.0/document-folders/{documentFolderId}/documents"
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

			JSONObject jsonResponse = new JSONObject(response);

			return Collections.singletonMap(
				"contentUrl", jsonResponse.optString("contentUrl", null));
		}
		catch (WebClientResponseException e) {
			String responseBody = e.getResponseBodyAsString();

			System.err.println("❌ Erro ao enviar documento para Liferay:");
			System.err.println("Status: " + e.getStatusCode());
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
				e.getStatusCode(
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

	private int _getQuizQuestionScore(
		Map<String, Object> answerMap, JSONObject quizQuestionJSONObject,
		JSONObject scoreSheetJSONObject) {

		JSONArray quizAnswersJSONArray = quizQuestionJSONObject.getJSONArray(
			"quizAnswers");

		scoreSheetJSONObject.put("questionsAnswers", quizAnswersJSONArray);

		boolean incorrectAnswer = false;

		for (int j = 0; j < quizAnswersJSONArray.length(); j++) {
			JSONObject quizAnswerJSONObject =
				quizAnswersJSONArray.getJSONObject(j);

			if (((quizAnswerJSONObject.getInt("score") > 0) &&
				 !GetterUtil.getBoolean(
					 answerMap.get(
						 String.valueOf(
							 quizAnswerJSONObject.getLong("id"))))) ||
				((quizAnswerJSONObject.getInt("score") <= 0) &&
				 GetterUtil.getBoolean(
					 answerMap.get(
						 String.valueOf(
							 quizAnswerJSONObject.getLong("id")))))) {

				incorrectAnswer = true;

				break;
			}
		}

		if (incorrectAnswer) {
			return 0;
		}

		return quizQuestionJSONObject.getInt("questionTotalScore");
	}

	private Map<String, Object> _getQuizResult(
		JSONObject quizAnswersJSONObject, JSONObject quizJSONObject) {

		JSONArray quizQuestionsJSONArray = quizJSONObject.getJSONArray(
			"quizQuestions");

		Map<String, Object> map = HashMapBuilder.<String, Object>put(
			"isKnowledgeCheck", false
		).put(
			"passingScore", quizJSONObject.getInt("passingScore")
		).put(
			"selectedAnswers", quizAnswersJSONObject.toMap()
		).put(
			"totalQuestions", quizQuestionsJSONArray.length()
		).build();

		float achievedQuizScore = 0;
		float totalQuizScore = 0;
		int totalPassedQuizQuestions = 0;

		JSONArray scoreSheetJSONArray = new JSONArray();

		for (int i = 0; i < quizQuestionsJSONArray.length(); i++) {
			JSONObject quizQuestionJSONObject =
				quizQuestionsJSONArray.getJSONObject(i);
			JSONObject scoreSheetJSONObject = new JSONObject();

			scoreSheetJSONObject.put(
				"questionId", quizQuestionJSONObject.getLong("id")
			).put(
				"questionTitle", quizQuestionJSONObject.getString("question")
			).put(
				"totalScore",
				quizQuestionJSONObject.getInt("questionTotalScore")
			).put(
				"type",
				quizQuestionJSONObject.getJSONObject(
					"questionType"
				).getString(
					"key"
				)
			);

			int quizQuestionScore = 0;

			if (Objects.equals(
					scoreSheetJSONObject.getString("type"),
					"selectMultipleChoice")) {

				JSONObject jsonObject = quizAnswersJSONObject.getJSONObject(
					String.valueOf(quizQuestionJSONObject.getLong("id")));

				scoreSheetJSONObject.put("selectedAnswer", jsonObject);

				quizQuestionScore = _getQuizQuestionScore(
					jsonObject.toMap(), quizQuestionJSONObject,
					scoreSheetJSONObject);
			}
			else {
				long id = quizAnswersJSONObject.getLong(
					String.valueOf(quizQuestionJSONObject.getLong("id")));

				scoreSheetJSONObject.put("selectedAnswer", id);

				quizQuestionScore = _getQuizQuestionScore(
					Collections.singletonMap(String.valueOf(id), true),
					quizQuestionJSONObject, scoreSheetJSONObject);
			}

			if (quizQuestionScore > 0) {
				totalPassedQuizQuestions++;
			}

			achievedQuizScore += quizQuestionScore;
			scoreSheetJSONObject.put("achievedScore", quizQuestionScore);
			totalQuizScore += quizQuestionJSONObject.getInt(
				"questionTotalScore");

			scoreSheetJSONArray.put(scoreSheetJSONObject);
		}

		if (quizJSONObject.getBoolean("isKnowledgeCheck")) {
			map.put("isKnowledgeCheck", true);
			map.put("scoreSheet", scoreSheetJSONArray.toList());
		}

		map.put(
			"passed",
			Math.round((achievedQuizScore / totalQuizScore) * 100) >=
				quizJSONObject.getInt("passingScore"));
		map.put("totalPassedQuestions", totalPassedQuizQuestions);
		map.put(
			"totalScore",
			Math.round((achievedQuizScore / totalQuizScore) * 100));

		return map;
	}

	private void _postUserBadge(long quizId, long userId) {
		JSONArray jsonArray = new JSONObject(
			get(
				_getAuthorization(),
				UriComponentsBuilder.fromPath(
					"/o/c/p2s3quizes/" + quizId + "/quizBadge"
				).queryParam(
					"fields", "id"
				).build(
				).toUri())
		).getJSONArray(
			"items"
		);

		if (jsonArray.isEmpty()) {
			return;
		}

		JSONObject badgeJSONObject = jsonArray.getJSONObject(0);

		JSONObject userBadgeJSONObject = new JSONObject(
			get(
				_getAuthorization(),
				UriComponentsBuilder.fromPath(
					"/o/c/p2s3userbadges"
				).queryParam(
					"filter",
					StringBundler.concat(
						"userId eq '", userId, "' and badgeId eq ",
						badgeJSONObject.getLong("id"))
				).build(
				).toUri()));

		if (userBadgeJSONObject.getInt("totalCount") > 0) {
			return;
		}

		post(
			_getAuthorization(),
			new JSONObject(
			).put(
				"badgeId", badgeJSONObject.getLong("id")
			).put(
				"quizId", quizId
			).put(
				"r_lUserToP2S3UserBadges_userId", userId
			).toString(),
			UriComponentsBuilder.fromPath(
				"/o/c/p2s3userbadges"
			).build(
			).toUri());
	}

	private String _prepareContentForTTS(String content) {
		if (content == null) {
			return "";
		}

		String cleaned = content;

		// 1️⃣ Remover &nbsp; (espaço não separável)

		cleaned = cleaned.replaceAll("&nbsp;", " ");

		// 2️⃣ Remover qualquer texto que o TTS leia como "less than speak greater than"

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

		// 3️⃣ Remover palavra "link" e deixar apenas o texto do <a>

		cleaned = cleaned.replaceAll(
			"<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "$2");

		// 4️⃣ Melhorar leitura das tabelas

		cleaned = cleaned
				.replaceAll("(?i)<table[^>]*>", "<p>Table information:</p>")
				.replaceAll("(?i)</table>", "<p>End of table.</p>")
				.replaceAll("(?i)<thead>", "")
				.replaceAll("(?i)</thead>", "")
				.replaceAll("(?i)<tbody>", "")
				.replaceAll("(?i)</tbody>", "")

				// Cada linha = Row

				.replaceAll("(?i)<tr>", "<p>Row:</p>")
				.replaceAll("(?i)</tr>", "")

				// Cabeçalhos e células

				.replaceAll("(?i)<th[^>]*>", "<p>Header: ")
				.replaceAll("(?i)</th>", ".</p>")
				.replaceAll("(?i)<td[^>]*>", "<p>Information: ")
				.replaceAll("(?i)</td>", ".</p>")

				// Substituir ✔ por "supported"

				.replaceAll("✔", "supported");

		// 5️⃣ Remover repetições finais e tags speak residuais

		cleaned = cleaned.replaceAll(
			"(?i)less than,?\\s*slash,?\\s*speak\\s*greater than", ""
		).replaceAll(
			"&lt;/?speak&gt;", ""
		).replaceAll(
			"<speak>|</speak>", ""
		);

		// Limpeza final

		cleaned = cleaned.replaceAll(
			"\\s{2,}", " "
		).trim();

		return cleaned;
	}

	private String _replace(String s, String replacement, String regex) {
		return Pattern.compile(
			regex
		).matcher(
			s
		).replaceAll(
			replacement
		);
	}

	private List<String> _splitSsml(String ssml, int maxLength) {
		List<String> parts = new ArrayList<>();

		if ((ssml == null) || ssml.isEmpty()) {
			return parts;
		}
		ssml = ssml.replaceAll("(?i)&lt;/?speak&gt;", "").replaceAll("(?i)<\\/?speak>", "");


		// Remove tags <speak> se existirem e pega só o conteúdo

		String ssmlContent = ssml.replaceFirst(
			"^<speak>", ""
		).replaceFirst(
			"</speak>$", ""
		).trim();

		// Divide o texto em frases por pontuação forte

		String[] sentences = ssmlContent.split("(?<=[.!?])\\s+");

		StringBuilder currentPart = new StringBuilder();

		for (String sentence : sentences) {

			// Se a frase adicionada ultrapassar o limite, inicia um novo bloco

			if ((currentPart.length() + sentence.length()) > maxLength) {
				parts.add(
					"<speak>" +
						currentPart.toString(
						).trim() + "</speak>");
				currentPart = new StringBuilder();
			}

			currentPart.append(
				sentence
			).append(
				" "
			);
		}

		if (currentPart.length() > 0) {
			parts.add(
				"<speak>" +
					currentPart.toString(
					).trim() + "</speak>");
		}

		return parts;
	}

	private Map<String, Object> _toMap(Object object) {
		Map<String, Object> map = (Map<String, Object>)object;

		if (!map.containsKey("objectDefinition")) {
			return null;
		}

		Map<String, Object> objectDefinitionMap = (Map<String, Object>)map.get(
			"objectDefinition");

		return HashMapBuilder.<String, Object>put(
			"externalReferenceCode",
			objectDefinitionMap.get("externalReferenceCode")
		).put(
			"id", objectDefinitionMap.get("id")
		).put(
			"title", objectDefinitionMap.get("pluralLabel")
		).build();
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