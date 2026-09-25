package com.acheialoja.app.data

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
import kotlinx.coroutines.tasks.await

/**
 * Tudo que fala com o Firebase fica aqui.
 *
 * Estrutura no Firestore:
 *   shoppings/{id}      -> nome, cidade, ativo (bool), mapa (texto JSON gerado pelo editor)
 *   usuarios/{uid}      -> favoritos (lista de ids de shopping)
 *   sugestoes/{auto}    -> uid, shoppingId, texto, criadoEm
 *
 * O Firestore guarda uma cópia local automaticamente, então o app continua
 * funcionando sem internet depois que o mapa foi aberto uma vez.
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
        db.collection("usuarios").document(user.uid).delete().await()

        user.delete().await()
    }

    // ---------- Shoppings ----------

    fun shoppings(): Flow<List<ShoppingResumo>> = callbackFlow {
        val reg = db.collection("shoppings").addSnapshotListener { snap, erro ->
            if (erro != null) {
                close(erro); return@addSnapshotListener
            }
            val lista = snap?.documents.orEmpty()
                .filter { it.getBoolean("ativo") != false }
                .map {
                    ShoppingResumo(
                        id = it.id,
                        nome = it.getString("nome").orEmpty(),
                        cidade = it.getString("cidade").orEmpty(),
                    )
                }
                .sortedBy { normalizar(it.nome) }
            trySend(lista)
        }
        awaitClose { reg.remove() }
    }

    suspend fun carregarMapa(id: String): Mapa {
        val doc = db.collection("shoppings").document(id).get().await()
        val texto = doc.getString("mapa")
            ?: error("Este shopping ainda não tem mapa cadastrado.")
        val mapa = LeitorMapa.ler(texto, idPadrao = id)
        // Nome/cidade do documento têm prioridade (facilita corrigir pelo console)
        return mapa.copy(
            id = id,
            nome = doc.getString("nome") ?: mapa.nome,
            cidade = doc.getString("cidade") ?: mapa.cidade,
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
}
