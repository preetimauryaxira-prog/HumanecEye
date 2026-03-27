package com.xira.humanec_eye_app;

import static android.content.ContentValues.TAG;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;

public class Embedding {

    // Encode float array to JSON string
    public static String encodeEmbeddingsToJson(float[] embeddings) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (float value : embeddings) {
            jsonArray.put(value);
        }
        return jsonArray.toString();
    }

    // Decode JSON string back to float array

    public static float[] decodeEmbedding(String embeddingString) {
        if (embeddingString == null || embeddingString.isEmpty()) {
            Log.e(TAG, "Embedding string is null or empty");
            return null;
        }

        try {
            Log.d(TAG, "Raw embedding string: " + embeddingString);
            String cleanedString = embeddingString.trim();
            if (cleanedString.startsWith("\"") && cleanedString.endsWith("\"")) {
                cleanedString = cleanedString.substring(1, cleanedString.length() - 1);
            }
            if (cleanedString.startsWith("\\\"") && cleanedString.endsWith("\\\"")) {
                cleanedString = cleanedString.substring(2, cleanedString.length() - 2);
            }

            cleanedString = cleanedString.replace("[", "").replace("]", "").trim();
            Log.d(TAG, "Cleaned embedding string: " + cleanedString);

            // Split the string into individual values
            String[] stringValues = cleanedString.split(",");
            float[] embedding = new float[stringValues.length];

            // Convert each value to float
            for (int j = 0; j < stringValues.length; j++) {
                String value = stringValues[j].trim();
                embedding[j] = Float.parseFloat(value); // Explicitly parse as float
            }

            Log.d(TAG, "Successfully decoded embedding, length: " + embedding.length + ", first value: " + embedding[0]);
            return embedding;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode embedding: " + embeddingString, e);
            return null;
        }
    }
}
