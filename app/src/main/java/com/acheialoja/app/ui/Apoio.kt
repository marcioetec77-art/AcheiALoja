package com.acheialoja.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.acheialoja.app.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.Normalizer

/** Monta o "Pix copia e cola" (BR Code estático, padrão do Banco Central). */
object Pix {
    private fun campo(id: String, valor: String) = id + valor.length.toString().padStart(2, '0') + valor

    private fun limpar(s: String, max: Int) =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^A-Za-z0-9 ]"), "")
            .uppercase()
            .trim()
            .take(max)

    fun copiaECola(chave: String, nome: String, cidade: String): String {
        val conta = campo("00", "br.gov.bcb.pix") + campo("01", chave.trim())
        val semCrc = campo("00", "01") +
            campo("26", conta) +
            campo("52", "0000") +
            campo("53", "986") +
            campo("58", "BR") +
            campo("59", limpar(nome, 25)) +
            campo("60", limpar(cidade, 15)) +
            campo("62", campo("05", "***")) +
            "6304"
        return semCrc + crc16(semCrc)
    }

    /** CRC16-CCITT (polinômio 0x1021, inicial 0xFFFF), exigido pelo Pix. */
    private fun crc16(texto: String): String {
        var crc = 0xFFFF
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }

    fun qrCode(texto: String, tamanho: Int = 600): ImageBitmap {
        val matriz = QRCodeWriter().encode(
            texto, BarcodeFormat.QR_CODE, tamanho, tamanho,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(tamanho * tamanho) { i ->
            if (matriz.get(i % tamanho, i / tamanho)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, tamanho, tamanho, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Nome que aparece como desenvolvedor do app. */
const val DESENVOLVEDOR = "Marcio Monteiro Albino"

/** Janela "Apoie o desenvolvedor" com QR Code do Pix e botão de copiar. */
@Composable
fun DialogoApoio(aoFechar: () -> Unit) {
    val chave = stringResource(R.string.pix_chave)
    val nome = stringResource(R.string.pix_nome)
    val cidade = stringResource(R.string.pix_cidade)
    val codigo = remember(chave, nome, cidade) { Pix.copiaECola(chave, nome, cidade) }
    val qr = remember(codigo) { runCatching { Pix.qrCode(codigo) }.getOrNull() }
    val area = LocalClipboardManager.current
    var copiado by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Apoie o Achei a Loja") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "O app é gratuito. Se ele te ajuda nas entregas, você pode contribuir com qualquer valor " +
                        "para as atualizações e a manutenção.",
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Desenvolvedor: $DESENVOLVEDOR",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (qr != null) {
                    Box(
                        Modifier
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Image(qr, contentDescription = "QR Code do Pix", modifier = Modifier.size(220.dp))
                    }
                }
                Text("Chave Pix", style = MaterialTheme.typography.labelMedium)
                Text(chave, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Button(
                    onClick = { area.setText(AnnotatedString(codigo)); copiado = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (copiado) "Copiado! Cole no app do banco" else "Copiar Pix copia e cola") }
                Text(
                    "Contribuição voluntária: não libera nenhuma função extra. Obrigado!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}
