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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.acheialoja.app.data.Repositorio
import com.acheialoja.app.data.ShoppingResumo
import com.acheialoja.app.data.normalizar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaShoppings(
    uid: String,
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

    Scaffold(
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
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                placeholder = { Text("Buscar shopping ou cidade") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            val atual = lista
            when {
                erro != null -> Mensagem("Não foi possível carregar os shoppings.\n$erro")
                atual == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val termo = normalizar(busca)
                    val filtrada = atual
                        .filter { termo.isEmpty() || normalizar(it.nome + " " + it.cidade).contains(termo) }
                        .sortedByDescending { it.id in favoritos }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (filtrada.isEmpty()) {
                            item {
                                Mensagem(
                                    if (atual.isEmpty()) "Nenhum shopping cadastrado ainda."
                                    else "Nenhum shopping encontrado para \"$busca\"."
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
                            )
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { aoAbrir("exemplo", true) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Ver shopping de exemplo") }
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
                if (shopping.cidade.isNotBlank()) {
                    Text(
                        shopping.cidade,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
