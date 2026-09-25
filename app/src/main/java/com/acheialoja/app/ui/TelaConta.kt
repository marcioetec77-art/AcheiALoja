package com.acheialoja.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.acheialoja.app.R
import com.acheialoja.app.data.Repositorio
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaConta(email: String, aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    val urlPrivacidade = stringResource(R.string.url_privacidade)
    var confirmarExclusao by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minha conta") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp)
        ) {
            Text("Conectado como", style = MaterialTheme.typography.labelMedium)
            Text(email, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))

            OutlinedButton(onClick = { Repositorio.sair() }, modifier = Modifier.fillMaxWidth()) {
                Text("Sair")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    contexto.startActivity(Intent(Intent.ACTION_VIEW, urlPrivacidade.toUri()))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Política de privacidade") }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Text("Zona de perigo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Text(
                "Excluir a conta apaga seu cadastro, seus favoritos e as sugestões que você enviou. Não dá para desfazer.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { confirmarExclusao = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Excluir minha conta") }
        }
    }

    if (confirmarExclusao) DialogoExcluir(aoFechar = { confirmarExclusao = false })
}

@Composable
private fun DialogoExcluir(aoFechar: () -> Unit) {
    var senha by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var excluindo by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!excluindo) aoFechar() },
        title = { Text("Excluir conta?") },
        text = {
            Column {
                Text("Para confirmar, digite sua senha.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = senha,
                    onValueChange = { senha = it },
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                erro?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = senha.isNotBlank() && !excluindo,
                onClick = {
                    excluindo = true
                    erro = null
                    escopo.launch {
                        try {
                            Repositorio.excluirConta(senha)
                            // Ao excluir, o app volta sozinho para a tela de login
                        } catch (e: Exception) {
                            erro = traduzirErro(e)
                            excluindo = false
                        }
                    }
                },
            ) { Text(if (excluindo) "Excluindo…" else "Excluir", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = aoFechar, enabled = !excluindo) { Text("Cancelar") }
        },
    )
}
