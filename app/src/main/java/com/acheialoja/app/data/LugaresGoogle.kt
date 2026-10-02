package com.acheialoja.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max

/** Lugar de comida encontrado no Google. */
data class LugarGoogle(
    val id: String,
    val nome: String,
    val categoria: String,
    val lat: Double,
    val lng: Double,
)

/**
 * Busca lojas de comida do Google (Places API "New" - Nearby Search) dentro de uma área.
 * Usado só pelo administrador, para importar as lojas de um shopping de uma vez.
 *
 * A busca devolve no máximo 20 lugares por pedido, então a área é dividida em
 * vários círculos pequenos e os resultados repetidos são descartados.
 */
object LugaresGoogle {

    private const val URL_BUSCA = "https://places.googleapis.com/v1/places:searchNearby"
    private const val RAIO_M = 100.0
    private const val PASSO_M = 140.0
    private const val MAX_CIRCULOS = 16

    // Tipos mais completos; se o Google recusar algum, usa a lista básica.
    private val TIPOS_COMPLETOS = listOf(
        "restaurant", "fast_food_restaurant", "cafe", "coffee_shop", "bakery",
        "ice_cream_shop", "dessert_shop", "meal_takeaway", "bar", "sandwich_shop",
    )
    private val TIPOS_BASICOS = listOf(
        "restaurant", "fast_food_restaurant", "cafe", "bakery", "ice_cream_shop", "meal_takeaway", "bar",
    )

    class ErroGoogle(msg: String) : Exception(msg)

    /**
     * Procura lojas de comida no retângulo (sul, oeste, norte, leste).
     * [aoProgresso] recebe (feitos, total) para mostrar o andamento.
     */
    suspend fun buscarComida(
        contexto: Context,
        sul: Double, oeste: Double, norte: Double, leste: Double,
        aoProgresso: (Int, Int) -> Unit = { _, _ -> },
    ): List<LugarGoogle> = withContext(Dispatchers.IO) {
        val chave = chaveApi(contexto) ?: throw ErroGoogle("Chave do Google Maps não encontrada no app.")
        val cert = certificadoSha1(contexto)
        val circulos = grade(sul, oeste, norte, leste)
        val achados = LinkedHashMap<String, LugarGoogle>()
        var tipos = TIPOS_COMPLETOS

        for ((i, c) in circulos.withIndex()) {
            val resposta = try {
                pedir(chave, contexto.packageName, cert, c.first, c.second, tipos)
            } catch (e: ErroGoogle) {
                if (tipos === TIPOS_COMPLETOS && e.message.orEmpty().contains("type", ignoreCase = true)) {
                    tipos = TIPOS_BASICOS
                    pedir(chave, contexto.packageName, cert, c.first, c.second, tipos)
                } else throw e
            }
            resposta.filter { it.lat in sul..norte && it.lng in oeste..leste }
                .forEach { achados.putIfAbsent(it.id, it) }
            withContext(Dispatchers.Main) { aoProgresso(i + 1, circulos.size) }
        }
        achados.values.toList()
    }

    /** Centros dos círculos que cobrem a área (no máximo [MAX_CIRCULOS]). */
    private fun grade(sul: Double, oeste: Double, norte: Double, leste: Double): List<Pair<Double, Double>> {
        val latMeio = (sul + norte) / 2
        val mPorGrauLat = 111_320.0
        val mPorGrauLng = 111_320.0 * cos(Math.toRadians(latMeio))
        val altura = (norte - sul) * mPorGrauLat
        val largura = (leste - oeste) * mPorGrauLng
        var passo = PASSO_M
        var nx: Int
        var ny: Int
        while (true) {
            nx = max(1, ceil(largura / passo).toInt())
            ny = max(1, ceil(altura / passo).toInt())
            if (nx * ny <= MAX_CIRCULOS) break
            passo *= 1.25
        }
        val lista = mutableListOf<Pair<Double, Double>>()
        for (iy in 0 until ny) for (ix in 0 until nx) {
            val lat = sul + (norte - sul) * (iy + 0.5) / ny
            val lng = oeste + (leste - oeste) * (ix + 0.5) / nx
            lista += lat to lng
        }
        return lista
    }

