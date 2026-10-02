package com.acheialoja.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.acheialoja.app.data.CATEGORIAS
import com.acheialoja.app.data.Item
import com.acheialoja.app.data.Shopping
import com.acheialoja.app.data.TIPOS_PONTO
import com.acheialoja.app.data.nomePonto

/** Posição atual da câmera do mapa (para gravar como posição inicial do shopping). */
data class Enquadramento(val lat: Double, val lng: Double, val zoom: Float)

/** Menu que aparece quando o administrador toca num lugar vazio do mapa. */
@Composable
fun DialogoAdicionar(piso: String, aoEscolher: (String) -> Unit, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Adicionar aqui") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (piso.isBlank()) "Sem piso definido (aparece em todos os pisos)."
                    else "Será marcado no piso $piso (o piso escolhido no Google Maps).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OpcaoAdicionar(GRUPOS_COMIDA[0].cor, false, "", "Loja de comida") { aoEscolher("loja_comida") }
                OpcaoAdicionar(COR_OUTRAS_LOJAS, false, "", "Outra loja (referência)") { aoEscolher("loja_outra") }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TIPOS_PONTO.forEach { tipo ->
                    OpcaoAdicionar(corDoPonto(tipo), true, letraDoPonto(tipo), nomePonto(tipo)) { aoEscolher(tipo) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
private fun OpcaoAdicionar(cor: Color, redondo: Boolean, letra: String, texto: String, aoClicar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(cor, if (redondo) CircleShape else RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (letra.isNotEmpty()) {
                Text(letra, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Cria ou edita uma loja ou ponto. */
@Composable
fun DialogoItem(
    item: Item,
    nova: Boolean,
    aoSalvar: (Item) -> Unit,
    aoExcluir: () -> Unit,
    aoMover: () -> Unit,
    aoFechar: () -> Unit,
) {
    var nome by remember { mutableStateOf(item.nome) }
    var categoria by remember { mutableStateOf(item.categoria) }
    var tipo by remember { mutableStateOf(item.tipo) }
    var numero by remember { mutableStateOf(item.numero) }
    var dica by remember { mutableStateOf(item.dica) }
    var piso by remember { mutableStateOf(item.piso) }
    val eLoja = item.eLoja

    AlertDialog(
        onDismissRequest = aoFechar,
        title = {
            Text(
                when {
                    eLoja && nova -> "Nova loja"
                    eLoja -> "Editar loja"
                    nova -> "Novo ponto"
                    else -> "Editar ponto"
                }
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (eLoja) {
                    OutlinedTextField(nome, { nome = it }, label = { Text("Nome da loja") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SeletorOpcao("Tipo", CATEGORIAS, categoria) { categoria = it }
                    OutlinedTextField(numero, { numero = it }, label = { Text("Número / LUC (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    SeletorOpcao("Tipo", TIPOS_PONTO.map { it to nomePonto(it) }, tipo) { tipo = it }
                    OutlinedTextField(
                        nome, { nome = it },
                        label = { Text("Nome (opcional)") },
                        placeholder = { Text(nomePonto(tipo)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(dica, { dica = it }, label = { Text("Dica para o motoboy (opcional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    piso, { piso = it.take(6) },
                    label = { Text("Piso (igual ao seletor do Google)") },
                    supportingText = { Text("Deixe vazio para aparecer em todos os pisos.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
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
                enabled = !eLoja || nome.isNotBlank(),
                onClick = {
                    aoSalvar(
                        item.copy(
                            nome = nome.trim(),
                            categoria = if (eLoja) categoria else "",
                            tipo = if (eLoja) "loja" else tipo,
                            numero = numero.trim(),
                            dica = dica.trim(),
                            piso = piso.trim(),
                        )
                    )
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoDadosShopping(
    shopping: Shopping,
    enquadramento: Enquadramento?,
    aoSalvar: (Shopping) -> Unit,
    aoExcluirShopping: () -> Unit,
    aoFechar: () -> Unit,
) {
    var nome by remember { mutableStateOf(shopping.nome) }
    var cidade by remember { mutableStateOf(shopping.cidade) }
    var observacoes by remember { mutableStateOf(shopping.observacoes) }
    var usarEnquadramento by remember { mutableStateOf(false) }
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
                OutlinedTextField(cidade, { cidade = it }, label = { Text("Cidade / bairro") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Aviso para motoboys") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (enquadramento != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { usarEnquadramento = !usarEnquadramento },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = usarEnquadramento, onCheckedChange = { usarEnquadramento = it })
                        Text("Abrir o shopping sempre nesta posição e zoom do mapa que estou vendo agora")
                    }
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
                val e = enquadramento.takeIf { usarEnquadramento }
                aoSalvar(
                    shopping.copy(
                        nome = nome.trim(),
                        cidade = cidade.trim(),
                        observacoes = observacoes.trim(),
                        lat = e?.lat ?: shopping.lat,
                        lng = e?.lng ?: shopping.lng,
                        zoom = e?.zoom ?: shopping.zoom,
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoNovoShopping(aoCriar: (nome: String, cidade: String, busca: String) -> Unit, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var cidade by remember { mutableStateOf("São Paulo - SP") }
    var busca by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Novo shopping") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "O shopping abre no Google Maps. Depois é só tocar no mapa para marcar a entrada de motoboys e as lojas.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome do shopping") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cidade, { cidade = it }, label = { Text("Cidade / bairro") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    busca, { busca = it },
                    label = { Text("Endereço (opcional)") },
                    supportingText = { Text("Se deixar vazio, procuro pelo nome + cidade.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = {
                aoCriar(nome.trim(), cidade.trim(), busca.trim().ifBlank { "${nome.trim()}, ${cidade.trim()}" })
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
