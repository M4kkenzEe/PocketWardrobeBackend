package com.example.database.domain.repository

import com.example.database.data.model.Clothe

interface ClotheRepository {
    suspend fun getAllClothes(userId: Int): List<Clothe>
    suspend fun getClotheByName(name: String, idUser: Int): Clothe
    suspend fun getClotheById(clotheId: Int, idUser: Int): Clothe
    suspend fun addClothe(clothe: Clothe, idUser: Int): Int
    suspend fun removeClothe(name: String): Boolean
}