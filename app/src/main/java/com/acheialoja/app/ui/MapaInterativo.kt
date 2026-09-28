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
import androidx.compose.ui.graphics.drawscope.rotate
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
    /** Modo edição: recebe o ponto tocado em coordenadas do desenho. */
    aoTocarNoDesenho: ((Float, Float) -> Unit)? = null,
) {
    val medidor = rememberTextMeasurer()
    val aoTocar by rememberUpdatedState(aoTocarLoja)
    val aoTocarEdicao by rememberUpdatedState(aoTocarNoDesenho)
    val pisoAtual by rememberUpdatedState(piso)

    val esquema = MaterialTheme.colorScheme
    val escuro = esquema.surface.luminance() < 0.5f
    val cores = if (escuro) CoresMapa(
        fundo = Color(0xFF0B0D10),
        contorno = Color(0xFF1C2128),
        borda = Color(0xFFAEB6C2),
        corredor = Color(0xFF2B313B),
        praca = Color(0xFF4A3F1E),
        bloqueado = Color(0xFF232830),
        comida = Color(0xFF6B3517),
        comidaBorda = Color(0xFFFF9A5C),
        outras = Color(0xFF2E343E),
        outrasBorda = Color(0xFF555E6C),
        texto = Color(0xFFE8EAED),
        textoFraco = Color(0xFF9AA3AF),
        rua = Color(0xFF3A4454),
        via = Color(0xFF262C35),
        textoRua = Color(0xFFC9D1DC),
    ) else CoresMapa(
        fundo = Color(0xFFEDEBE8),
        contorno = Color(0xFFF4EEE1),
        borda = Color(0xFF1F2328),
        corredor = Color.White,
        praca = Color(0xFFFFF1C7),
        bloqueado = Color(0xFFDADCE0),
        comida = Color(0xFFFFE0CC),
        comidaBorda = LaranjaEscuro,
        outras = Color(0xFFE6E7EA),
        outrasBorda = Color(0xFFB5B9C0),
        texto = AzulNoite,
        textoFraco = Color(0xFF6B7280),
        rua = Color(0xFFA9B8C8),
        via = Color(0xFFDDE2E9),
        textoRua = Color(0xFF3B4658),
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
                        val edicao = aoTocarEdicao
                        if (edicao != null) edicao(q.x, q.y)
                        else aoTocar(pisoAtual.lojas.lastOrNull { it.contem(q.x, q.y) })
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

        // Estrutura do prédio, desenhada em camadas para ter contorno só por fora:
        // 1) sombra/contorno do prédio  2) preenchimento das áreas
        // 3) contorno dos corredores    4) preenchimento dos corredores
        val espessura = 1.5f.dp.toPx()
        fun retangulo(f: com.acheialoja.app.data.Forma, cor: Color, extra: Float = 0f) =
            drawRect(cor, pos(f.x, f.y) - Offset(extra, extra), Size(f.w * s + 1f + 2 * extra, f.h * s + 1f + 2 * extra))

        // 0) ruas em volta (embaixo de tudo)
        for (f in piso.formas) if (f.tipo == "via") retangulo(f, cores.via)
        for (f in piso.formas) if (f.tipo == "rua") retangulo(f, cores.rua)

        for (f in piso.formas) if (f.tipo == "predio") retangulo(f, cores.borda, espessura)
        for (f in piso.formas) {
            if (f.tipo == "corredor" || f.tipo in TIPOS_RUA) continue
            val cor = when (f.tipo) {
                "contorno", "predio" -> cores.contorno
                "praca" -> cores.praca
                else -> cores.bloqueado
            }
            if (f.tipo == "predio") {
                retangulo(f, cor)
            } else {
                drawRoundRect(cor, pos(f.x, f.y), Size(f.w * s, f.h * s), raio)
                drawRoundRect(cores.borda, pos(f.x, f.y), Size(f.w * s, f.h * s), raio, style = Stroke(if (f.tipo == "contorno") 2.dp.toPx() else espessura))
            }
        }
        for (f in piso.formas) if (f.tipo == "corredor") retangulo(f, cores.borda, espessura)
        for (f in piso.formas) if (f.tipo == "corredor") retangulo(f, cores.corredor)
        for (f in piso.formas) {
            if (f.tipo == "nome_rua") {
                nomeDeRua(medidor, f.rotulo, pos(f.x, f.y), Size(f.w * s, f.h * s), cores.textoRua)
            } else if (f.rotulo.isNotBlank() && f.tipo != "corredor" && f.tipo != "predio" && f.tipo !in TIPOS_RUA) {
                textoCentral(medidor, f.rotulo, pos(f.x, f.y), Size(f.w * s, f.h * s), cores.textoFraco, 12f, italico = true)
            }
        }

        // Lojas (comida colorida por tipo; demais lojas em cinza)
        for (l in piso.lojas) {
            val sel = l.id == selecionada?.id
            val canto = pos(l.x, l.y)
            val tam = Size(l.w * s, l.h * s)
            val grupo = grupoDaCategoria(l.categoria)
            if (sel) {
                val m = 6.dp.toPx()
                drawRoundRect(
                    Laranja.copy(alpha = 0.35f), canto - Offset(m, m),
                    Size(tam.width + 2 * m, tam.height + 2 * m), CornerRadius(8.dp.toPx()),
                )
            }
            val preenchimento = grupo?.cor ?: cores.outras
            val borda = when {
                sel -> cores.texto
                else -> cores.borda
            }
            drawRoundRect(preenchimento, canto, tam, raio)
            drawRoundRect(borda, canto, tam, raio, style = if (sel) Stroke(3.dp.toPx()) else Stroke(espessura))
            textoCentral(
                medidor, l.nome, canto, tam,
                cor = if (grupo != null) Color.White else cores.textoFraco,
                tamanhoSp = if (grupo != null) 12f else 10f,
                negrito = grupo != null || sel,
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
    val rua: Color, val via: Color, val textoRua: Color,
)

/** Tipos de área que são ruas (desenhadas por baixo, sem contorno). */
val TIPOS_RUA = setOf("rua", "via", "nome_rua")

/** Nome de rua: texto em negrito; fica na vertical quando a área é mais alta que larga. */
private fun DrawScope.nomeDeRua(medidor: TextMeasurer, texto: String, canto: Offset, tam: Size, cor: Color) {
    if (texto.isBlank()) return
    val vertical = tam.height > tam.width
    val comprimento = if (vertical) tam.height else tam.width
    if (comprimento < 40f) return
    val layout = medidor.measure(
        texto,
        style = TextStyle(color = cor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = comprimento.toInt().coerceAtLeast(1)),
    )
    val centro = canto + Offset(tam.width / 2f, tam.height / 2f)
    val topo = centro - Offset(layout.size.width / 2f, layout.size.height / 2f)
    if (vertical) {
        rotate(degrees = -90f, pivot = centro) { drawText(layout, topLeft = topo) }
    } else {
        drawText(layout, topLeft = topo)
    }
}

/** Grupos de cores das lojas de comida (mesmas cores do esboço e da legenda). */
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

/** null = não é comida (desenhada em cinza). */
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
