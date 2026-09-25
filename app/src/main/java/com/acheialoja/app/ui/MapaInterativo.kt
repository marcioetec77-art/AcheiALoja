package com.acheialoja.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acheialoja.app.data.Loja
import com.acheialoja.app.data.Mapa
import com.acheialoja.app.data.Piso
import com.acheialoja.app.data.Ponto
import com.acheialoja.app.ui.theme.AzulNoite
import com.acheialoja.app.ui.theme.Laranja
import com.acheialoja.app.ui.theme.LaranjaEscuro
import com.acheialoja.app.ui.theme.Verde
import kotlin.math.min

/** Guarda zoom e posição do mapa. */
@Stable
class EstadoMapa {
    var escala by mutableFloatStateOf(1f)
    var desloc by mutableStateOf(Offset.Zero)
    var tamanho by mutableStateOf(IntSize.Zero)
    var enquadrado by mutableStateOf(false)

    /** Escala que faz o shopping inteiro caber na tela. */
    fun base(m: Mapa): Float {
        if (tamanho.width == 0 || m.largura <= 0f || m.altura <= 0f) return 1f
        return min(tamanho.width / m.largura, tamanho.height / m.altura) * 0.94f
    }

    fun enquadrar(m: Mapa) {
        if (tamanho == IntSize.Zero) return
        escala = 1f
        val b = base(m)
        desloc = Offset((tamanho.width - m.largura * b) / 2f, (tamanho.height - m.altura * b) / 2f)
        enquadrado = true
    }

    /** Centraliza um ponto do desenho na tela com o zoom pedido. */
    fun focar(m: Mapa, cx: Float, cy: Float, zoom: Float = 2.5f) {
        if (tamanho == IntSize.Zero) return
        escala = zoom
        val s = base(m) * zoom
        desloc = Offset(tamanho.width / 2f - cx * s, tamanho.height / 2f - cy * s)
    }

    fun transformar(centro: Offset, pan: Offset, zoom: Float) {
        val nova = (escala * zoom).coerceIn(0.6f, 12f)
        val f = nova / escala
        desloc = (desloc - centro) * f + centro + pan
        escala = nova
    }

    fun zoomNoCentro(fator: Float) =
        transformar(Offset(tamanho.width / 2f, tamanho.height / 2f), Offset.Zero, fator)

    fun paraDesenho(p: Offset, m: Mapa): Offset = (p - desloc) / (base(m) * escala)
}

