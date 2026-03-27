package com.xira.humanec_eye_app.ui.camera

import android.content.Context
import android.util.Log
import com.xira.humanec_eye_app.model.FaceData
import org.json.JSONArray
import org.json.JSONObject
import java.io.*

class FileAccess(private val context: Context) {

    companion object {
        private const val TAG = "FileAccess"
        private const val FACE_DATA_FILE = "face_embeddings.json"
    }

    // Save registered faces to a JSON file
    fun saveRegisteredFaces(registeredFaces: List<FaceData>) {
        try {
            val array = JSONArray()
            for (face in registeredFaces) {
                val obj = JSONObject().apply {
                    put("name", face.name)
                    put("code", face.code)
                    put("embedding", JSONArray(face.embedding.toList()))
                }
                array.put(obj)
            }

            val file = File(context.filesDir, FACE_DATA_FILE)
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving face data", e)
        }
    }

    // Load registered faces from a JSON file
    fun loadRegisteredFaces(): List<FaceData> {
        val registeredFaces = mutableListOf<FaceData>()
        try {
            val file = File(context.filesDir, FACE_DATA_FILE)
            if (file.exists()) {
                val json = file.readText()
                val array = JSONArray(json)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.getString("name")
                    val code = obj.getString("code")
                    val embArray = obj.getJSONArray("embedding")

                    Log.d(TAG, "Embedding Fetching for: $name")

                    val embedding = FloatArray(embArray.length()) { j -> embArray.getDouble(j).toFloat() }
                    Log.d(TAG, "Loaded Embedding: ${embedding.contentToString()}")

                    registeredFaces.add(FaceData(name, code, embedding))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading face data", e)
        }
        return registeredFaces
    }


    // Add a face entry to the JSON file
    fun addFace(faceData: FaceData) {
        try {
            val registeredFaces = loadRegisteredFaces().toMutableList()
            registeredFaces.add(faceData)
            saveRegisteredFaces(registeredFaces)
            Log.d(TAG, "Successfully added face: ${faceData.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding face data", e)
        }
    }

    // Remove a face entry by searching with code
    fun removeFaceByCode(code: String) {
        try {
            val registeredFaces = loadRegisteredFaces().toMutableList()
            val removed = registeredFaces.removeIf { it.code == code }

            if (removed) {
                saveRegisteredFaces(registeredFaces)
                Log.d(TAG, "Successfully removed face with code: $code")
            } else {
                Log.d(TAG, "Face with code not found: $code")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing face data", e)
        }
    }
    fun clearAllFaces() {
        try {
            val file = File(context.filesDir, FACE_DATA_FILE)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Successfully cleared all face data")
                } else {
                    Log.e(TAG, "Failed to delete face data file")
                }
            } else {
                Log.d(TAG, "No face data file found to delete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing face data", e)
        }
    }
}