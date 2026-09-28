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

// ---------- Gravação e modelos base (usados pelo modo administrador) ----------

/** Categorias que o administrador pode escolher (código, nome). */
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

val TIPOS_FORMA = listOf(
    "praca" to "Praça de alimentação",
    "corredor" to "Corredor",
    "bloqueado" to "Área fechada / estacionamento",
    "contorno" to "Contorno do prédio",
    "predio" to "Parte do prédio (planta)",
    "rua" to "Rua / avenida",
    "via" to "Rua interna / estacionamento",
    "nome_rua" to "Nome de rua (texto)",
)

object EscritorMapa {

    fun escrever(m: Mapa): String = JSONObject().apply {
        put("id", m.id)
        put("nome", m.nome)
        put("cidade", m.cidade)
        put("endereco", m.endereco)
        put("observacoes", m.observacoes)
        put("largura", m.largura.toDouble())
        put("altura", m.altura.toDouble())
        put("pisos", JSONArray().apply { m.pisos.forEach { put(piso(it)) } })
    }.toString()

    private fun piso(p: Piso) = JSONObject().apply {
        put("id", p.id)
        put("nome", p.nome)
        put("formas", JSONArray().apply {
            p.formas.forEach { f ->
                put(JSONObject().apply {
                    put("tipo", f.tipo); put("rotulo", f.rotulo)
                    put("x", r(f.x)); put("y", r(f.y)); put("w", r(f.w)); put("h", r(f.h))
                })
            }
        })
        put("lojas", JSONArray().apply {
            p.lojas.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("nome", l.nome); put("categoria", l.categoria)
                    put("numero", l.numero); put("dica", l.dica)
                    put("x", r(l.x)); put("y", r(l.y)); put("w", r(l.w)); put("h", r(l.h))
                })
            }
        })
        put("pontos", JSONArray().apply {
            p.pontos.forEach { pt ->
                put(JSONObject().apply {
                    put("tipo", pt.tipo); put("rotulo", pt.rotulo)
                    put("x", r(pt.x)); put("y", r(pt.y))
                })
            }
        })
    }

    private fun r(v: Float): Int = Math.round(v)

    /** Piso vazio retangular: contorno + corredor em cruz. */
    fun pisoBase(id: String, nome: String) = Piso(
        id = id,
        nome = nome,
        formas = listOf(
            Forma("contorno", 20f, 20f, 960f, 660f, ""),
            Forma("corredor", 20f, 310f, 960f, 80f, ""),
            Forma("corredor", 460f, 20f, 80f, 660f, ""),
        ),
        lojas = emptyList(),
        pontos = listOf(Ponto("entrada", 500f, 675f, "Entrada principal")),
    )

    /** Shopping novo, retangular, com a quantidade de pisos pedida. */
    fun shoppingBase(id: String, nome: String, cidade: String, pisos: Int): Mapa = Mapa(
        id = id,
        nome = nome,
        cidade = cidade,
        endereco = "",
        observacoes = "",
        largura = 1000f,
        altura = 700f,
        pisos = (1..pisos.coerceIn(1, 6)).map { i ->
            pisoBase(if (i == 1) "T" else "P$i", if (i == 1) "Térreo" else "Piso $i")
        },
    )
}
