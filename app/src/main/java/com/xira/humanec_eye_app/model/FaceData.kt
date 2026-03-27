package com.xira.humanec_eye_app.model

data class FaceData(
    val name: String, // Name of the person
    val code: String, // Unique identifier for the face
    val embedding: FloatArray // Face embedding (feature vector)
) {
    // Override equals and hashCode to handle FloatArray comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceData

        if (name != other.name) return false
        if (code != other.code) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + code.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}