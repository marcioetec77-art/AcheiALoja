package com.acheialoja.app.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.acheialoja.app.data.Item
import com.acheialoja.app.data.LugaresGoogle
import com.acheialoja.app.data.Repositorio
import com.acheialoja.app.data.Rota
import com.acheialoja.app.data.Shopping
import com.acheialoja.app.data.nomeCategoria
import com.acheialoja.app.data.nomePonto
import com.acheialoja.app.data.normalizar
import com.acheialoja.app.data.novoId
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.IndoorBuilding
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.IndoorStateChangeListener
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import com.google.android.gms.maps.GoogleMap as MapaGoogle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaMapa(
    uid: String,
    shoppingId: String,
    @Suppress("UNUSED_PARAMETER") exemplo: Boolean,
    admin: Boolean,
    aoVoltar: () -> Unit,
) {
    var shopping by remember { mutableStateOf<Shopping?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var tentativa by remember { mutableIntStateOf(0) }
    val camera = rememberCameraPositionState()

    LaunchedEffect(shoppingId, tentativa) {
        erro = null
        try {
            val s = Repositorio.carregarShopping(shoppingId)
            camera.position = CameraPosition.fromLatLngZoom(LatLng(s.lat, s.lng), s.zoom)
            shopping = s
        } catch (e: Exception) {
            erro = traduzirErro(e)
        }
    }

    val favoritos by remember(uid) { Repositorio.favoritos(uid) }.collectAsState(initial = emptySet())
    val favorito = shoppingId in favoritos
    val snackbar = remember { SnackbarHostState() }
    var mostrarReporte by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    var editando by remember { mutableStateOf(false) }
    var mostrarDados by remember { mutableStateOf(false) }

    /** Aplica uma alteração do administrador e salva no Firebase. */
    fun alterar(novo: Shopping) {
        shopping = novo
        escopo.launch {
            try {
                Repositorio.salvarShopping(novo)
            } catch (e: Exception) {
                snackbar.showSnackbar("Não salvou: ${traduzirErro(e)}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            shopping?.nome ?: "Carregando…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        shopping?.cidade?.takeIf { it.isNotBlank() }?.let {
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
                    if (admin && shopping != null) {
                        IconButton(onClick = { editando = !editando }) {
                            Icon(
                                if (editando) Icons.Filled.Check else Icons.Filled.Edit,
                                contentDescription = if (editando) "Concluir edição" else "Editar marcações",
                                tint = if (editando) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = { Repositorio.alternarFavorito(uid, shoppingId, !favorito) }) {
                        Icon(
                            if (favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favoritar",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { mostrarReporte = true }, enabled = shopping != null) {
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
            val s = shopping
            when {
                erro != null -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Mensagem("Não foi possível abrir o shopping.\n$erro")
                    TextButton(onClick = { tentativa++ }) { Text("Tentar de novo") }
                }
                s == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> ConteudoMapa(
                    shopping = s,
                    camera = camera,
                    editando = editando,
                    aoAlterar = { alterar(it) },
                    aoAbrirDados = { mostrarDados = true },
                    aoConcluirEdicao = { editando = false },
                    aoAviso = { texto -> escopo.launch { snackbar.showSnackbar(texto) } },
                )
            }
        }
    }

    val s = shopping
    if (mostrarDados && s != null) {
        val pos = camera.position
        DialogoDadosShopping(
            shopping = s,
            enquadramento = Enquadramento(pos.target.latitude, pos.target.longitude, pos.zoom),
            aoSalvar = { alterar(it); mostrarDados = false },
            aoExcluirShopping = {
                mostrarDados = false
                escopo.launch {
                    try {
                        Repositorio.excluirShopping(s.id)
                        aoVoltar()
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Não excluiu: ${traduzirErro(e)}")
                    }
                }
            },
            aoFechar = { mostrarDados = false },
        )
    }
    if (mostrarReporte && s != null) {
        DialogoReporte(
            aoFechar = { mostrarReporte = false },
            aoEnviar = { texto ->
                mostrarReporte = false
                escopo.launch {
                    try {
                        Repositorio.enviarSugestao(s.id, texto)
                        snackbar.showSnackbar("Obrigado! Vamos conferir e corrigir o mapa.")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Não foi possível enviar: ${traduzirErro(e)}")
                    }
                }
            },
        )
    }
}

/** Nome curto do piso ativo no Google Maps ("1", "2", "T"...). */
private fun nivelDe(b: IndoorBuilding): String? =
    b.levels.getOrNull(b.activeLevelIndex)?.let { it.shortName?.ifBlank { null } ?: it.name }

private fun mesmoPiso(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun ConteudoMapa(
    shopping: Shopping,
    camera: CameraPositionState,
    editando: Boolean,
    aoAlterar: (Shopping) -> Unit,
    aoAbrirDados: () -> Unit,
    aoConcluirEdicao: () -> Unit,
    aoAviso: (String) -> Unit,
) {
    val contexto = LocalContext.current
    val shoppingAtual by rememberUpdatedState(shopping)
    var mostrarImportar by remember { mutableStateOf(false) }
    var importando by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()
    val densidade = LocalDensity.current.density

    var selecionadoId by remember(shopping.id) { mutableStateOf<String?>(null) }
    var busca by remember(shopping.id) { mutableStateOf("") }
    var mostrarLista by remember(shopping.id) { mutableStateOf(false) }
    var avisoFechado by remember(shopping.id) { mutableStateOf(false) }
    var mapaGoogle by remember { mutableStateOf<MapaGoogle?>(null) }
    var nivelAtual by remember { mutableStateOf<String?>(null) }

    // ----- Modo edição (administrador) -----
    var alvo by remember(shopping.id) { mutableStateOf<Pair<Item, Boolean>?>(null) } // item, é novo?
    var adicionarEm by remember(shopping.id) { mutableStateOf<Pair<LatLng, String>?>(null) } // posição, nome sugerido
    var movendo by remember(shopping.id) { mutableStateOf<Item?>(null) }

    LaunchedEffect(editando) {
        if (editando) selecionadoId = null else { movendo = null; adicionarEm = null }
    }

    val selecionado = shopping.itens.firstOrNull { it.id == selecionadoId }
    val rota = remember(selecionado, shopping, editando) {
        selecionado?.takeIf { it.eLoja && !editando }?.let { Rota.calcular(shopping, it) }
    }

    val ouvinteIndoor = remember {
        object : IndoorStateChangeListener {
            override fun onIndoorBuildingFocused() {
                nivelAtual = mapaGoogle?.focusedBuilding?.let { nivelDe(it) }
            }

            override fun onIndoorLevelActivated(building: IndoorBuilding) {
                nivelAtual = nivelDe(building)
            }
        }
    }

    /** Troca o seletor de piso do Google para o piso do item (se o prédio estiver em foco). */
    fun ativarPiso(piso: String) {
        if (piso.isBlank()) return
        try {
            val b = mapaGoogle?.focusedBuilding ?: return
            b.levels.firstOrNull { mesmoPiso(it.shortName.orEmpty(), piso) || mesmoPiso(it.name.orEmpty(), piso) }
                ?.activate()
        } catch (_: Exception) {
        }
    }

    fun focar(item: Item) {
        selecionadoId = item.id
        busca = ""
        mostrarLista = false
        val r = if (item.eLoja) Rota.calcular(shopping, item) else null
        escopo.launch {
            try {
                val alvoPos = LatLng(item.lat, item.lng)
                if (r != null && r.metros > 5) {
                    val limites = LatLngBounds.builder()
                        .include(LatLng(r.origem.lat, r.origem.lng))
                        .include(alvoPos)
                        .build()
                    camera.animate(CameraUpdateFactory.newLatLngBounds(limites, (90 * densidade).toInt()), 700)
                    if (camera.position.zoom > 20f) camera.animate(CameraUpdateFactory.zoomTo(20f), 300)
                } else {
                    camera.animate(CameraUpdateFactory.newLatLngZoom(alvoPos, maxOf(camera.position.zoom, 19f)), 700)
                }
            } catch (_: Exception) {
            }
            ativarPiso(item.piso)
        }
    }

    fun abrirNavegacao(lat: Double, lng: Double) {
        val url = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=two-wheeler"
        try {
            contexto.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
        }
    }

    fun tocarNoMapa(pos: LatLng, nomeSugerido: String) {
        if (!editando) {
            selecionadoId = null
            return
        }
        val m = movendo
        if (m != null) {
            movendo = null
            aoAlterar(shopping.copy(itens = shopping.itens.map {
                if (it.id == m.id) it.copy(lat = pos.latitude, lng = pos.longitude) else it
            }))
        } else {
            adicionarEm = pos to nomeSugerido
        }
    }

    fun adicionar(codigo: String, pos: LatLng, nomeSugerido: String) {
        adicionarEm = null
        val base = Item(
            id = novoId(), tipo = "loja", nome = nomeSugerido, categoria = "lanches", numero = "", dica = "",
            lat = pos.latitude, lng = pos.longitude, piso = nivelAtual.orEmpty(),
        )
        when (codigo) {
            "loja_comida" -> alvo = base to true
            "loja_outra" -> alvo = base.copy(categoria = "outros") to true
            else -> aoAlterar(shopping.copy(itens = shopping.itens + base.copy(tipo = codigo, categoria = "", nome = "")))
        }
    }

    /** Busca as lojas de comida do Google na área que está aparecendo na tela. */
    fun importar() {
        val limites = camera.projection?.visibleRegion?.latLngBounds
        if (limites == null) {
            aoAviso("Espere o mapa carregar e tente de novo.")
            return
        }
        importando = "Buscando lojas no Google…"
        escopo.launch {
            try {
                val lugares = LugaresGoogle.buscarComida(
                    contexto,
                    limites.southwest.latitude, limites.southwest.longitude,
                    limites.northeast.latitude, limites.northeast.longitude,
                ) { feitos, total -> importando = "Buscando lojas no Google… $feitos de $total" }
                val atual = shoppingAtual
                val novos = lugares
                    .filter { !LugaresGoogle.jaExiste(it, atual.itens) }
                    .map {
                        Item(
                            id = "g_" + it.id, tipo = "loja", nome = it.nome, categoria = it.categoria,
                            numero = "", dica = "", lat = it.lat, lng = it.lng, piso = "",
                        )
                    }
                if (novos.isNotEmpty()) aoAlterar(atual.copy(itens = atual.itens + novos))
                aoAviso(
                    when {
                        lugares.isEmpty() -> "Nenhuma loja de comida encontrada nesta área."
                        novos.isEmpty() -> "As ${lugares.size} lojas encontradas já estavam marcadas."
                        else -> "${novos.size} lojas importadas! Toque em cada uma para conferir o piso."
                    }
                )
            } catch (e: Exception) {
                aoAviso(e.message ?: "Não foi possível buscar no Google.")
            } finally {
                importando = null
            }
        }
    }

    fun primeiroDoTipo(tipo: String): Item? {
        val todos = shopping.itens.filter { it.tipo == tipo }
        return todos.firstOrNull { nivelAtual != null && mesmoPiso(it.piso, nivelAtual!!) } ?: todos.firstOrNull()
    }

    fun noPisoAtual(i: Item): Boolean {
        val n = nivelAtual ?: return true
        return i.piso.isBlank() || mesmoPiso(i.piso, n)
    }

    val entradaMotoboy = primeiroDoTipo("entrada_motoboy")
    val vagas = primeiroDoTipo("estacionamento")
    val retirada = primeiroDoTipo("retirada")

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

        // Atalhos
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = mostrarLista,
                onClick = { mostrarLista = !mostrarLista; busca = "" },
                label = { Text("Lista de lojas") },
            )
            entradaMotoboy?.let { e ->
                AssistChip(onClick = { focar(e) }, label = { Text("Entrada motoboy") }, leadingIcon = { Bolinha(e.tipo) })
            }
            vagas?.let { v ->
                AssistChip(onClick = { focar(v) }, label = { Text("Vagas de motos") }, leadingIcon = { Bolinha(v.tipo) })
            }
            retirada?.let { r ->
                AssistChip(onClick = { focar(r) }, label = { Text("Retirada") }, leadingIcon = { Bolinha(r.tipo) })
            }
        }

        if (editando) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val carregando = importando
                    if (carregando != null) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            carregando != null -> carregando
                            movendo != null -> "Toque no novo lugar de \"${movendo!!.titulo}\""
                            else -> "Toque no mapa para marcar. Piso: ${nivelAtual ?: "todos"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (movendo != null) {
                        TextButton(onClick = { movendo = null }) { Text("Cancelar") }
                    } else if (carregando == null) {
                        TextButton(onClick = { mostrarImportar = true }) { Text("Importar") }
                        TextButton(onClick = aoAbrirDados) { Text("Dados") }
                        TextButton(onClick = aoConcluirEdicao) { Text("Concluir") }
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val propriedades = remember { MapProperties(isIndoorEnabled = true, isBuildingEnabled = true) }
            val controles = remember {
                MapUiSettings(
                    indoorLevelPickerEnabled = true,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    tiltGesturesEnabled = false,
                    zoomControlsEnabled = true,
                )
            }
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = propriedades,
                uiSettings = controles,
                indoorStateChangeListener = ouvinteIndoor,
                onMapClick = { tocarNoMapa(it, "") },
                onPOIClick = { poi -> tocarNoMapa(poi.latLng, if (editando) poi.name.orEmpty() else "") },
            ) {
                MapEffect(Unit) { mapa -> mapaGoogle = mapa }
                val icones = remember(densidade) { IconesMapa(densidade) }

                shopping.itens.forEach { item ->
                    key(item.id, item.lat, item.lng) {
                        val estado = remember { MarkerState(LatLng(item.lat, item.lng)) }
                        val destaque = item.id == selecionadoId || item.id == movendo?.id
                        val visivelNoPiso = noPisoAtual(item)
                        Marker(
                            state = estado,
                            icon = icones.de(item, destaque),
                            anchor = if (item.eLoja) Offset(0.5f, 1f) else Offset(0.5f, 0.5f),
                            alpha = if (visivelNoPiso || destaque) 1f else 0.3f,
                            zIndex = when {
                                destaque -> 10f
                                !visivelNoPiso -> 0f
                                item.eLoja -> 2f
                                else -> 3f
                            },
                            title = null,
                            onClick = {
                                if (editando) {
                                    if (movendo == null) alvo = item to false
                                } else {
                                    focar(item)
                                }
                                true
                            },
                        )
                    }
                }

                rota?.let { r ->
                    Polyline(
                        points = listOf(LatLng(r.origem.lat, r.origem.lng), LatLng(r.destino.lat, r.destino.lng)),
                        color = COR_ROTA,
                        width = 7f * densidade,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 5f,
                    )
                }
            }

            // Resultados da busca por cima do mapa
            val termo = normalizar(busca)
            if (termo.isNotEmpty() || mostrarLista) {
                val resultados = shopping.itens
                    .filter { it.eLoja }
                    .filter {
                        if (termo.isEmpty()) it.eComida
                        else normalizar(it.nome + " " + it.numero + " " + nomeCategoria(it.categoria)).contains(termo)
                    }
                    .sortedWith(compareByDescending<Item> { it.eComida }.thenBy { normalizar(it.nome) })

                ElevatedCard(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .heightIn(max = if (mostrarLista) 440.dp else 320.dp)
                ) {
                    if (resultados.isEmpty()) {
                        Text(
                            if (shopping.itens.none { it.eLoja }) "Ainda não marcamos lojas neste shopping."
                            else "Nenhuma loja encontrada.",
                            Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn {
                            items(resultados, key = { it.id }) { loja ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { focar(loja) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .background(corDaLoja(loja.categoria), MaterialTheme.shapes.extraSmall)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(loja.nome, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOf(
                                                loja.piso.takeIf { it.isNotBlank() }?.let { "Piso $it" }.orEmpty(),
                                                loja.numero,
                                                nomeCategoria(loja.categoria),
                                            ).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // Painel inferior
        val item = selecionado
        when {
            item != null && item.eLoja -> PainelInfo(
                titulo = item.nome,
                linhas = listOfNotNull(
                    listOf(
                        item.piso.takeIf { it.isNotBlank() }?.let { "Piso $it" },
                        item.numero.takeIf { it.isNotBlank() }?.let { "Loja $it" },
                        nomeCategoria(item.categoria),
                    ).filterNotNull().joinToString(" · "),
                    item.dica.takeIf { it.isNotBlank() }?.let { "Dica: $it" },
                    rota?.aviso ?: "A entrada de motoboys deste shopping ainda não foi marcada.",
                ),
                acao = rota?.let { r -> "Ir de moto até a entrada" to { abrirNavegacao(r.origem.lat, r.origem.lng) } },
                aoFechar = { selecionadoId = null },
            )
            item != null -> PainelInfo(
                titulo = item.titulo,
                linhas = listOfNotNull(
                    item.piso.takeIf { it.isNotBlank() }?.let { "Piso $it" },
                    item.dica.takeIf { it.isNotBlank() },
                ),
                acao = if (item.tipo in listOf("entrada_motoboy", "estacionamento", "entrada")) {
                    "Ir de moto até aqui" to { abrirNavegacao(item.lat, item.lng) }
                } else null,
                aoFechar = { selecionadoId = null },
            )
            editando -> Unit
            shopping.observacoes.isNotBlank() && !avisoFechado -> PainelInfo(
                titulo = "Aviso para motoboys",
                linhas = listOf(shopping.observacoes),
                aoFechar = { avisoFechado = true },
            )
            shopping.itens.isEmpty() && !avisoFechado -> PainelInfo(
                titulo = "Em breve",
                linhas = listOf("Ainda estamos marcando a entrada de motoboys e as lojas deste shopping. O mapa do Google já mostra o prédio."),
                aoFechar = { avisoFechado = true },
            )
        }
        Legenda(shopping)
    }

    // Diálogos do modo edição
    if (mostrarImportar) {
        AlertDialog(
            onDismissRequest = { mostrarImportar = false },
            title = { Text("Importar lojas do Google") },
            text = {
                Text(
                    "Deixe o shopping inteiro aparecendo na tela, de preferência sem as ruas em volta. " +
                        "Vou buscar lanchonetes, restaurantes, cafés, padarias e sorveterias dessa área e marcar no mapa " +
                        "com o nome e a cor certa.\n\nO Google não informa o piso: depois toque em cada loja para conferir."
                )
            },
            confirmButton = {
                TextButton(onClick = { mostrarImportar = false; importar() }) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { mostrarImportar = false }) { Text("Cancelar") } },
        )
    }
    adicionarEm?.let { (pos, nomeSugerido) ->
        DialogoAdicionar(
            piso = nivelAtual.orEmpty(),
            aoEscolher = { adicionar(it, pos, nomeSugerido) },
            aoFechar = { adicionarEm = null },
        )
    }
    alvo?.let { (a, nova) ->
        key(a.id) {
            DialogoItem(
                item = a,
                nova = nova,
                aoSalvar = { novo ->
                    aoAlterar(
                        shopping.copy(
                            itens = if (nova) shopping.itens + novo
                            else shopping.itens.map { if (it.id == novo.id) novo else it }
                        )
                    )
                    alvo = null
                },
                aoExcluir = {
                    aoAlterar(shopping.copy(itens = shopping.itens.filter { it.id != a.id }))
                    alvo = null
                },
                aoMover = { movendo = a; alvo = null },
                aoFechar = { alvo = null },
            )
        }
    }
}

@Composable
private fun PainelInfo(
    titulo: String,
    linhas: List<String>,
    acao: Pair<String, () -> Unit>? = null,
    aoFechar: (() -> Unit)?,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                linhas.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (acao != null) {
                    Button(onClick = acao.second, modifier = Modifier.padding(top = 8.dp)) { Text(acao.first) }
                }
            }
            if (aoFechar != null) {
                IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar") }
            }
        }
    }
}

@Composable
private fun Legenda(shopping: Shopping) {
    val lojas = shopping.itens.filter { it.eLoja }
    val grupos = lojas.mapNotNull { grupoDaCategoria(it.categoria) }.toSet()
    val temOutras = lojas.any { grupoDaCategoria(it.categoria) == null }
    val tiposPonto = shopping.itens.filter { !it.eLoja }.map { it.tipo }.distinct()
    if (grupos.isEmpty() && !temOutras && tiposPonto.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GRUPOS_COMIDA.filter { it in grupos }.forEach { ItemLegenda(cor = it.cor, texto = it.nome) }
        if (temOutras) ItemLegenda(cor = COR_OUTRAS_LOJAS, texto = "Outras lojas")
        tiposPonto.forEach { tipo ->
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
