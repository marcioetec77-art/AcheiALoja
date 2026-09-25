package com.acheialoja.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Item da lista de shoppings (sem o mapa, que é carregado só ao abrir). */
data class ShoppingResumo(
    val id: String,
    val nome: String,
    val cidade: String,
)

/** Mapa completo de um shopping. Coordenadas em "unidades de desenho" (0..largura, 0..altura). */
data class Mapa(
    val id: String,
    val nome: String,
    val cidade: String,
    val endereco: String,
    val observacoes: String,
    val largura: Float,
    val altura: Float,
    val pisos: List<Piso>,
)

data class Piso(
    val id: String,
    val nome: String,
    val formas: List<Forma>,
    val lojas: List<Loja>,
    val pontos: List<Ponto>,
)

/** tipo: "contorno" | "corredor" | "praca" | "bloqueado" */
data class Forma(
    val tipo: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val rotulo: String,
)

data class Loja(
    val id: String,
    val nome: String,
    val categoria: String,
    val numero: String,
    val dica: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
) {
    val eComida: Boolean get() = categoria.isNotBlank() && categoria != "outros"
    fun contem(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
}

/** tipo: "entrada_motoboy" | "estacionamento" | "retirada" | "entrada" | "escada" | "elevador" | "banheiro" */
data class Ponto(
    val tipo: String,
    val x: Float, val y: Float,
    val rotulo: String,
)

object LeitorMapa {

    fun ler(texto: String, idPadrao: String = ""): Mapa {
        val o = JSONObject(texto)
        return Mapa(
            id = o.optString("id", idPadrao).ifBlank { idPadrao },
            nome = o.optString("nome"),
            cidade = o.optString("cidade"),
            endereco = o.optString("endereco"),
            observacoes = o.optString("observacoes"),
            largura = o.optDouble("largura", 1000.0).toFloat(),
            altura = o.optDouble("altura", 700.0).toFloat(),
            pisos = o.optJSONArray("pisos").objetos().map(::lerPiso),
        )
    }

    private fun lerPiso(o: JSONObject) = Piso(
        id = o.optString("id"),
        nome = o.optString("nome"),
        formas = o.optJSONArray("formas").objetos().map {
            Forma(
                tipo = it.optString("tipo", "corredor"),
                x = it.f("x"), y = it.f("y"), w = it.f("w"), h = it.f("h"),
                rotulo = it.optString("rotulo"),
            )
        },
        lojas = o.optJSONArray("lojas").objetos().mapIndexed { i, j ->
            Loja(
                id = j.optString("id").ifBlank { "loja$i" },
                nome = j.optString("nome"),
                categoria = j.optString("categoria", "outros"),
                numero = j.optString("numero"),
                dica = j.optString("dica"),
                x = j.f("x"), y = j.f("y"), w = j.f("w"), h = j.f("h"),
            )
        },
        pontos = o.optJSONArray("pontos").objetos().map {
            Ponto(
                tipo = it.optString("tipo"),
                x = it.f("x"), y = it.f("y"),
                rotulo = it.optString("rotulo"),
            )
        },
    )

    private fun JSONObject.f(chave: String) = optDouble(chave, 0.0).toFloat()

    private fun JSONArray?.objetos(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

fun nomeCategoria(c: String): String = when (c) {
    "cafe" -> "Café"
    "padaria" -> "Padaria"
    "lanches" -> "Lanches"
    "pizza" -> "Pizzaria"
    "japonesa" -> "Japonesa"
    "mexicana" -> "Mexicana"
    "brasileira" -> "Brasileira"
    "churrasco" -> "Churrasco"
    "saudavel" -> "Saudável"
    "sobremesa" -> "Doces e sobremesas"
    "bebidas" -> "Bebidas"
    "mercado" -> "Mercado"
    "outros" -> "Outros"
    else -> c.replaceFirstChar { it.uppercase() }
}

fun nomePonto(tipo: String): String = when (tipo) {
    "entrada_motoboy" -> "Entrada de motoboys"
    "estacionamento" -> "Estacionamento de motos"
    "retirada" -> "Balcão de retirada"
    "entrada" -> "Entrada"
    "escada" -> "Escada"
    "elevador" -> "Elevador"
    "banheiro" -> "Banheiro"
    else -> tipo
}

/** Remove acentos e deixa minúsculo, para a busca achar "grao" em "Grão". */
fun normalizar(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()
