package com.quicktutorialz.nio.utils;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author alessandroargentieri
 *
 * Util class which encapsulate the use of Gson class to parse Json messages from the request to a Java class
 * and from a Java class to a Json string message
 * @param <T>
 */
public class JsonConverter<T> {

    private static JsonConverter instance = null;
    public static JsonConverter getInstance(){
        if(instance == null){
            instance = new JsonConverter();
        }
        return instance;
    }

    Gson gson = new Gson();


    public T getDataFromJsonInputStream(final InputStream in, final Class clazz) throws IOException {
        Reader reader = new InputStreamReader(in, "UTF-8");
        return (T) gson.fromJson(reader, clazz);
    }

    public String getJsonOf(final Object object){
        return gson.toJson(object);
    }



    public T getObjectFromJson(final String jsonString, final Class clazz){
        return (T) gson.fromJson(jsonString, clazz);
    }
}