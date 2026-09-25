package com.acheialoja.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.acheialoja.app.ui.TelaConta
import com.acheialoja.app.ui.TelaLogin
import com.acheialoja.app.ui.TelaMapa
import com.acheialoja.app.ui.TelaShoppings
import com.acheialoja.app.ui.theme.TemaAcheiALoja
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

/** Telas do app (navegação simples por estado). */
sealed interface Tela {
    data object Lista : Tela
    data object Conta : Tela
    data class Mapa(val shoppingId: String, val exemplo: Boolean = false) : Tela
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaAcheiALoja { App() }
        }
    }
}

@Composable
private fun App() {
    var usuario by remember { mutableStateOf(Firebase.auth.currentUser) }
    var tela by remember { mutableStateOf<Tela>(Tela.Lista) }

    DisposableEffect(Unit) {
        val ouvinte = FirebaseAuth.AuthStateListener { usuario = it.currentUser }
        Firebase.auth.addAuthStateListener(ouvinte)
        onDispose { Firebase.auth.removeAuthStateListener(ouvinte) }
    }

    val u = usuario
    LaunchedEffect(u?.uid) { tela = Tela.Lista }
    if (u == null) {
        TelaLogin()
        return
    }

    BackHandler(enabled = tela != Tela.Lista) { tela = Tela.Lista }

    when (val t = tela) {
        Tela.Lista -> TelaShoppings(
            uid = u.uid,
            aoAbrir = { id, exemplo -> tela = Tela.Mapa(id, exemplo) },
            aoAbrirConta = { tela = Tela.Conta },
        )
        Tela.Conta -> TelaConta(
            email = u.email.orEmpty(),
            aoVoltar = { tela = Tela.Lista },
        )
        is Tela.Mapa -> TelaMapa(
            uid = u.uid,
            shoppingId = t.shoppingId,
            exemplo = t.exemplo,
            aoVoltar = { tela = Tela.Lista },
        )
    }
}
