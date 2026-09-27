package com.acheialoja.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.acheialoja.app.data.CATEGORIAS
import com.acheialoja.app.data.EscritorMapa
import com.acheialoja.app.data.Forma
import com.acheialoja.app.data.Loja
import com.acheialoja.app.data.Mapa
import com.acheialoja.app.data.Piso
import com.acheialoja.app.data.Ponto
import com.acheialoja.app.data.TIPOS_FORMA
import com.acheialoja.app.data.TIPOS_PONTO
import com.acheialoja.app.data.nomePonto
import kotlin.math.hypot

/** O que o administrador tocou no modo edição. */
sealed interface AlvoEdicao {
    data class LojaAlvo(val loja: Loja, val nova: Boolean) : AlvoEdicao
    data class PontoAlvo(val indice: Int, val ponto: Ponto) : AlvoEdicao
    data class FormaAlvo(val indice: Int, val forma: Forma) : AlvoEdicao
    data class Adicionar(val x: Float, val y: Float) : AlvoEdicao
}

/** Troca um piso do mapa por uma versão alterada. */
fun Mapa.comPiso(indice: Int, alterar: (Piso) -> Piso): Mapa =
    copy(pisos = pisos.mapIndexed { i, p -> if (i == indice) alterar(p) else p })

/** Descobre o que está no ponto tocado (pontos > lojas > áreas > contorno). */
fun acertarEdicao(piso: Piso, x: Float, y: Float, raioPonto: Float): AlvoEdicao {
    piso.pontos.withIndex().lastOrNull { (_, p) -> hypot(p.x - x, p.y - y) <= raioPonto }
        ?.let { return AlvoEdicao.PontoAlvo(it.index, it.value) }
    piso.lojas.lastOrNull { it.contem(x, y) }
        ?.let { return AlvoEdicao.LojaAlvo(it, nova = false) }
    val dentro = { f: Forma -> x >= f.x && x <= f.x + f.w && y >= f.y && y <= f.y + f.h }
    piso.formas.withIndex().lastOrNull { (_, f) -> f.tipo != "contorno" && f.tipo != "corredor" && dentro(f) }
        ?.let { return AlvoEdicao.FormaAlvo(it.index, it.value) }
    return AlvoEdicao.Adicionar(x, y)
}

fun novoIdLoja(): String = "l" + System.currentTimeMillis().toString(36)

// ------------------------------------------------------------------
// Diálogos
// ------------------------------------------------------------------

private val OPCOES_ADICIONAR = listOf(
    "loja_comida" to "Loja de comida",
    "loja_outra" to "Outra loja (referência)",
) + TIPOS_PONTO.map { it to nomePonto(it) } + listOf(
    "area_praca" to "Área: praça de alimentação",
    "area_corredor" to "Área: corredor",
    "area_bloqueado" to "Área: fechada / estacionamento",
)

