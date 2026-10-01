package com.osx23.avi.data

import com.osx23.avi.model.Registro
import com.osx23.avi.model.toInsert
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RegistroRepository {
    private val _registros = MutableStateFlow<List<Registro>>(emptyList())
    val registros: StateFlow<List<Registro>> = _registros.asStateFlow()

    suspend fun registrar(registro: Registro): Result<Unit> = runCatching {
        check(SupabaseProvider.isConfigured) {
            "Configura Supabase en local.properties antes de registrar."
        }
        SupabaseProvider.client.from("registros").insert(registro.toInsert())
        refresh().getOrThrow()
    }

    suspend fun refresh(): Result<Unit> = runCatching {
        if (!SupabaseProvider.isConfigured) {
            _registros.value = emptyList()
            return@runCatching
        }
        _registros.value = SupabaseProvider.client
            .from("registros")
            .select {
                order(column = "creado_en", order = Order.DESCENDING)
                limit(count = 20)
            }
            .decodeList<Registro>()
    }
}
