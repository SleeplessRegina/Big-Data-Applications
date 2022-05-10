package com.info7255.controller;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.info7255.controller.Application;
import com.info7255.models.AuthenticationResponse;
import com.info7255.service.PlanService;
import com.info7255.util.JwtUtil;
import com.info7255.validator.JsonValidator;

@RestController
public class HelloController {

	@Autowired
	PlanService planService;

	@Autowired
	JsonValidator validator;

	@Autowired
	JwtUtil jwtUtil;

	@Autowired
	RabbitTemplate template;

	@GetMapping("/getToken")
	public ResponseEntity<AuthenticationResponse> getToken() {
		String token = jwtUtil.generateToken();

		return ResponseEntity.status(HttpStatus.CREATED).body(new AuthenticationResponse(token));
	}

	@GetMapping(value = "/{objectType}/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getPlanEntity(@PathVariable String objectId, @PathVariable String objectType,
			@RequestHeader HttpHeaders headers) {
		String key = objectType + "_" + objectId;
		if (!planService.checkIfPlanExists(key)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new JSONObject().put("Message", "Plan does not exist").toString());
		}

		String eTag = planService.getEtag(key);
		String headersEtag = headers.getFirst("If-None-Match");

		if (headersEtag != null && eTag.equals(headersEtag)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).build();
		}
		Map<String, Object> planAsMap = new HashMap<>();

		planAsMap = planService.getPlan(key, planAsMap);
		if (objectType.equals("plan"))
			return ResponseEntity.ok().eTag(eTag).body(planAsMap);
		return ResponseEntity.ok().body(planAsMap);

	}

	@PostMapping(value = "/validateToken", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> validateToken(@RequestHeader HttpHeaders requestHeader) {
		final String authorizationHeader = requestHeader.getFirst("Authorization");

		if (authorizationHeader == null || authorizationHeader.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JSONObject().put("Message", "Missing Token").toString());
		}
		String token = null;
		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			token = authorizationHeader.substring(7);
			try {
				if (jwtUtil.validateToken(token)) {
					return ResponseEntity.status(HttpStatus.ACCEPTED)
							.body(new JSONObject().put("Message", "Valid Token").toString());
				}
			} catch (Exception e) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(new JSONObject().put("Message", "UNAUTHORIZED Token").toString());

			}
		}
		return ResponseEntity.ok().body("");
	}

	@PostMapping(value = "/plan", produces = "application/json")
	public ResponseEntity<?> createPlan(@RequestBody(required = false) String plan) throws JSONException, Exception {
		if (plan == null || plan.isEmpty()) {
			return new ResponseEntity<>("Body is Empty", HttpStatus.BAD_REQUEST);
		}

		JSONObject jsonPlan = new JSONObject(plan);

		try {
			validator.validateJson(jsonPlan);
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new JSONObject().put("Error", ex.getMessage()).toString());
		}
		String key = jsonPlan.get("objectType").toString() + "_" + jsonPlan.get("objectId").toString();

		if (planService.checkIfPlanExists(key)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new JSONObject().put("Message", "Plan already exist").toString());
		}
		String newEtag = planService.savePlan(jsonPlan, key);

		Map<String, String> message = new HashMap<>();
		message.put("operation", "SAVE");
		message.put("body", plan);
		System.out.println("Sending message: " + message);
		template.convertAndSend(Application.queueName, message);

		return ResponseEntity.ok().eTag(newEtag)
				.body(" {\"message\": \"Created plan with ID: " + jsonPlan.get("objectId") + "\" }");
	}

	@DeleteMapping(value = "/{objectType}/{objectId}", produces = "application/json")
	public ResponseEntity<?> deletePlan(@PathVariable String objectId, @PathVariable String objectType) {
		String key = objectType + "_" + objectId;

		if (!planService.checkIfPlanExists(key)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new JSONObject().put("Message", "Plan does not exist").toString());
		}
		Map<String, Object> planAsMap = new HashMap<>();

		planAsMap = planService.getPlan(key, planAsMap);
		Map<String, String> message = new HashMap<>();
		message.put("operation", "DELETE");
		message.put("body", new JSONObject(planAsMap).toString());
		template.convertAndSend(Application.queueName, message);

		planService.deletePlan(key);
		return ResponseEntity.ok().body(" {\"message\": \"" + objectType + " " + objectId + " deleted \" }");
	}

	@PutMapping(value = "/plan/{objectId}", produces = "application/json")
	public ResponseEntity<?> updatePlan(@RequestBody String plan, @PathVariable String objectId,
			@RequestHeader HttpHeaders headers) {
		if (plan == null || plan.isEmpty()) {
			return new ResponseEntity<>("Body is Empty", HttpStatus.BAD_REQUEST);
		}

		JSONObject jsonPlan = new JSONObject(plan);
		String key = "plan" + "_" + objectId;
		if (!planService.checkIfPlanExists(key)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new JSONObject().put("Message", "Plan does not exist").toString());
		}
		String eTag = planService.getEtag(key);
		String headersEtag = headers.getFirst("If-Match");
		if (headersEtag != null && !eTag.equals(headersEtag)) {
			return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(eTag).build();
		}

		planService.deletePlan(key);

		String updatedEtag = planService.savePlan(jsonPlan, key);

		return ResponseEntity.ok().eTag(updatedEtag)
				.body(" {\"message\": \"Update plan successfully with ID: " + jsonPlan.get("objectId") + "\" }");

	}

	@PatchMapping(value = "/plan/{objectId}", produces = "application/json")
	public ResponseEntity<?> patchPlan(@RequestBody String plan, @PathVariable String objectId,
			@RequestHeader HttpHeaders headers) {
		if (plan == null || plan.isEmpty()) {
			return new ResponseEntity<>("Body is Empty", HttpStatus.BAD_REQUEST);
		}

		JSONObject jsonPlan = new JSONObject(plan);
		String key = "plan" + "_" + objectId;
		if (!planService.checkIfPlanExists(key)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new JSONObject().put("Message", "Plan does not exist").toString());
		}

		String eTag = planService.getEtag(key);
		String headersEtag = headers.getFirst("If-Match");
		if (headersEtag != null && !eTag.equals(headersEtag)) {
			return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(eTag).build();
		}

		String updatedEtag = planService.savePlan(jsonPlan, key);

		// Send message to queue for index update
		Map<String, String> message = new HashMap<>();
		message.put("operation", "SAVE");
		message.put("body", plan);

		System.out.println("Sending message: " + message);
		template.convertAndSend(Application.queueName, message);

		return ResponseEntity.ok().eTag(updatedEtag)
				.body(" {\"message\": \"Update plan successfully with ID: " + jsonPlan.get("objectId") + "\" }");

	}
}