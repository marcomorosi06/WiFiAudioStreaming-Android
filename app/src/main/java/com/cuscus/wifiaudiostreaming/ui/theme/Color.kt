/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
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
package com.cuscus.wifiaudiostreaming.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's own palette, used when the wallpaper colours are off or not
 * available (before Android 12): violet, where WiFi Screen Streaming is blue.
 * Secondary is a warm magenta, tertiary an orchid violet, the surfaces a
 * faintly violet grey.
 */
val PulseDark = darkColorScheme(
    primary = Color(0xFFD3BBFF),
    onPrimary = Color(0xFF3E1C76),
    primaryContainer = Color(0xFF55358E),
    onPrimaryContainer = Color(0xFFEBDCFF),
    inversePrimary = Color(0xFF6E4FA8),
    secondary = Color(0xFFFFAEDB),
    onSecondary = Color(0xFF5B1145),
    secondaryContainer = Color(0xFF77295D),
    onSecondaryContainer = Color(0xFFFFD8EC),
    tertiary = Color(0xFFE7B6FF),
    onTertiary = Color(0xFF4A1060),
    tertiaryContainer = Color(0xFF63297A),
    onTertiaryContainer = Color(0xFFF8D8FF),
    background = Color(0xFF110F15),
    onBackground = Color(0xFFE7E0EA),
    surface = Color(0xFF110F15),
    onSurface = Color(0xFFE7E0EA),
    surfaceVariant = Color(0xFF4A4454),
    onSurfaceVariant = Color(0xFFCCC3D6),
    surfaceContainerLowest = Color(0xFF0C0A10),
    surfaceContainerLow = Color(0xFF19171D),
    surfaceContainer = Color(0xFF1E1B22),
    surfaceContainerHigh = Color(0xFF28252D),
    surfaceContainerHighest = Color(0xFF333038),
    inverseSurface = Color(0xFFE7E0EA),
    inverseOnSurface = Color(0xFF322F36),
    outline = Color(0xFF958E9F),
    outlineVariant = Color(0xFF4A4454),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val PulseLight = lightColorScheme(
    primary = Color(0xFF6E4FA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDCFF),
    onPrimaryContainer = Color(0xFF27004F),
    inversePrimary = Color(0xFFD3BBFF),
    secondary = Color(0xFF94407A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8EC),
    onSecondaryContainer = Color(0xFF3B0030),
    tertiary = Color(0xFF7E4192),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8D8FF),
    onTertiaryContainer = Color(0xFF320045),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1A20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1A20),
    surfaceVariant = Color(0xFFE9DFF1),
    onSurfaceVariant = Color(0xFF4A4454),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1FB),
    surfaceContainer = Color(0xFFF2EBF5),
    surfaceContainerHigh = Color(0xFFECE5F0),
    surfaceContainerHighest = Color(0xFFE6E0EA),
    inverseSurface = Color(0xFF322F36),
    inverseOnSurface = Color(0xFFF5EFF8),
    outline = Color(0xFF7B7485),
    outlineVariant = Color(0xFFCCC3D6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)
