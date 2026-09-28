package com.acheialoja.app.data

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Caminho calculado: pontos no desenho (x, y) e um aviso opcional (ex.: subir de piso). */
data class ResultadoRota(val pontos: List<Pair<Float, Float>>, val aviso: String?)

/**
 * Calcula o caminho a pé da entrada de motoboys até a loja, preferindo corredores.
 * O mapa vira uma grade: corredor e praça são baratos, área de fora é média,
 * paredes/lojas são caras (só atravessa se não houver outro jeito, ex.: porta não desenhada).
 */
object Rota {
    private const val CEL = 8f
    private const val FORA = 3
    private const val RUA = 4
    private const val CORREDOR = 1
    private const val PAREDE = 40

    private val ORDEM_INICIO = listOf("entrada_motoboy", "estacionamento", "escada", "elevador", "entrada")

    fun calcular(mapa: Mapa, pisoIdx: Int, loja: Loja): ResultadoRota? {
        val piso = mapa.pisos.getOrNull(pisoIdx) ?: return null
        val inicio = ORDEM_INICIO.firstNotNullOfOrNull { t -> piso.pontos.firstOrNull { it.tipo == t } } ?: return null

        // Aviso quando a entrada de motoboys está em outro piso
        var aviso: String? = null
        if (inicio.tipo != "entrada_motoboy") {
            val pisoMoto = mapa.pisos.firstOrNull { p -> p.pontos.any { it.tipo == "entrada_motoboy" } }
            if (pisoMoto != null && pisoMoto.id != piso.id) {
                aviso = "Entre pela entrada de motoboys (${pisoMoto.nome}) e vá até o ${piso.nome}" +
                    if (inicio.tipo == "escada" || inicio.tipo == "elevador") " pela ${nomePonto(inicio.tipo).lowercase()}." else "."
            }
        }

        val gw = max(1, ceil(mapa.largura / CEL).toInt())
        val gh = max(1, ceil(mapa.altura / CEL).toInt())
        val custo = IntArray(gw * gh) { FORA }

        fun pintar(x: Float, y: Float, w: Float, h: Float, valor: Int) {
            val c0 = max(0, ceil(x / CEL - 0.5f).toInt())
            val c1 = min(gw - 1, floor((x + w) / CEL - 0.5f).toInt())
            val r0 = max(0, ceil(y / CEL - 0.5f).toInt())
            val r1 = min(gh - 1, floor((y + h) / CEL - 0.5f).toInt())
            for (r in r0..r1) for (c in c0..c1) custo[r * gw + c] = valor
        }

        for (f in piso.formas) if (f.tipo == "via" || f.tipo == "rua") pintar(f.x, f.y, f.w, f.h, RUA)
        for (f in piso.formas) if (f.tipo == "predio" || f.tipo == "contorno" || f.tipo == "bloqueado") pintar(f.x, f.y, f.w, f.h, PAREDE)
        for (l in piso.lojas) if (l.id != loja.id) pintar(l.x, l.y, l.w, l.h, PAREDE)
        for (f in piso.formas) if (f.tipo == "corredor" || f.tipo == "praca") pintar(f.x, f.y, f.w, f.h, CORREDOR)

        fun celula(x: Float, y: Float): Int {
            val c = (x / CEL).toInt().coerceIn(0, gw - 1)
            val r = (y / CEL).toInt().coerceIn(0, gh - 1)
            return r * gw + c
        }

        val alvoX = loja.x + loja.w / 2f
        val alvoY = loja.y + loja.h / 2f
        val origem = celula(inicio.x, inicio.y)
        val destino = celula(alvoX, alvoY)
        // a própria loja é "porta aberta"
        pintar(loja.x, loja.y, loja.w, loja.h, CORREDOR)

        // A* com 8 vizinhos (custos x10 em linha reta, x14 na diagonal)
        val dc = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val dr = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
        val g = IntArray(gw * gh) { Int.MAX_VALUE }
        val pai = IntArray(gw * gh) { -1 }
        val fechado = BooleanArray(gw * gh)
        val dcol = destino % gw
        val drow = destino / gw
        fun h(i: Int): Int {
            val ax = kotlin.math.abs(i % gw - dcol)
            val ay = kotlin.math.abs(i / gw - drow)
            return 10 * (ax + ay) - 6 * min(ax, ay)
        }
        val fila = PriorityQueue<Long>()
        g[origem] = 0
        fila.add((h(origem).toLong() shl 32) or origem.toLong())
        while (fila.isNotEmpty()) {
            val atual = (fila.poll()!! and 0xFFFFFFFFL).toInt()
            if (fechado[atual]) continue
            if (atual == destino) break
            fechado[atual] = true
            val c = atual % gw
            val r = atual / gw
            for (k in 0 until 8) {
                val nc = c + dc[k]
                val nr = r + dr[k]
                if (nc < 0 || nr < 0 || nc >= gw || nr >= gh) continue
                val viz = nr * gw + nc
                if (fechado[viz]) continue
                val passo = if (k < 4) 10 else 14
                val novo = g[atual] + passo * custo[viz]
                if (novo < g[viz]) {
                    g[viz] = novo
                    pai[viz] = atual
                    fila.add(((novo + h(viz)).toLong() shl 32) or viz.toLong())
                }
            }
        }
        if (g[destino] == Int.MAX_VALUE) return null

        // Reconstrói e simplifica (só mantém as curvas)
        val celulas = ArrayList<Int>()
        var i = destino
        while (i != -1) { celulas.add(i); i = pai[i] }
        celulas.reverse()
        val pontos = ArrayList<Pair<Float, Float>>()
        pontos.add(inicio.x to inicio.y)
        var dirAnt = -1
        for (k in 1 until celulas.size - 1) {
            val a = celulas[k]
            val b = celulas[k + 1]
            val dir = (b % gw - a % gw + 1) * 3 + (b / gw - a / gw + 1)
            if (dir != dirAnt) {
                pontos.add(((a % gw) + 0.5f) * CEL to ((a / gw) + 0.5f) * CEL)
                dirAnt = dir
            }
        }
        pontos.add(alvoX to alvoY)
        return ResultadoRota(pontos, aviso)
    }
}