    private fun pedir(
        chave: String, pacote: String, cert: String,
        lat: Double, lng: Double, tipos: List<String>,
    ): List<LugarGoogle> {
        val corpo = JSONObject().apply {
            put("includedTypes", JSONArray(tipos))
            put("maxResultCount", 20)
            put("rankPreference", "DISTANCE")
            put("languageCode", "pt-BR")
            put("locationRestriction", JSONObject().put("circle", JSONObject().apply {
                put("center", JSONObject().put("latitude", lat).put("longitude", lng))
                put("radius", RAIO_M)
            }))
        }.toString()

        val con = (URL(URL_BUSCA).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Goog-Api-Key", chave)
            setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.location,places.primaryType,places.types")
            // Necessários porque a chave está restrita ao app Android
            setRequestProperty("X-Android-Package", pacote)
            if (cert.isNotEmpty()) setRequestProperty("X-Android-Cert", cert)
        }
        try {
            con.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
            val codigo = con.responseCode
            val texto = (if (codigo in 200..299) con.inputStream else con.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (codigo !in 200..299) {
                val msg = try {
                    JSONObject(texto).optJSONObject("error")?.optString("message")
                } catch (e: Exception) {
                    null
                }
                throw ErroGoogle(explicar(codigo, msg.orEmpty()))
            }
            val lugares = JSONObject(texto).optJSONArray("places") ?: return emptyList()
            return (0 until lugares.length()).mapNotNull { i ->
                val p = lugares.optJSONObject(i) ?: return@mapNotNull null
                val loc = p.optJSONObject("location") ?: return@mapNotNull null
                val nome = p.optJSONObject("displayName")?.optString("text").orEmpty()
                if (nome.isBlank()) return@mapNotNull null
                val tiposLugar = buildList {
                    add(p.optString("primaryType"))
                    p.optJSONArray("types")?.let { t -> for (j in 0 until t.length()) add(t.optString(j)) }
                }
                LugarGoogle(
                    id = p.optString("id"),
                    nome = nome,
                    categoria = categoriaDe(tiposLugar),
                    lat = loc.optDouble("latitude"),
                    lng = loc.optDouble("longitude"),
                )
            }.filter { !it.lat.isNaN() && !it.lng.isNaN() }
        } finally {
            con.disconnect()
        }
    }

    private fun explicar(codigo: Int, msg: String): String = when {
        msg.contains("not been used", true) || msg.contains("disabled", true) ->
            "O Places API (New) não está ativado no Google Cloud."
        codigo == 403 && msg.contains("blocked", true) ->
            "A chave não tem permissão para o Places API (New). Inclua esse serviço nas restrições da chave."
        codigo == 403 ->
            "O Google recusou a chave (403). Confira as restrições da chave. $msg"
        else -> "Erro do Google ($codigo). $msg"
    }

    /** Converte os tipos do Google para as categorias do app. */
    fun categoriaDe(tipos: List<String>): String {
        val t = tipos.joinToString(" ")
        return when {
            "pizza" in t -> "pizza"
            "sushi" in t || "japanese" in t || "ramen" in t -> "japonesa"
            "italian" in t -> "italiana"
            "steak_house" in t || "barbecue" in t -> "churrasco"
            "brazilian" in t -> "brasileira"
            "mexican" in t -> "mexicana"
            "ice_cream" in t || "dessert" in t || "confectionery" in t || "chocolate" in t ||
                "donut" in t || "candy" in t -> "sobremesa"
            "acai" in t || "juice" in t || "vegan" in t || "vegetarian" in t || "salad" in t -> "saudavel"
            "hamburger" in t || "fast_food" in t || "sandwich" in t || "hot_dog" in t || "chicken" in t -> "lanches"
            "bakery" in t -> "padaria"
            "cafe" in t || "coffee" in t || "tea_house" in t -> "cafe"
            "bar" in t.split(" ") || "pub" in t || "wine_bar" in t -> "bar"
            "meal_takeaway" in t -> "lanches"
            else -> "restaurante"
        }
    }

    /** A chave fica no AndroidManifest (preenchida pelo GitHub na hora de gerar o app). */
    private fun chaveApi(contexto: Context): String? = try {
        @Suppress("DEPRECATION")
        val info = contexto.packageManager.getApplicationInfo(contexto.packageName, PackageManager.GET_META_DATA)
        info.metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** SHA-1 do certificado que assinou o app (o Google confere com a restrição da chave). */
    @Suppress("DEPRECATION")
    private fun certificadoSha1(contexto: Context): String = try {
        val pm = contexto.packageManager
        val assinaturas = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(contexto.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(contexto.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val bytes = assinaturas?.firstOrNull()?.toByteArray()
        if (bytes == null) "" else MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02X".format(it) }
    } catch (e: Exception) {
        ""
    }

    /** Usado para não importar duas vezes a mesma loja. */
    fun jaExiste(lugar: LugarGoogle, itens: List<Item>): Boolean {
        val n = normalizar(lugar.nome)
        return itens.any { it.eLoja && normalizar(it.nome) == n }
    }
}
