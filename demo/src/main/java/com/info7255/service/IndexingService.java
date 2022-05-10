package com.info7255.service;

import java.io.IOException;
import java.util.*;

import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IndexingService {
 
 @Autowired RestHighLevelClient client;
 
 private static final String indexName = "plan-index";
 private static LinkedHashMap<String, Map<String, Object>> mapOfDocuments = new LinkedHashMap<>();
 private static List<String> keys = new ArrayList<>(); 

 public void receiveMessage(Map<String, String> message) {
  try {
   String operation = message.get("operation");
   String body = message.get("body");
   JSONObject jsonObject = new JSONObject(body);

   if (operation.equals("SAVE")) {
    postDocument(jsonObject);
   } else if (operation.equals("DELETE")) {
    deleteDocument(jsonObject);
   }
  } catch (Exception e) {
   // TODO: handle exception
   System.out.println("Error: receiveMessage: " + e);
  }

 }

 private boolean indexExists() throws IOException {
  GetIndexRequest request = new GetIndexRequest(indexName);
  return client.indices().exists(request, RequestOptions.DEFAULT);
 }

 private void postDocument(JSONObject plan) throws IOException {
  if (!indexExists()) {
   createElasticIndex();
  }

  mapOfDocuments = new LinkedHashMap<>();
  convertMapToDocumentIndex(plan, "", "plan");

  for (Map.Entry<String, Map<String, Object>> entry : mapOfDocuments.entrySet()) {
   String parentId = entry.getKey().split(":")[0];
   String objectId = entry.getKey().split(":")[1];
   IndexRequest request = new IndexRequest(indexName);
   request.id(objectId);
   request.source(entry.getValue());
   request.routing(parentId);
   request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
   IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);
   System.out.println("response id: " + indexResponse.getId() + " parent id: " + parentId);
  }
 }

 private void deleteDocument(JSONObject jsonObject) throws IOException {
  keys = new ArrayList<>();
  convertToKeys(jsonObject);

  for (String key : keys) {
   DeleteRequest request = new DeleteRequest(indexName, key);
   DeleteResponse deleteResponse = client.delete(request, RequestOptions.DEFAULT);
   if (deleteResponse.getResult() == DocWriteResponse.Result.NOT_FOUND) {
    System.out.println("Document " + key + " Not Found!!");
   }
  }
 }

 private Map<String, Map<String, Object>> convertToKeys(JSONObject jsonObject) {

  Map<String, Map<String, Object>> map = new HashMap<>();
  Map<String, Object> valueMap = new HashMap<>();

  for (String key : jsonObject.keySet()) {
   String redisKey = jsonObject.get("objectId").toString();
   Object value = jsonObject.get(key);

   if (value instanceof JSONObject) {
    convertToKeys((JSONObject) value);
   } else if (value instanceof JSONArray) {
    convertToKeysList((JSONArray) value);
   } else {
    valueMap.put(key, value);
    map.put(redisKey, valueMap);
   }
  }

  keys.add(jsonObject.get("objectId").toString());
  return map;
 }

 private List<Object> convertToKeysList(JSONArray jsonArray) {
  List<Object> list = new ArrayList<>();
  for (Object value : jsonArray) {
   if (value instanceof JSONArray) {
    value = convertToKeysList((JSONArray) value);
   } else if (value instanceof JSONObject) {
    value = convertToKeys((JSONObject) value);
   }
   list.add(value);
  }
  return list;
 }

 private Map<String, Map<String, Object>> convertMapToDocumentIndex(JSONObject jsonObject, String parentId,
   String objectName) {

  Map<String, Map<String, Object>> map = new HashMap<>();
  Map<String, Object> valueMap = new HashMap<>();
  Iterator<String> iterator = jsonObject.keys();

  while (iterator.hasNext()) {
   String key = iterator.next();
   String redisKey = jsonObject.get("objectType") + ":" + parentId;
   Object value = jsonObject.get(key);

   if (value instanceof JSONObject) {

    convertMapToDocumentIndex((JSONObject) value, jsonObject.get("objectId").toString(), key);

   } else if (value instanceof JSONArray) {

    convertToList((JSONArray) value, jsonObject.get("objectId").toString(), key);

   } else {
    valueMap.put(key, value);
    map.put(redisKey, valueMap);
   }
  }

  Map<String, Object> temp = new HashMap<>();
  if (objectName == "plan") {
   valueMap.put("plan_join", objectName);
  } else {
   temp.put("name", objectName);
   temp.put("parent", parentId);
   valueMap.put("plan_join", temp);
  }

  String id = parentId + ":" + jsonObject.get("objectId").toString();
  System.out.println(valueMap);
  mapOfDocuments.put(id, valueMap);

  return map;
 }
 
    private List<Object> convertToList(JSONArray array, String parentId, String objectName) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = convertToList((JSONArray) value, parentId, objectName);
            } else if (value instanceof JSONObject) {
                value = convertMapToDocumentIndex((JSONObject) value, parentId, objectName);
            }
            list.add(value);
        }
        return list;
    } 
    
    private void createElasticIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(Settings.builder().put("index.number_of_shards", 1).put("index.number_of_replicas", 1));
        XContentBuilder mapping = getMapping();
        request.mapping(mapping);
        client.indices().create(request, RequestOptions.DEFAULT);        
        /*
        CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);

        boolean acknowledged = createIndexResponse.isAcknowledged();
        System.out.println("Index Creation:" + acknowledged);
  */
    }
    
    private XContentBuilder getMapping() throws IOException {

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            builder.startObject("properties");
            {
                builder.startObject("plan");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("planType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("creationDate");
                        {
                            builder.field("type", "date");
                            builder.field("format", "MM-dd-yyyy");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("planCostShares");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("copay");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("deductible");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("linkedPlanServices");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("linkedService");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("name");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("planserviceCostShares");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("copay");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("deductible");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("plan_join");
                {
                    builder.field("type", "join");
                    builder.field("eager_global_ordinals", "true");
                    builder.startObject("relations");
                    {
                        builder.array("plan", "planCostShares", "linkedPlanServices");
                        builder.array("linkedPlanServices", "linkedService", "planserviceCostShares");
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();

        return builder;

    }    

    
    

}