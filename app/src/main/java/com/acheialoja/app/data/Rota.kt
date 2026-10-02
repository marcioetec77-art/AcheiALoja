package com.acheialoja.app.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Linha da entrada escolhida até a loja + texto para o motoboy. */
data class ResultadoRota(
    val origem: Item,
    val destino: Item,
    val metros: Int,
    val aviso: String,
)

/**
 * Traça a linha azul: parte da entrada de motoboys (ou, se não houver, das vagas de motos
 * ou de uma entrada comum) mais próxima da loja, dando preferência ao mesmo piso.
 */
object Rota {

    fun calcular(s: Shopping, loja: Item): ResultadoRota? {
        val origem = listOf("entrada_motoboy", "estacionamento", "entrada").firstNotNullOfOrNull { tipo ->
            val candidatos = s.itens.filter { it.tipo == tipo }
            candidatos.minByOrNull { c ->
                val outroPiso = c.piso.isNotBlank() && loja.piso.isNotBlank() && c.piso != loja.piso
                distancia(c, loja) + if (outroPiso) 100_000.0 else 0.0
            }
        } ?: return null

        val metros = distancia(origem, loja).toInt()
        val deOnde = when (origem.tipo) {
            "entrada_motoboy" -> "da entrada de motoboys"
            "estacionamento" -> "das vagas de motos"
            else -> "da entrada"
        }
        val trocaPiso = origem.piso.isNotBlank() && loja.piso.isNotBlank() && origem.piso != loja.piso
        val aviso = buildString {
            append("Linha azul: cerca de $metros m em linha reta $deOnde.")
            if (trocaPiso) {
                append(" A loja fica no piso ${loja.piso} e a entrada no piso ${origem.piso}: use escada ou elevador.")
            }
        }
        return ResultadoRota(origem, loja, metros, aviso)
    }

    /** Distância em metros entre dois pontos (fórmula de Haversine). */
    fun distancia(a: Item, b: Item): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
