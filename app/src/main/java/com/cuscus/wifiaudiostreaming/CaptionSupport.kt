/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 */

package com.cuscus.wifiaudiostreaming

import android.content.Context
import android.os.Build
import java.io.File

object CaptionSupport {

    const val OS = "android"

    val arch: String = when {
        Build.SUPPORTED_ABIS.isEmpty() -> "arm64"
        Build.SUPPORTED_ABIS[0].startsWith("arm64") -> "arm64"
        Build.SUPPORTED_ABIS[0].startsWith("x86_64") -> "x86_64"
        Build.SUPPORTED_ABIS[0].startsWith("armeabi") -> "armv7"
        else -> Build.SUPPORTED_ABIS[0]
    }

    val isSupported: Boolean = arch == "arm64" || arch == "x86_64"

    val renderOnly: Boolean get() = availableEngines().isEmpty()

    fun availableRuntimes(engine: AsrEngineKind): List<NativeRuntime> =
        if (!isSupported) emptyList()
        else CaptionCatalog.runtimesFor(engine, OS, arch)
            .filter { it.sha256 != null }

    fun availableEngines(): List<AsrEngineKind> =
        if (!isSupported) emptyList()
        else CaptionCatalog.SUPPORTED_ENGINES_ANDROID.filter { availableRuntimes(it).isNotEmpty() }

    fun availableModels(engine: AsrEngineKind): List<CaptionModel> =
        if (!isSupported) emptyList()
        else CaptionCatalog.modelsFor(engine, mobileOnly = true)

    fun dataRoot(context: Context): File =
        File(context.filesDir, "captions").apply { mkdirs() }

    fun totalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024L * 1024L)
    }

    fun fitsInMemory(context: Context, model: CaptionModel): Boolean =
        totalRamMb(context) >= model.approxRamMb * 3L
}
