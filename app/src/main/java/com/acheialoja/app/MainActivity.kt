package com.acheialoja.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.acheialoja.app.ui.theme.ModoTema
import com.acheialoja.app.ui.theme.PreferenciaTema
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
            var modoTema by remember { mutableStateOf(PreferenciaTema.ler(this)) }
            val escuro = when (modoTema) {
                ModoTema.SISTEMA -> isSystemInDarkTheme()
                ModoTema.CLARO -> false
                ModoTema.ESCURO -> true
            }
            // Ícones da barra de status acompanham o tema escolhido
            LaunchedEffect(escuro) {
                val estilo = if (escuro) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }
            TemaAcheiALoja(escuro) {
                App(
                    modoTema = modoTema,
                    aoMudarTema = { novo ->
                        modoTema = novo
                        PreferenciaTema.salvar(this, novo)
                    },
                )
            }
        }
    }
}

@Composable
private fun App(modoTema: ModoTema, aoMudarTema: (ModoTema) -> Unit) {
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
            modoTema = modoTema,
            aoMudarTema = aoMudarTema,
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
