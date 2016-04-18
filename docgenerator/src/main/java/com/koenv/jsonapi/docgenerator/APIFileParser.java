package com.koenv.jsonapi.docgenerator;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.koenv.jsonapi.docgenerator.model.PlatformMethods;
import com.koenv.jsonapi.util.json.JSONException;
import com.koenv.jsonapi.util.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class APIFileParser {
    private File file;

    public APIFileParser(File file) {
        this.file = file;
    }

    public PlatformMethods parse() throws IOException, JSONException {
        JSONObject input = new JSONObject(Files.asCharSource(file, Charsets.UTF_8).read());
        return new PlatformMethods(input);
    }
}