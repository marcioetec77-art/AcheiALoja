package com.acheialoja.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.acheialoja.app.R
import com.acheialoja.app.data.Repositorio
import com.acheialoja.app.ui.theme.Laranja
import kotlinx.coroutines.launch

@Composable
fun TelaLogin() {
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var cadastro by rememberSaveable { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var mensagemOk by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    fun executar(sucesso: String? = null, acao: suspend () -> Unit) {
        mensagem = null
        carregando = true
        escopo.launch {
            try {
                acao()
                mensagem = sucesso
                mensagemOk = true
            } catch (e: Exception) {
                mensagem = traduzirErro(e)
                mensagemOk = false
            } finally {
                carregando = false
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(Laranja, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Achei a Loja",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "O mapa das lojas de comida dentro do shopping, feito para motoboys.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it },
                label = { Text(if (cadastro) "Crie uma senha (mín. 6 caracteres)" else "Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            mensagem?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = if (mensagemOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (email.isBlank() || senha.isBlank()) {
                        mensagem = "Preencha e-mail e senha."; mensagemOk = false
                    } else {
                        executar {
                            if (cadastro) Repositorio.cadastrar(email, senha)
                            else Repositorio.entrar(email, senha)
                        }
                    }
                },
                enabled = !carregando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (carregando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(if (cadastro) "Criar conta" else "Entrar")
            }

            TextButton(onClick = { cadastro = !cadastro; mensagem = null }) {
                Text(if (cadastro) "Já tenho conta — entrar" else "Não tem conta? Cadastre-se")
            }
            if (!cadastro) {
                TextButton(onClick = {
                    if (email.isBlank()) {
                        mensagem = "Digite seu e-mail acima para recuperar a senha."; mensagemOk = false
                    } else {
                        executar("Se houver conta com este e-mail, enviamos um link para redefinir a senha.") {
                            Repositorio.recuperarSenha(email)
                        }
                    }
                }) { Text("Esqueci minha senha") }
            }
        }
    }
}

fun traduzirErro(e: Exception): String {
    val nome = e::class.simpleName.orEmpty()
    val msg = e.message.orEmpty()
    return when {
        nome.contains("InvalidCredentials") || msg.contains("credential", true) ->
            "E-mail ou senha incorretos."
        nome.contains("UserCollision") -> "Já existe uma conta com este e-mail."
        nome.contains("WeakPassword") -> "Senha fraca: use pelo menos 6 caracteres."
        nome.contains("InvalidUser") -> "Conta não encontrada."
        nome.contains("Network") || msg.contains("network", true) ->
            "Sem conexão com a internet. Tente de novo."
        msg.contains("badly formatted", true) -> "E-mail inválido."
        msg.contains("too many", true) -> "Muitas tentativas. Aguarde um pouco."
        else -> msg.ifBlank { "Algo deu errado. Tente de novo." }
    }
}
