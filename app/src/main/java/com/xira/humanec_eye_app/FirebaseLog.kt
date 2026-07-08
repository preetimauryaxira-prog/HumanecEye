package com.xira.humanec_eye_app

import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FirebaseLogger {
    private val database: FirebaseDatabase = Firebase.database
    private val logsReference: DatabaseReference = database.getReference("logs")

    fun logMessageWithTimestamp(path: String, message: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val currentTime = Calendar.getInstance().time
        val formattedTimestamp = dateFormat.format(currentTime)
        val safePath = path
            .replace(".", "_dot_")
            .replace("#", "_hash_")
            .replace("$", "_dollar_")
            .replace("[", "_lbracket_")
            .replace("]", "_rbracket_")
            .replace(" ", "_")
        val logEntry = mapOf(
            "message" to message,
            "timestamp" to formattedTimestamp
        )
        logsReference.child(safePath).push().setValue(logEntry)
    }


    fun logData(path: String, data: Any) {

        val safePath = path
            .replace(".", "_dot_")
            .replace("#", "_hash_")
            .replace("$", "_dollar_")
            .replace("[", "_lbracket_")
            .replace("]", "_rbracket_")
            .replace(" ", "_")
        logsReference.child(safePath).push().setValue(data)
    }

    companion object {
        // Singleton instance
        @Volatile private var instance: FirebaseLogger? = null

        fun getInstance(): FirebaseLogger {
            return instance ?: synchronized(this) {
                instance ?: FirebaseLogger().also { instance = it }
            }
        }
    }
}

//package com.xira.humanec_eye_app
//
//import com.google.firebase.Firebase
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.firestore.firestore
//import java.text.SimpleDateFormat
//import java.util.Calendar
//import java.util.Locale
//
//class FirebaseLogger private constructor() {
//    private val firestore: FirebaseFirestore = Firebase.firestore
//
//    fun logMessageWithTimestamp(path: String, message: String) {
//        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
//        val currentTime = Calendar.getInstance().time
//        val formattedTimestamp = dateFormat.format(currentTime)
//        val safePath = sanitizePath(path)
//
//        val logEntry = mapOf(
//            "message" to message,
//            "timestamp" to formattedTimestamp
//        )
//
//        // Structure: Collection("sample") -> Document(safePath) -> Collection("logs") -> Document(Auto-ID)
//        firestore.collection("eye_logs")
//            .document(safePath)
//            .collection("logs")
//            .add(logEntry)
//    }
//
//    fun logData(path: String, data: Any) {
//        val safePath = sanitizePath(path)
//
//        firestore.collection("eye_logs")
//            .document(safePath)
//            .collection("logs")
//            .add(data)
//    }
//
//    // Extracted path sanitization to keep the code DRY (Don't Repeat Yourself)
//    private fun sanitizePath(path: String): String {
//        return path
//            .replace("/", "_slash_") // Added `/` replacement because Firestore document IDs cannot contain forward slashes
//            .replace(".", "_dot_")
//            .replace("#", "_hash_")
//            .replace("$", "_dollar_")
//            .replace("[", "_lbracket_")
//            .replace("]", "_rbracket_")
//            .replace(" ", "_")
//    }
//
//    companion object {
//        // Singleton instance
//        @Volatile private var instance: FirebaseLogger? = null
//
//        fun getInstance(): FirebaseLogger {
//            return instance ?: synchronized(this) {
//                instance ?: FirebaseLogger().also { instance = it }
//            }
//        }
//    }
//}