@Composable
fun DialogoAdicionar(aoEscolher: (String) -> Unit, aoEditarAreas: () -> Unit, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Adicionar aqui") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OPCOES_ADICIONAR.forEach { (codigo, nome) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { aoEscolher(codigo) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            codigo.startsWith("loja") -> Box(
                                Modifier.size(18.dp).fundo(if (codigo == "loja_comida") GRUPOS_COMIDA[0].cor else Color(0xFFB5B9C0))
                            )
                            codigo.startsWith("area") -> Box(Modifier.size(18.dp).fundo(Color(0xFFFFE9A8)))
                            else -> Box(Modifier.size(18.dp).fundo(corDoPonto(codigo), redondo = true))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(nome)
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = aoEditarAreas) { Text("Editar corredores e contorno deste piso") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoLoja(
    loja: Loja,
    nova: Boolean,
    aoSalvar: (Loja) -> Unit,
    aoExcluir: () -> Unit,
    aoMover: () -> Unit,
    aoFechar: () -> Unit,
) {
    var nome by remember { mutableStateOf(loja.nome) }
    var categoria by remember { mutableStateOf(loja.categoria) }
    var numero by remember { mutableStateOf(loja.numero) }
    var dica by remember { mutableStateOf(loja.dica) }
    var largura by remember { mutableStateOf(loja.w.toInt().toString()) }
    var altura by remember { mutableStateOf(loja.h.toInt().toString()) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (nova) "Nova loja" else "Editar loja") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome da loja") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SeletorOpcao("Tipo", CATEGORIAS, categoria) { categoria = it }
                OutlinedTextField(numero, { numero = it }, label = { Text("Número (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dica, { dica = it }, label = { Text("Dica para o motoboy (opcional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Tamanho(largura, altura, { largura = it }, { altura = it })
                if (!nova) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = aoMover) { Text("Mover") }
                        OutlinedButton(onClick = aoExcluir) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    val w = numeroOu(largura, loja.w)
                    val h = numeroOu(altura, loja.h)
                    // mantém o centro no mesmo lugar ao mudar o tamanho
                    val cx = loja.x + loja.w / 2f
                    val cy = loja.y + loja.h / 2f
                    aoSalvar(
                        loja.copy(
                            nome = nome.trim(), categoria = categoria, numero = numero.trim(), dica = dica.trim(),
                            x = cx - w / 2f, y = cy - h / 2f, w = w, h = h,
                        )
                    )
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoPonto(
    ponto: Ponto,
    aoSalvar: (Ponto) -> Unit,
    aoExcluir: () -> Unit,
    aoMover: () -> Unit,
    aoFechar: () -> Unit,
) {
    var tipo by remember { mutableStateOf(ponto.tipo) }
    var rotulo by remember { mutableStateOf(ponto.rotulo) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Editar ponto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeletorOpcao("Tipo", TIPOS_PONTO.map { it to nomePonto(it) }, tipo) { tipo = it }
                OutlinedTextField(rotulo, { rotulo = it }, label = { Text("Descrição (ex.: Portão 3, lado da Rua B)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = aoMover) { Text("Mover") }
                    OutlinedButton(onClick = aoExcluir) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { aoSalvar(ponto.copy(tipo = tipo, rotulo = rotulo.trim())) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoForma(
    forma: Forma,
    aoSalvar: (Forma) -> Unit,
    aoExcluir: () -> Unit,
    aoMover: () -> Unit,
    aoFechar: () -> Unit,
) {
    var tipo by remember { mutableStateOf(forma.tipo) }
    var rotulo by remember { mutableStateOf(forma.rotulo) }
    var largura by remember { mutableStateOf(forma.w.toInt().toString()) }
    var altura by remember { mutableStateOf(forma.h.toInt().toString()) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Editar área") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeletorOpcao("Tipo", TIPOS_FORMA, tipo) { tipo = it }
                OutlinedTextField(rotulo, { rotulo = it }, label = { Text("Nome (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Tamanho(largura, altura, { largura = it }, { altura = it })
                Text(
                    "O desenho inteiro mede 1000 de largura por 700 de altura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = aoMover) { Text("Mover") }
                    OutlinedButton(onClick = aoExcluir) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = numeroOu(largura, forma.w)
                val h = numeroOu(altura, forma.h)
                aoSalvar(forma.copy(tipo = tipo, rotulo = rotulo.trim(), w = w, h = h))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Lista todas as áreas do piso (inclusive corredores e contorno) para editar. */
@Composable
fun DialogoListaAreas(piso: Piso, aoEscolher: (Int) -> Unit, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Áreas de ${piso.nome}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (piso.formas.isEmpty()) Text("Nenhuma área neste piso.")
                piso.formas.forEachIndexed { i, f ->
                    val nome = TIPOS_FORMA.firstOrNull { it.first == f.tipo }?.second ?: f.tipo
                    Text(
                        "$nome ${if (f.rotulo.isNotBlank()) "— ${f.rotulo}" else ""}  (${f.w.toInt()} × ${f.h.toInt()})",
                        Modifier
                            .fillMaxWidth()
                            .clickable { aoEscolher(i) }
                            .padding(vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

@Composable
fun DialogoDadosShopping(
    mapa: Mapa,
    aoSalvar: (Mapa) -> Unit,
    aoExcluirShopping: () -> Unit,
    aoFechar: () -> Unit,
) {
    var nome by remember { mutableStateOf(mapa.nome) }
    var cidade by remember { mutableStateOf(mapa.cidade) }
    var endereco by remember { mutableStateOf(mapa.endereco) }
    var observacoes by remember { mutableStateOf(mapa.observacoes) }
    val nomesPisos = remember { mutableStateListOf<String>().apply { addAll(mapa.pisos.map { it.nome }) } }
    var confirmarExclusao by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Dados do shopping") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cidade, { cidade = it }, label = { Text("Cidade") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endereco, { endereco = it }, label = { Text("Endereço") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Aviso para motoboys") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                Text("Pisos", fontWeight = FontWeight.Bold)
                nomesPisos.forEachIndexed { i, n ->
                    OutlinedTextField(n, { nomesPisos[i] = it }, label = { Text("Piso ${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nomesPisos.add("Piso ${nomesPisos.size + 1}") }) { Text("+ Piso") }
                    if (nomesPisos.size > 1) {
                        OutlinedButton(onClick = { nomesPisos.removeAt(nomesPisos.lastIndex) }) { Text("− Último piso") }
                    }
                }
                if (nomesPisos.size < mapa.pisos.size) {
                    Text(
                        "Atenção: o último piso e tudo que está nele serão apagados ao salvar.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(onClick = { if (confirmarExclusao) aoExcluirShopping() else confirmarExclusao = true }) {
                    Text(
                        if (confirmarExclusao) "Toque de novo para EXCLUIR o shopping" else "Excluir este shopping",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = {
                val pisos = nomesPisos.mapIndexed { i, n ->
                    val existente = mapa.pisos.getOrNull(i)
                    existente?.copy(nome = n.trim().ifBlank { "Piso ${i + 1}" })
                        ?: EscritorMapa.pisoBase("P${i + 1}_${System.currentTimeMillis() % 10000}", n.trim().ifBlank { "Piso ${i + 1}" })
                }
                aoSalvar(
                    mapa.copy(
                        nome = nome.trim(), cidade = cidade.trim(), endereco = endereco.trim(),
                        observacoes = observacoes.trim(), pisos = pisos,
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoNovoShopping(aoCriar: (nome: String, cidade: String, pisos: Int) -> Unit, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var cidade by remember { mutableStateOf("São Paulo - SP") }
    var pisos by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Novo shopping") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "O mapa começa retangular, com corredores em cruz. Depois é só tocar no mapa para adicionar as lojas.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome do shopping") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cidade, { cidade = it }, label = { Text("Cidade") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pisos, { pisos = it.filter(Char::isDigit).take(1) },
                    label = { Text("Quantos pisos? (1 a 6)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = {
                aoCriar(nome, cidade, (pisos.toIntOrNull() ?: 1).coerceIn(1, 6))
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

// ------------------------------------------------------------------
// Peças pequenas
// ------------------------------------------------------------------

@Composable
private fun SeletorOpcao(rotulo: String, opcoes: List<Pair<String, String>>, valor: String, aoMudar: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    val atual = opcoes.firstOrNull { it.first == valor }?.second ?: valor
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { aberto = true }, modifier = Modifier.fillMaxWidth()) {
                Text(atual, Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                opcoes.forEach { (codigo, nome) ->
                    DropdownMenuItem(text = { Text(nome) }, onClick = { aoMudar(codigo); aberto = false })
                }
            }
        }
    }
}

@Composable
private fun Tamanho(largura: String, altura: String, aoLargura: (String) -> Unit, aoAltura: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            largura, { aoLargura(it.filter(Char::isDigit).take(4)) },
            label = { Text("Largura") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            altura, { aoAltura(it.filter(Char::isDigit).take(4)) },
            label = { Text("Altura") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun numeroOu(texto: String, padrao: Float): Float =
    (texto.toFloatOrNull() ?: padrao).coerceIn(10f, 2000f)

private fun Modifier.fundo(cor: Color, redondo: Boolean = false): Modifier =
    this.background(cor, if (redondo) CircleShape else RoundedCornerShape(4.dp))
