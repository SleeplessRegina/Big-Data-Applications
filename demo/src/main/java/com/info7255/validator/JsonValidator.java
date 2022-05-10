package com.info7255.validator;

import java.io.IOException;
import java.io.InputStream;

import org.everit.json.schema.*;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

@Service
public class JsonValidator {
    public void validateJson(JSONObject object) throws IOException {
        try(InputStream inputStream = getClass().getResourceAsStream("/planSchema.json")){
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            Schema schema = SchemaLoader.load(rawSchema);
            schema.validate(object);
        }
    }
}