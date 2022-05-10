package com.info7255.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import redis.clients.jedis.Jedis;

@Service
public class PlanService {

	private RedisTemplate<String, Object> redisTemplate;
	private final Jedis jedis;

	public PlanService(RedisTemplate<String, Object> redisTemplate, Jedis jedis) {
		this.redisTemplate = redisTemplate;
		this.jedis = jedis;
	}

	public String setEtag(JSONObject object, String key) {
		String eTag = DigestUtils.md5Hex(object.toString());
		jedis.hset(key, "eTag", eTag);
		return eTag;
	}

	public String getEtag(String key) {
		return jedis.hget(key, "eTag");
	}

	public String savePlan(JSONObject plan, String key) {
		convertToMap(plan);
		return setEtag(plan, key);
	}

	public Map<String, Object> getPlan(String redisKey, Map<String, Object> planAsMap) {
		Set<String> keySet = jedis.keys(redisKey + "_*");
		keySet.add(redisKey);

		for (String key : keySet) {
			if (key.equals(redisKey)) {
				Map<String, String> stringMap = jedis.hgetAll(key);
				for (String stringMapKey : stringMap.keySet()) {
					String value = stringMap.get(stringMapKey);
					planAsMap.put(stringMapKey, NumberUtils.isParsable(value) ? Integer.parseInt(value) : value);
				}
			} else {
				String ObjectKey = key.substring((redisKey + "_").length());
				Set<String> setMembers = jedis.smembers(key);
				if (ObjectKey.equals("linkedPlanServices")) {
					List<Object> list = new ArrayList<>();
					for (String setMember : setMembers) {
						Map<String, Object> mapOfList = new HashMap<>();
						// Recursive decompose the list until no list
						list.add(getPlan(setMember, mapOfList));
					}
					planAsMap.put(ObjectKey, list);
				} else if (setMembers.size() == 1) {
					Map<String, String> setObjMap = jedis.hgetAll(setMembers.iterator().next());
					Map<String, Object> contentMap = new HashMap<>();
					for (String setObjMapKey : setObjMap.keySet()) {
						String value = setObjMap.get(setObjMapKey);
						contentMap.put(setObjMapKey, NumberUtils.isParsable(value) ? Integer.parseInt(value) : value);
					}
					planAsMap.put(ObjectKey, contentMap);

				}
			}
		}
		return planAsMap;
	}

	public void deletePlan(String redisKey) {
		Set<String> keySet = jedis.keys(redisKey + "_*");
		keySet.add(redisKey);
		for (String key : keySet) {
			if (key.equals(redisKey)) {
				jedis.del(new String[] { key });
			} else {
				Set<String> setMembers = jedis.smembers(key);
				if (setMembers.size() == 1) {
					jedis.del(new String[] { setMembers.iterator().next(), key });

				} else {
					for (String setMember : setMembers) {
						deletePlan(setMember);
					}
					// jedis.del(new String[]{key});
				}
			}
		}

	}

	public boolean checkIfPlanExists(String key) {
		return jedis.exists(key);
	}

	private Map<String, Map<String, Object>> convertToMap(JSONObject jsonObject) {
		Map<String, Map<String, Object>> map = new HashMap<String, Map<String, Object>>();
		Map<String, Object> content = new HashMap<String, Object>();

		for (String key : jsonObject.keySet()) {
			// loop through all root keys
			String redisKeyString = jsonObject.get("objectType") + "_" + jsonObject.get("objectId");
			Object value = jsonObject.get(key);
			if (value instanceof JSONObject) {
				value = convertToMap((JSONObject) value);
				HashMap<String, Map<String, Object>> mapValue = (HashMap<String, Map<String, Object>>) value;
				jedis.sadd(redisKeyString + "_" + key, mapValue.entrySet().iterator().next().getKey());
			} else if (value instanceof JSONArray) {
				value = convertToList((JSONArray) value);
				for (HashMap<String, HashMap<String, Object>> entry : (List<HashMap<String, HashMap<String, Object>>>) value) {
					for (String listKey : entry.keySet()) {
						jedis.sadd(redisKeyString + "_" + key, listKey);
					}
				}
			} else {
				jedis.hset(redisKeyString, key, value.toString());
				content.put(key, value);
				map.put(redisKeyString, content);
			}

		}
		return map;
	}

	public List<Object> convertToList(JSONArray jsonArray) {
		List<Object> res = new ArrayList<>();
		for (Object value : jsonArray) {
			if (value instanceof JSONArray) {
				value = convertToList((JSONArray) value);
			} else if (value instanceof JSONObject) {
				value = convertToMap((JSONObject) value);
			}
			res.add(value);
		}
		return res;
	}

}