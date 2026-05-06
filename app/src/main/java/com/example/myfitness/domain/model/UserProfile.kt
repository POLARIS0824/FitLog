package com.example.myfitness.domain.model

enum class Gender {
    MALE,
    FEMALE,
    OTHER
}

data class UserProfile(
    val id: Long,
    val name: String,
    val age: Int?,
    val gender: Gender?,
    val height: Float?,
    val weight: Float?,
    val trainingLevel: TrainingLevel,
)

data class TrainingLevel(

)