@Composable
fun MapaInterativo(
    mapa: Mapa,
    piso: Piso,
    estado: EstadoMapa,
    selecionada: Loja?,
    pontoDestacado: Ponto?,
    aoTocarLoja: (Loja?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val medidor = rememberTextMeasurer()
    val aoTocar by rememberUpdatedState(aoTocarLoja)
    val pisoAtual by rememberUpdatedState(piso)

    val esquema = MaterialTheme.colorScheme
    val escuro = esquema.surface.luminance() < 0.5f
    val cores = if (escuro) CoresMapa(
        fundo = Color(0xFF0B0D10),
        contorno = Color(0xFF1C2128),
        borda = Color(0xFF4A5260),
        corredor = Color(0xFF2B313B),
        praca = Color(0xFF4A3F1E),
        bloqueado = Color(0xFF232830),
        comida = Color(0xFF6B3517),
        comidaBorda = Color(0xFFFF9A5C),
        outras = Color(0xFF2E343E),
        outrasBorda = Color(0xFF555E6C),
        texto = Color(0xFFE8EAED),
        textoFraco = Color(0xFF9AA3AF),
    ) else CoresMapa(
        fundo = Color(0xFFEDEBE8),
        contorno = Color.White,
        borda = Color(0xFF8A8F98),
        corredor = Color(0xFFF1F1F1),
        praca = Color(0xFFFFF1C7),
        bloqueado = Color(0xFFDADCE0),
        comida = Color(0xFFFFE0CC),
        comidaBorda = LaranjaEscuro,
        outras = Color(0xFFE6E7EA),
        outrasBorda = Color(0xFFB5B9C0),
        texto = AzulNoite,
        textoFraco = Color(0xFF6B7280),
    )

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { estado.tamanho = it }
            .pointerInput(mapa) {
                detectTransformGestures { centro, pan, zoom, _ ->
                    estado.transformar(centro, pan, zoom)
                }
            }
            .pointerInput(mapa) {
                detectTapGestures(
                    onDoubleTap = { p -> estado.transformar(p, Offset.Zero, 2f) },
                    onTap = { p ->
                        val q = estado.paraDesenho(p, mapa)
                        aoTocar(pisoAtual.lojas.lastOrNull { it.contem(q.x, q.y) })
                    },
                )
            }
    ) {
        drawRect(cores.fundo)
        val s = estado.base(mapa) * estado.escala
        val d = estado.desloc
        fun pos(x: Float, y: Float) = Offset(d.x + x * s, d.y + y * s)
        val raio = CornerRadius(3.dp.toPx())
        val linha = Stroke(1.dp.toPx())

        // Estrutura do prédio
        for (f in piso.formas) {
            val cor = when (f.tipo) {
                "contorno" -> cores.contorno
                "praca" -> cores.praca
                "bloqueado" -> cores.bloqueado
                else -> cores.corredor
            }
            drawRoundRect(cor, pos(f.x, f.y), Size(f.w * s, f.h * s), raio)
            if (f.tipo == "contorno") {
                drawRoundRect(cores.borda, pos(f.x, f.y), Size(f.w * s, f.h * s), raio, style = Stroke(2.dp.toPx()))
            }
            if (f.rotulo.isNotBlank() && f.tipo != "corredor") {
                textoCentral(medidor, f.rotulo, pos(f.x, f.y), Size(f.w * s, f.h * s), cores.textoFraco, 12f, italico = true)
            }
        }

        // Lojas
        for (l in piso.lojas) {
            val sel = l.id == selecionada?.id
            val canto = pos(l.x, l.y)
            val tam = Size(l.w * s, l.h * s)
            val preenchimento = when {
                sel -> Laranja
                l.eComida -> cores.comida
                else -> cores.outras
            }
            val borda = when {
                sel -> LaranjaEscuro
                l.eComida -> cores.comidaBorda
                else -> cores.outrasBorda
            }
            drawRoundRect(preenchimento, canto, tam, raio)
            drawRoundRect(borda, canto, tam, raio, style = if (sel) Stroke(3.dp.toPx()) else linha)
            textoCentral(
                medidor, l.nome, canto, tam,
                cor = if (sel) Color.White else if (l.eComida) cores.texto else cores.textoFraco,
                tamanhoSp = if (l.eComida) 12f else 10f,
                negrito = l.eComida || sel,
            )
        }

        // Pontos (entrada de motoboy, escada, etc.)
        for (p in piso.pontos) {
            val destaque = pontoDestacado != null && p.tipo == pontoDestacado.tipo &&
                p.x == pontoDestacado.x && p.y == pontoDestacado.y
            desenharPonto(medidor, p, pos(p.x, p.y), destaque)
        }
    }
}

private data class CoresMapa(
    val fundo: Color, val contorno: Color, val borda: Color, val corredor: Color,
    val praca: Color, val bloqueado: Color, val comida: Color, val comidaBorda: Color,
    val outras: Color, val outrasBorda: Color, val texto: Color, val textoFraco: Color,
)

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

private fun DrawScope.desenharPonto(medidor: TextMeasurer, p: Ponto, centro: Offset, destaque: Boolean) {
    val r = (if (destaque) 17.dp else 12.dp).toPx()
    if (destaque) drawCircle(corDoPonto(p.tipo).copy(alpha = 0.25f), r * 2f, centro)
    drawCircle(Color.White, r + 2.dp.toPx(), centro)
    drawCircle(corDoPonto(p.tipo), r, centro)
    val letra = letraDoPonto(p.tipo)
    val layout = medidor.measure(
        letra,
        style = TextStyle(
            color = Color.White,
            fontSize = (if (letra.length > 1) 9 else 12).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    drawText(layout, topLeft = centro - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

private fun DrawScope.textoCentral(
    medidor: TextMeasurer,
    texto: String,
    canto: Offset,
    tam: Size,
    cor: Color,
    tamanhoSp: Float,
    negrito: Boolean = false,
    italico: Boolean = false,
) {
    val margem = 4.dp.toPx()
    val larguraMax = (tam.width - margem * 2).toInt()
    if (larguraMax < 28 || tam.height < 16.dp.toPx() || texto.isBlank()) return
    val layout = medidor.measure(
        texto,
        style = TextStyle(
            color = cor,
            fontSize = tamanhoSp.sp,
            fontWeight = if (negrito) FontWeight.SemiBold else FontWeight.Normal,
            fontStyle = if (italico) androidx.compose.ui.text.font.FontStyle.Italic else null,
            textAlign = TextAlign.Center,
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = if (tam.height > 44.dp.toPx()) 3 else 1,
        constraints = Constraints(maxWidth = larguraMax),
    )
    val x = canto.x + (tam.width - layout.size.width) / 2f
    val y = canto.y + (tam.height - layout.size.height) / 2f
    drawText(layout, topLeft = Offset(x, y))
}
