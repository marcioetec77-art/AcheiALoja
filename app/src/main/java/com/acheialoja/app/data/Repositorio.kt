package com.acheialoja.app.data

import android.content.Context
import android.location.Geocoder
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Tudo que fala com o Firebase fica aqui.
 *
 * Estrutura no Firestore:
 *   shoppings/{id}      -> nome, cidade, ativo (bool), dados (texto JSON: posição no Google Maps + marcações),
 *                          criadoPor (uid do motoboy que sugeriu; ativo = false até o admin aprovar)
 *   usuarios/{uid}      -> favoritos (lista de ids de shopping)
 *   sugestoes/{auto}    -> uid, shoppingId, texto, criadoEm
 *
 * O Firestore guarda uma cópia local automaticamente, então o app continua
 * funcionando sem internet depois que o shopping foi aberto uma vez
 * (o desenho do Google Maps precisa de internet).
 */
object Repositorio {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    // ---------- Conta ----------

    suspend fun entrar(email: String, senha: String) {
        auth.signInWithEmailAndPassword(email.trim(), senha).await()
    }

    suspend fun cadastrar(email: String, senha: String) {
        auth.createUserWithEmailAndPassword(email.trim(), senha).await()
    }

    suspend fun recuperarSenha(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun sair() = auth.signOut()

    /** Apaga os dados do usuário e a conta. Pede a senha de novo (exigência do Firebase). */
    suspend fun excluirConta(senha: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        user.reauthenticate(EmailAuthProvider.getCredential(email, senha)).await()

        val minhas = db.collection("sugestoes").whereEqualTo("uid", user.uid).get().await()
        for (doc in minhas.documents) doc.reference.delete().await()
        // Shoppings sugeridos que ainda não foram aprovados
        val sugeridos = db.collection("shoppings").whereEqualTo("criadoPor", user.uid).get().await()
        for (doc in sugeridos.documents) doc.reference.delete().await()
        db.collection("usuarios").document(user.uid).delete().await()

        user.delete().await()
    }

    // ---------- Shoppings ----------

    fun shoppings(): Flow<List<ShoppingResumo>> = callbackFlow {
        val reg = db.collection("shoppings").addSnapshotListener { snap, erro ->
            if (erro != null) {
                close(erro); return@addSnapshotListener
            }
            // Inclui os sugeridos (ativo = false); a tela decide quem pode vê-los
            val lista = snap?.documents.orEmpty()
                .map {
                    ShoppingResumo(
                        id = it.id,
                        nome = it.getString("nome").orEmpty(),
                        cidade = it.getString("cidade").orEmpty(),
                        ativo = it.getBoolean("ativo") != false,
                        criadoPor = it.getString("criadoPor").orEmpty(),
                    )
                }
                .sortedBy { normalizar(it.nome) }
            trySend(lista)
        }
        awaitClose { reg.remove() }
    }

    suspend fun carregarShopping(id: String): Shopping {
        val doc = db.collection("shoppings").document(id).get().await()
        if (!doc.exists()) error("Este shopping não foi encontrado.")
        return DadosShopping.ler(
            id = id,
            nome = doc.getString("nome").orEmpty(),
            cidade = doc.getString("cidade").orEmpty(),
            texto = doc.getString("dados"),
        ).copy(
            ativo = doc.getBoolean("ativo") != false,
            criadoPor = doc.getString("criadoPor").orEmpty(),
        )
    }

    // ---------- Favoritos ----------

    fun favoritos(uid: String): Flow<Set<String>> = callbackFlow {
        val reg = db.collection("usuarios").document(uid).addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val lista = (snap?.get("favoritos") as? List<String>).orEmpty()
            trySend(lista.toSet())
        }
        awaitClose { reg.remove() }
    }

    fun alternarFavorito(uid: String, shoppingId: String, favoritar: Boolean) {
        val valor = if (favoritar) FieldValue.arrayUnion(shoppingId) else FieldValue.arrayRemove(shoppingId)
        // Sem await: funciona offline e sincroniza quando a internet voltar
        db.collection("usuarios").document(uid)
            .set(mapOf("favoritos" to valor), SetOptions.merge())
    }

    // ---------- Sugestões de correção ----------

    suspend fun enviarSugestao(shoppingId: String, texto: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("sugestoes").add(
            mapOf(
                "uid" to uid,
                "shoppingId" to shoppingId,
                "texto" to texto.trim().take(1000),
                "criadoEm" to Timestamp.now(),
            )
        ).await()
    }

    // ---------- Administrador ----------

    /** É administrador quem tem um documento em admins/{uid} (criado pelo console do Firebase). */
    suspend fun eAdmin(uid: String): Boolean =
        db.collection("admins").document(uid).get().await().exists()

    /** Salva o shopping inteiro (todos veem na próxima vez que abrirem). */
    suspend fun salvarShopping(s: Shopping) {
        db.collection("shoppings").document(s.id).set(
            mapOf(
                "nome" to s.nome,
                "cidade" to s.cidade,
                "dados" to DadosShopping.escrever(s),
                "atualizadoEm" to Timestamp.now(),
            ),
            SetOptions.merge(),
        ).await()
    }

    /**
     * Procura um endereço/nome de lugar e devolve a posição (usa o Geocoder do próprio Android,
     * sem custo). Devolve null se não achar.
     */
    suspend fun buscarPosicao(contexto: Context, texto: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            if (texto.isBlank() || !Geocoder.isPresent()) return@withContext null
            try {
                @Suppress("DEPRECATION")
                val r = Geocoder(contexto, Locale("pt", "BR")).getFromLocationName(texto, 1)
                r?.firstOrNull()?.let { it.latitude to it.longitude }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Cria um shopping novo (sem marcações) na posição informada e devolve o id.
     * sugestao = true: criado por um motoboy; fica escondido até o administrador aprovar.
     */
    suspend fun criarShopping(nome: String, cidade: String, lat: Double, lng: Double, sugestao: Boolean): String {
        val uid = auth.currentUser?.uid ?: error("Faça login novamente.")
        val doc = db.collection("shoppings").document()
        val s = Shopping(doc.id, nome.trim(), cidade.trim(), "", lat, lng, 18f, emptyList())
        val dados = mutableMapOf<String, Any>(
            "nome" to s.nome,
            "cidade" to s.cidade,
            "ativo" to !sugestao,
            "dados" to DadosShopping.escrever(s),
            "atualizadoEm" to Timestamp.now(),
        )
        if (sugestao) {
            dados["criadoPor"] = uid
            dados["criadoEm"] = Timestamp.now()
        }
        doc.set(dados).await()
        return doc.id
    }

    /** Administrador: corrige nome e cidade de um shopping. */
    suspend fun renomearShopping(id: String, nome: String, cidade: String) {
        db.collection("shoppings").document(id).update(
            mapOf("nome" to nome.trim(), "cidade" to cidade.trim(), "atualizadoEm" to Timestamp.now())
        ).await()
    }

    /** Administrador: libera um shopping sugerido para todos verem. */
    suspend fun aprovarShopping(id: String) {
        db.collection("shoppings").document(id).update(
            // Ao aprovar, o shopping deixa de ter "dono" (não guardamos quem sugeriu)
            mapOf("ativo" to true, "aprovadoEm" to Timestamp.now(), "criadoPor" to FieldValue.delete())
        ).await()
    }

    suspend fun excluirShopping(id: String) {
        db.collection("shoppings").document(id).delete().await()
    }
}
