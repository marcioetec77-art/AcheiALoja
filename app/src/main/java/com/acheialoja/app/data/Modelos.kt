package com.acheialoja.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Máximo de pontos (entrada/vagas) que um motoboy pode marcar num shopping que ele sugeriu. */
const val MAX_PONTOS_USUARIO = 3

/** Pontos que o motoboy pode marcar ao sugerir um shopping. */
val TIPOS_PONTO_USUARIO = listOf("entrada_motoboy", "estacionamento", "entrada")

/** Item da lista de shoppings. */
data class ShoppingResumo(
    val id: String,
    val nome: String,
    val cidade: String,
    /** false = sugerido por um motoboy e ainda não aprovado pelo administrador. */
    val ativo: Boolean = true,
    /** uid de quem sugeriu (vazio nos criados pelo administrador). */
    val criadoPor: String = "",
)

/**
 * Shopping sobre o Google Maps: posição inicial da câmera + marcações feitas pelo administrador.
 * Fica no Firestore em shoppings/{id}: nome, cidade, ativo, dados (texto JSON).
 */
data class Shopping(
    val id: String,
    val nome: String,
    val cidade: String,
    val observacoes: String,
    val lat: Double,
    val lng: Double,
    val zoom: Float,
    val itens: List<Item>,
    val ativo: Boolean = true,
    val criadoPor: String = "",
)

/**
 * Marcação no mapa.
 * tipo = "loja" ou um ponto: "entrada_motoboy", "estacionamento", "retirada", "entrada", "banheiro", "escada", "elevador".
 * piso = nome curto do andar mostrado pelo Google (ex.: "1", "2", "T"); vazio = qualquer piso.
 */
data class Item(
    val id: String,
    val tipo: String,
    val nome: String,
    val categoria: String,
    val numero: String,
    val dica: String,
    val lat: Double,
    val lng: Double,
    val piso: String,
) {
    val eLoja: Boolean get() = tipo == "loja"
    val eComida: Boolean get() = eLoja && categoria.isNotBlank() && categoria != "outros"
    val titulo: String get() = if (eLoja) nome else nome.ifBlank { nomePonto(tipo) }
}

object DadosShopping {

    fun ler(id: String, nome: String, cidade: String, texto: String?): Shopping {
        val o = if (texto.isNullOrBlank()) JSONObject() else JSONObject(texto)
        val itens = o.optJSONArray("itens")
        return Shopping(
            id = id,
            nome = nome,
            cidade = cidade,
            observacoes = o.optString("observacoes"),
            lat = o.optDouble("lat", -23.5505),
            lng = o.optDouble("lng", -46.6333),
            zoom = o.optDouble("zoom", 17.0).toFloat(),
            itens = if (itens == null) emptyList() else (0 until itens.length()).mapNotNull { i ->
                val j = itens.optJSONObject(i) ?: return@mapNotNull null
                Item(
                    id = j.optString("id").ifBlank { "i$i" },
                    tipo = j.optString("tipo", "loja"),
                    nome = j.optString("nome"),
                    categoria = j.optString("categoria", "outros"),
                    numero = j.optString("numero"),
                    dica = j.optString("dica"),
                    lat = j.optDouble("lat"),
                    lng = j.optDouble("lng"),
                    piso = j.optString("piso"),
                )
            }.filter { !it.lat.isNaN() && !it.lng.isNaN() },
        )
    }

    fun escrever(s: Shopping): String = JSONObject().apply {
        put("observacoes", s.observacoes)
        put("lat", s.lat)
        put("lng", s.lng)
        put("zoom", s.zoom.toDouble())
        put("itens", JSONArray().apply {
            s.itens.forEach { it2 ->
                put(JSONObject().apply {
                    put("id", it2.id); put("tipo", it2.tipo); put("nome", it2.nome)
                    put("categoria", it2.categoria); put("numero", it2.numero); put("dica", it2.dica)
                    put("lat", it2.lat); put("lng", it2.lng); put("piso", it2.piso)
                })
            }
        })
    }.toString()
}

fun novoId(): String = "i" + System.currentTimeMillis().toString(36)

fun nomeCategoria(c: String): String =
    CATEGORIAS.firstOrNull { it.first == c }?.second ?: c.replaceFirstChar { it.uppercase() }

fun nomePonto(tipo: String): String = when (tipo) {
    "entrada_motoboy" -> "Entrada de motoboys"
    "estacionamento" -> "Vagas de motos"
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

/** Categorias de loja (código, nome). */
val CATEGORIAS = listOf(
    "lanches" to "Lanches / fast food",
    "pizza" to "Pizzaria",
    "restaurante" to "Restaurante",
    "brasileira" to "Brasileira",
    "japonesa" to "Japonesa",
    "italiana" to "Italiana",
    "churrasco" to "Churrasco",
    "mexicana" to "Mexicana",
    "cafe" to "Café",
    "padaria" to "Padaria",
    "sobremesa" to "Doces / sorvete",
    "bar" to "Bar",
    "saudavel" to "Saudável",
    "bebidas" to "Sucos / bebidas",
    "mercado" to "Mercado",
    "outros" to "Outra loja (não é comida)",
)

val TIPOS_PONTO = listOf(
    "entrada_motoboy", "estacionamento", "retirada", "entrada", "banheiro", "escada", "elevador",
)
