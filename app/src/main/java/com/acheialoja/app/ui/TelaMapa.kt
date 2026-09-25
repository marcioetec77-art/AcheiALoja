package com.acheialoja.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.acheialoja.app.data.LeitorMapa
import com.acheialoja.app.data.Loja
import com.acheialoja.app.data.Mapa
import com.acheialoja.app.data.Ponto
import com.acheialoja.app.data.Repositorio
import com.acheialoja.app.data.nomeCategoria
import com.acheialoja.app.data.nomePonto
import com.acheialoja.app.data.normalizar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaMapa(
    uid: String,
    shoppingId: String,
    exemplo: Boolean,
    aoVoltar: () -> Unit,
) {
    val contexto = LocalContext.current
    var mapa by remember { mutableStateOf<Mapa?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var tentativa by remember { mutableIntStateOf(0) }

    LaunchedEffect(shoppingId, tentativa) {
        erro = null
        try {
            mapa = if (exemplo) {
                withContext(Dispatchers.IO) {
                    val texto = contexto.assets.open("exemplo.json").bufferedReader().use { it.readText() }
                    LeitorMapa.ler(texto, "exemplo")
                }
            } else {
                Repositorio.carregarMapa(shoppingId)
            }
        } catch (e: Exception) {
            erro = traduzirErro(e)
        }
    }

    val favoritos by remember(uid) { Repositorio.favoritos(uid) }.collectAsState(initial = emptySet())
    val favorito = shoppingId in favoritos
    val snackbar = remember { SnackbarHostState() }
    var mostrarReporte by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            mapa?.nome ?: "Carregando…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        mapa?.cidade?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (!exemplo) {
                        IconButton(onClick = { Repositorio.alternarFavorito(uid, shoppingId, !favorito) }) {
                            Icon(
                                if (favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favoritar",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = { mostrarReporte = true }, enabled = mapa != null) {
                        Icon(Icons.Filled.Warning, contentDescription = "Informar erro no mapa")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            val m = mapa
            when {
                erro != null -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Mensagem("Não foi possível abrir o mapa.\n$erro")
                    TextButton(onClick = { tentativa++ }) { Text("Tentar de novo") }
                }
                m == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                m.pisos.isEmpty() -> Mensagem("Este shopping ainda não tem pisos desenhados.")
                else -> ConteudoMapa(m)
            }
        }
    }

    val m = mapa
    if (mostrarReporte && m != null) {
        DialogoReporte(
            aoFechar = { mostrarReporte = false },
            aoEnviar = { texto ->
                mostrarReporte = false
                escopo.launch {
                    try {
                        Repositorio.enviarSugestao(m.id, texto)
                        snackbar.showSnackbar("Obrigado! Vamos conferir e corrigir o mapa.")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Não foi possível enviar: ${traduzirErro(e)}")
                    }
                }
            },
        )
    }
}

@Composable
private fun ConteudoMapa(mapa: Mapa) {
    val estado = remember(mapa.id) { EstadoMapa() }
    var pisoIdx by remember(mapa.id) { mutableIntStateOf(0) }
    var selecionada by remember(mapa.id) { mutableStateOf<Loja?>(null) }
    var pontoDestacado by remember(mapa.id) { mutableStateOf<Ponto?>(null) }
    var busca by remember(mapa.id) { mutableStateOf("") }
    val piso = mapa.pisos[pisoIdx.coerceIn(0, mapa.pisos.lastIndex)]

    // Enquadra o shopping inteiro assim que a área do mapa tiver tamanho
    LaunchedEffect(estado.tamanho) {
        if (!estado.enquadrado && estado.tamanho != IntSize.Zero) estado.enquadrar(mapa)
    }

    fun irParaLoja(pisoDaLoja: Int, loja: Loja) {
        pisoIdx = pisoDaLoja
        selecionada = loja
        pontoDestacado = null
        busca = ""
        estado.focar(mapa, loja.x + loja.w / 2f, loja.y + loja.h / 2f)
    }

    fun irParaPonto(tipo: String) {
        for ((i, p) in mapa.pisos.withIndex()) {
            val ponto = p.pontos.firstOrNull { it.tipo == tipo } ?: continue
            pisoIdx = i
            selecionada = null
            pontoDestacado = ponto
            estado.focar(mapa, ponto.x, ponto.y, 2f)
            return
        }
    }

    val temEntradaMotoboy = mapa.pisos.any { p -> p.pontos.any { it.tipo == "entrada_motoboy" } }
    val temRetirada = mapa.pisos.any { p -> p.pontos.any { it.tipo == "retirada" } }

    Column(Modifier.fillMaxSize()) {
        // Busca de lojas
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            placeholder = { Text("Qual loja você procura?") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (busca.isNotEmpty()) IconButton(onClick = { busca = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Limpar busca")
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // Pisos e atalhos
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mapa.pisos.size > 1) {
                mapa.pisos.forEachIndexed { i, p ->
                    FilterChip(
                        selected = i == pisoIdx,
                        onClick = { pisoIdx = i; pontoDestacado = null },
                        label = { Text(p.nome) },
                    )
                }
            }
            if (temEntradaMotoboy) {
                AssistChip(
                    onClick = { irParaPonto("entrada_motoboy") },
                    label = { Text("Entrada motoboy") },
                    leadingIcon = { Bolinha("entrada_motoboy") },
                )
            }
            if (temRetirada) {
                AssistChip(
                    onClick = { irParaPonto("retirada") },
                    label = { Text("Retirada") },
                    leadingIcon = { Bolinha("retirada") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            MapaInterativo(
                mapa = mapa,
                piso = piso,
                estado = estado,
                selecionada = selecionada,
                pontoDestacado = pontoDestacado,
                aoTocarLoja = { loja ->
                    selecionada = loja
                    pontoDestacado = null
                },
            )

            // Botões de zoom
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = { estado.zoomNoCentro(1.5f) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Aproximar")
                }
                SmallFloatingActionButton(onClick = { estado.zoomNoCentro(1f / 1.5f) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                SmallFloatingActionButton(onClick = { estado.enquadrar(mapa) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Ver shopping inteiro")
                }
            }

            // Resultados da busca por cima do mapa
            val termo = normalizar(busca)
            if (termo.isNotEmpty()) {
                val resultados = mapa.pisos.withIndex().flatMap { (i, p) ->
                    p.lojas
                        .filter { normalizar(it.nome + " " + it.numero + " " + nomeCategoria(it.categoria)).contains(termo) }
                        .map { i to it }
                }.sortedByDescending { it.second.eComida }

                ElevatedCard(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    if (resultados.isEmpty()) {
                        Text("Nenhuma loja encontrada.", Modifier.padding(16.dp))
                    } else {
                        LazyColumn {
                            items(resultados, key = { "${it.first}-${it.second.id}" }) { (i, loja) ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { irParaLoja(i, loja) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(loja.nome, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOf(mapa.pisos[i].nome, loja.numero, nomeCategoria(loja.categoria))
                                            .filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // Painel inferior
        val loja = selecionada
        val ponto = pontoDestacado
        when {
            loja != null -> PainelInfo(
                titulo = loja.nome,
                linhas = listOfNotNull(
                    listOf(piso.nome, loja.numero.takeIf { it.isNotBlank() }?.let { "Loja $it" }, nomeCategoria(loja.categoria))
                        .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                    loja.dica.takeIf { it.isNotBlank() }?.let { "Dica: $it" },
                ),
                aoFechar = { selecionada = null },
            )
            ponto != null -> PainelInfo(
                titulo = ponto.rotulo.ifBlank { nomePonto(ponto.tipo) },
                linhas = listOf(piso.nome),
                aoFechar = { pontoDestacado = null },
            )
            mapa.observacoes.isNotBlank() -> PainelInfo(
                titulo = "Aviso do shopping",
                linhas = listOf(mapa.observacoes),
                aoFechar = null,
            )
            else -> Legenda()
        }
    }
}

@Composable
private fun PainelInfo(titulo: String, linhas: List<String>, aoFechar: (() -> Unit)?) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                linhas.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (aoFechar != null) {
                IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar") }
            }
        }
    }
}

@Composable
private fun Legenda() {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemLegenda(cor = Color(0xFFFFB38A), texto = "Comida")
        ItemLegenda(cor = Color(0xFFD5D7DB), texto = "Outras lojas")
        listOf("entrada_motoboy", "retirada", "estacionamento", "escada", "elevador").forEach { tipo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bolinha(tipo)
                Spacer(Modifier.width(4.dp))
                Text(nomePonto(tipo), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ItemLegenda(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .background(cor, MaterialTheme.shapes.extraSmall)
        )
        Spacer(Modifier.width(4.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Bolinha(tipo: String) {
    Box(
        Modifier
            .size(20.dp)
            .background(corDoPonto(tipo), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letraDoPonto(tipo),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DialogoReporte(
    aoFechar: () -> Unit,
    aoEnviar: (String) -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Algo errado no mapa?") },
        text = {
            Column {
                Text("Conte o que mudou: loja que fechou, mudou de lugar, entrada nova de motoboy…")
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { if (it.length <= 1000) texto = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = texto.isNotBlank(), onClick = { aoEnviar(texto) }) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
