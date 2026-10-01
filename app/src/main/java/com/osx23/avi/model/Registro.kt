package com.osx23.avi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Registro(
    val id: String? = null,
    val tipo: String,
    val via: Int,
    val placa: String? = null,
    @SerialName("texto_reconocido")
    val textoReconocido: String? = null,
    @SerialName("creado_en")
    val creadoEn: String? = null
)

@Serializable
data class RegistroInsert(
    val tipo: String,
    val via: Int,
    val placa: String? = null,
    @SerialName("texto_reconocido")
    val textoReconocido: String? = null
)

fun Registro.toInsert() = RegistroInsert(
    tipo = tipo,
    via = via,
    placa = placa,
    textoReconocido = textoReconocido
)
