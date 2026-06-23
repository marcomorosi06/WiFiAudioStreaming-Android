/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package com.cuscus.wifiaudiostreaming.data

import org.json.JSONArray
import org.json.JSONObject

data class AppScript(
    val id: String,
    val name: String,
    val actionId: String,
    val params: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("action", actionId)
        val p = JSONObject()
        params.forEach { (k, v) -> p.put(k, v) }
        obj.put("params", p)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AppScript {
            val params = mutableMapOf<String, String>()
            val p = obj.optJSONObject("params")
            if (p != null) {
                for (key in p.keys()) {
                    params[key] = p.optString(key)
                }
            }
            return AppScript(
                id = obj.optString("id"),
                name = obj.optString("name"),
                actionId = obj.optString("action"),
                params = params
            )
        }

        fun parseList(raw: String): List<AppScript> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun serializeList(list: List<AppScript>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
