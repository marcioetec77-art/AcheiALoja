package com.acheialoja.app.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Laranja = Color(0xFFFF6A13)
val LaranjaEscuro = Color(0xFFC94A00)
val AzulNoite = Color(0xFF14213D)
val Verde = Color(0xFF1E9E5A)
val Amarelo = Color(0xFFFFD166)

private val Claro = lightColorScheme(
    primary = LaranjaEscuro,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0CC),
    onPrimaryContainer = Color(0xFF3A1400),
    secondary = AzulNoite,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE3F5),
    onSecondaryContainer = AzulNoite,
    background = Color(0xFFFAF8F6),
    surface = Color(0xFFFAF8F6),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFFFF9A5C),
    onPrimary = Color(0xFF3A1400),
    primaryContainer = Color(0xFF7A2E00),
    onPrimaryContainer = Color(0xFFFFE0CC),
    secondary = Color(0xFFB8C6EA),
    onSecondary = AzulNoite,
    secondaryContainer = Color(0xFF2A3A5E),
    onSecondaryContainer = Color(0xFFDCE3F5),
    background = Color(0xFF111418),
    surface = Color(0xFF111418),
)

/** Escolha do usuário: seguir o celular, sempre claro ou sempre escuro. */
enum class ModoTema(val rotulo: String) {
    SISTEMA("Automático (igual ao celular)"),
    CLARO("Claro"),
    ESCURO("Escuro"),
}

/** Guarda a escolha do tema no próprio celular. */
object PreferenciaTema {
    private const val ARQUIVO = "config"
    private const val CHAVE = "modo_tema"

    fun ler(context: Context): ModoTema {
        val nome = context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .getString(CHAVE, ModoTema.SISTEMA.name)
        return ModoTema.entries.firstOrNull { it.name == nome } ?: ModoTema.SISTEMA
    }

    fun salvar(context: Context, modo: ModoTema) {
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .edit().putString(CHAVE, modo.name).apply()
    }
}

@Composable
fun TemaAcheiALoja(escuro: Boolean, conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (escuro) Escuro else Claro,
        content = conteudo,
    )
}
