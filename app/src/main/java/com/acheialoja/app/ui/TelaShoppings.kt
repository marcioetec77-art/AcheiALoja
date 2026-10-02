package com.acheialoja.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.acheialoja.app.data.Repositorio
import com.acheialoja.app.data.ShoppingResumo
import com.acheialoja.app.data.normalizar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaShoppings(
    uid: String,
    admin: Boolean,
    aoAbrir: (id: String, exemplo: Boolean) -> Unit,
    aoAbrirConta: () -> Unit,
) {
    var lista by remember { mutableStateOf<List<ShoppingResumo>?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var busca by rememberSaveable { mutableStateOf("") }
    val favoritos by remember(uid) { Repositorio.favoritos(uid) }.collectAsState(initial = emptySet())

    LaunchedEffect(Unit) {
        try {
            Repositorio.shoppings().collect { lista = it; erro = null }
        } catch (e: Exception) {
            erro = traduzirErro(e)
        }
    }

    var novoShopping by remember { mutableStateOf(false) }
    var editandoNome by remember { mutableStateOf<ShoppingResumo?>(null) }
    var mostrarApoio by remember { mutableStateOf(false) }
    if (mostrarApoio) DialogoApoio(aoFechar = { mostrarApoio = false })
    var criando by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val contexto = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val minhasPendentes = lista.orEmpty().count { !it.ativo && it.criadoPor == uid }

    editandoNome?.let { alvo ->
        DialogoRenomearShopping(
            shopping = alvo,
            aoSalvar = { nome, cidade ->
                editandoNome = null
                escopo.launch {
                    try {
                        Repositorio.renomearShopping(alvo.id, nome, cidade)
                        snackbar.showSnackbar("Nome atualizado.")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Não salvou: ${traduzirErro(e)}")
                    }
                }
            },
            aoFechar = { editandoNome = null },
        )
    }

    if (novoShopping) {
        DialogoNovoShopping(
            sugestao = !admin,
            aoCriar = { nome, cidade, endereco ->
                novoShopping = false
                criando = true
                escopo.launch {
                    try {
                        // Procura o endereço; se não achar, abre no centro de São Paulo e o admin arrasta o mapa
                        val (lat, lng) = Repositorio.buscarPosicao(contexto, endereco) ?: (-23.5505 to -46.6333)
                        val id = Repositorio.criarShopping(nome, cidade, lat, lng, sugestao = !admin)
                        aoAbrir(id, false)
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Não foi possível criar: ${traduzirErro(e)}")
                    } finally {
                        criando = false
                    }
                }
            },
            aoFechar = { novoShopping = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when {
                        criando -> Unit
                        !admin && minhasPendentes >= 3 -> escopo.launch {
                            snackbar.showSnackbar("Você já tem 3 sugestões aguardando aprovação. Aguarde a análise.")
                        }
                        else -> novoShopping = true
                    }
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (criando) "Criando…" else if (admin) "Novo shopping" else "Sugerir shopping") },
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Achei a Loja", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = aoAbrirConta) {
                        Icon(Icons.Filled.Person, contentDescription = "Minha conta")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            // Fica fixo no topo para não sumir quando a lista crescer
            OutlinedButton(
                onClick = { mostrarApoio = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Apoie o desenvolvedor")
            }
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                placeholder = { Text("Buscar shopping ou cidade") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            )

            val atual = lista
            when {
                erro != null -> Mensagem("Não foi possível carregar os shoppings.\n$erro")
                atual == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val termo = normalizar(busca)
                    // Sugeridos só aparecem para o administrador e para quem sugeriu
                    val visiveis = atual.filter { it.ativo || admin || it.criadoPor == uid }
                    val pendentes = if (admin) atual.count { !it.ativo } else 0
                    val filtrada = visiveis
                        .filter { termo.isEmpty() || normalizar(it.nome + " " + it.cidade).contains(termo) }
                        .sortedWith(
                            compareByDescending<ShoppingResumo> { admin && !it.ativo }
                                .thenByDescending { it.id in favoritos }
                        )

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (pendentes > 0) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (pendentes == 1) "🔔 1 shopping sugerido por motoboy aguardando você"
                                        else "🔔 $pendentes shoppings sugeridos por motoboys aguardando você",
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(14.dp),
                                    )
                                }
                            }
                        }
                        if (filtrada.isEmpty()) {
                            item {
                                Mensagem(
                                    if (visiveis.isEmpty()) "Nenhum shopping cadastrado ainda."
                                    else "Nenhum shopping encontrado para \"$busca\".\nNão achou? Toque em \"Sugerir shopping\"."
                                )
                            }
                        }
                        items(filtrada, key = { it.id }) { s ->
                            CartaoShopping(
                                shopping = s,
                                favorito = s.id in favoritos,
                                aoClicar = { aoAbrir(s.id, false) },
                                aoFavoritar = {
                                    Repositorio.alternarFavorito(uid, s.id, s.id !in favoritos)
                                },
                                aoEditar = if (admin) ({ editandoNome = s }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoShopping(
    shopping: ShoppingResumo,
    favorito: Boolean,
    aoClicar: () -> Unit,
    aoFavoritar: () -> Unit,
    aoEditar: (() -> Unit)? = null,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar)
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(shopping.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!shopping.ativo) {
                    Text(
                        "Aguardando aprovação",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (shopping.cidade.isNotBlank()) {
                    Text(
                        shopping.cidade,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (aoEditar != null) {
                IconButton(onClick = aoEditar) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar nome", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = aoFavoritar) {
                Icon(
                    if (favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (favorito) "Remover dos favoritos" else "Favoritar",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun Mensagem(texto: String) {
    Text(
        texto,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}
