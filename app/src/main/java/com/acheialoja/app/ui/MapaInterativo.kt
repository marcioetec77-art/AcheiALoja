package com.acheialoja.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.acheialoja.app.data.Item
import com.acheialoja.app.ui.theme.AzulNoite
import com.acheialoja.app.ui.theme.LaranjaEscuro
import com.acheialoja.app.ui.theme.Verde
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** Grupos de cores das lojas de comida (mesmas cores da legenda). */
data class GrupoCategoria(val nome: String, val cor: Color)

val GRUPOS_COMIDA = listOf(
    GrupoCategoria("Lanches", Color(0xFFE07A10)),
    GrupoCategoria("Pizzaria", Color(0xFFD62828)),
    GrupoCategoria("Restaurantes", Color(0xFF218C80)),
    GrupoCategoria("Café / padaria", Color(0xFF8D5B3E)),
    GrupoCategoria("Doces / sorvete", Color(0xFFC2448A)),
    GrupoCategoria("Bares", Color(0xFF7B4FD1)),
    GrupoCategoria("Saudável / sucos", Color(0xFF3E9A42)),
)

val COR_OUTRAS_LOJAS = Color(0xFF8A9099)
val COR_ROTA = Color(0xFF2F6FDE)

/** null = não é comida (aparece em cinza). */
fun grupoDaCategoria(categoria: String): GrupoCategoria? = when (categoria) {
    "", "outros" -> null
    "lanches", "mercado" -> GRUPOS_COMIDA[0]
    "pizza" -> GRUPOS_COMIDA[1]
    "cafe", "padaria" -> GRUPOS_COMIDA[3]
    "sobremesa" -> GRUPOS_COMIDA[4]
    "bar" -> GRUPOS_COMIDA[5]
    "saudavel", "bebidas" -> GRUPOS_COMIDA[6]
    else -> GRUPOS_COMIDA[2] // restaurante, italiana, japonesa, brasileira, churrasco, mexicana...
}

fun corDaLoja(categoria: String): Color = grupoDaCategoria(categoria)?.cor ?: COR_OUTRAS_LOJAS

fun corDoPonto(tipo: String): Color = when (tipo) {
    "entrada_motoboy" -> Verde
    "estacionamento" -> Color(0xFF2F6FDE)
    "retirada" -> LaranjaEscuro
    "entrada" -> AzulNoite
    "banheiro" -> Color(0xFF7A5AF8)
    else -> Color(0xFF5B6472)
}

fun letraDoPonto(tipo: String): String = when (tipo) {
    "entrada_motoboy" -> "M"
    "estacionamento" -> "P"
    "retirada" -> "R"
    "entrada" -> "E"
    "escada" -> "≡"
    "elevador" -> "⇅"
    "banheiro" -> "WC"
    else -> "•"
}

/**
 * Desenha os ícones que aparecem por cima do Google Maps:
 *  - lojas: etiqueta colorida com o nome e contorno preto, com uma "pontinha" embaixo;
 *  - pontos: bolinha colorida com a letra (M = entrada de motoboys, P = vagas...).
 * Só pode ser usado depois que o mapa carregou (dentro do GoogleMap).
 */
class IconesMapa(private val densidade: Float) {

    private val cache = HashMap<String, BitmapDescriptor>()

    fun de(item: Item, destaque: Boolean): BitmapDescriptor =
        if (item.eLoja) loja(item.nome, corDaLoja(item.categoria), destaque)
        else ponto(item.tipo, destaque)

    fun loja(nome: String, cor: Color, destaque: Boolean): BitmapDescriptor {
        val texto = nome.ifBlank { "Loja" }.let { if (it.length > 22) it.take(21) + "…" else it }
        return cache.getOrPut("L|$texto|${cor.toArgb()}|$destaque") {
            val d = densidade
            val borda = (if (destaque) 3f else 1.5f) * d
            val pincelTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = (if (destaque) 14f else 12f) * d
                typeface = Typeface.DEFAULT_BOLD
            }
            val larguraTexto = pincelTexto.measureText(texto)
            val padH = 7f * d
            val padV = 4f * d
            val ponta = 6f * d
            val altCaixa = pincelTexto.textSize + padV * 2
            val w = (larguraTexto + padH * 2 + borda * 2).toInt() + 1
            val h = (altCaixa + ponta + borda * 2).toInt() + 1
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            val caixa = RectF(borda, borda, w - borda, borda + altCaixa)
            val forma = Path().apply {
                addRoundRect(caixa, 6f * d, 6f * d, Path.Direction.CW)
                moveTo(w / 2f - ponta, caixa.bottom - 1f)
                lineTo(w / 2f, caixa.bottom + ponta)
                lineTo(w / 2f + ponta, caixa.bottom - 1f)
                close()
            }
            c.drawPath(forma, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor.toArgb() })
            c.drawPath(forma, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = borda
                strokeJoin = Paint.Join.ROUND
            })
            val baseTexto = caixa.centerY() - (pincelTexto.descent() + pincelTexto.ascent()) / 2f
            c.drawText(texto, caixa.left + padH, baseTexto, pincelTexto)
            BitmapDescriptorFactory.fromBitmap(bmp)
        }
    }

    fun ponto(tipo: String, destaque: Boolean): BitmapDescriptor =
        cache.getOrPut("P|$tipo|$destaque") {
            val d = densidade
            val raio = (if (destaque) 17f else 13f) * d
            val lado = (raio * 2 + 4 * d).toInt()
            val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = lado / 2f
            c.drawCircle(cx, cx, raio + 1.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK })
            c.drawCircle(cx, cx, raio, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
            c.drawCircle(cx, cx, raio - 2f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = corDoPonto(tipo).toArgb() })
            val letra = letraDoPonto(tipo)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                textSize = (if (letra.length > 1) 0.75f else 1f) * raio
            }
            c.drawText(letra, cx, cx - (p.descent() + p.ascent()) / 2f, p)
            BitmapDescriptorFactory.fromBitmap(bmp)
        }
}
