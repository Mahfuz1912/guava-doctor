package com.guavacare.ai.presentation.theme

import androidx.compose.ui.graphics.Color
import com.guavacare.ai.domain.model.GuavaLeafClass

// Light Theme — Clean Airy White & Botanical Green Minimalism
val OrchardGreenPrimary = Color(0xFF16794B)
val OrchardOnPrimary = Color(0xFFFFFFFF)
val OrchardPrimaryContainer = Color(0xFFE3F5EB)
val OrchardOnPrimaryContainer = Color(0xFF0A3822)

val OliveSecondary = Color(0xFF4E6658)
val OliveOnSecondary = Color(0xFFFFFFFF)
val OliveSecondaryContainer = Color(0xFFEAF2EC)
val OliveOnSecondaryContainer = Color(0xFF182D20)

val HarvestAmberTertiary = Color(0xFFB4691E)
val HarvestOnTertiary = Color(0xFFFFFFFF)
val HarvestTertiaryContainer = Color(0xFFFFF1E2)
val HarvestOnTertiaryContainer = Color(0xFF422200)

val StoneSurface = Color(0xFFF7FAF7)
val StoneSurfaceContainerLow = Color(0xFFF0F5F1)
val StoneSurfaceContainer = Color(0xFFFFFFFF)
val StoneSurfaceContainerHigh = Color(0xFFE8EFEA)
val StoneOnSurface = Color(0xFF15221B)
val StoneOnSurfaceVariant = Color(0xFF56685D)
val StoneOutline = Color(0xFFCFDCD3)
val StoneOutlineVariant = Color(0xFFE6EEE8)

val AnthracnoseCrimson = Color(0xFFC6382E)
val AnthracnoseContainer = Color(0xFFFDECEB)
val AnthracnoseOnContainer = Color(0xFF57120E)

// Dark Theme — Deep Canopy Slate
val LeafSagePrimaryDark = Color(0xFF7CD69F)
val LeafSageOnPrimaryDark = Color(0xFF04381E)
val LeafSagePrimaryContainerDark = Color(0xFF144B2D)
val LeafSageOnPrimaryContainerDark = Color(0xFFC5F2D6)

val OliveSecondaryDark = Color(0xFFB3CBB9)
val OliveOnSecondaryDark = Color(0xFF1F3527)
val OliveSecondaryContainerDark = Color(0xFF324B3B)
val OliveOnSecondaryContainerDark = Color(0xFFD6E8DA)

val HarvestAmberDark = Color(0xFFF5B971)
val HarvestOnAmberDark = Color(0xFF492700)
val HarvestAmberContainerDark = Color(0xFF6B3C0A)
val HarvestOnAmberContainerDark = Color(0xFFFFE1C2)

val CanopySurfaceDark = Color(0xFF0F1511)
val CanopySurfaceContainerLowDark = Color(0xFF151D18)
val CanopySurfaceContainerDark = Color(0xFF1A241E)
val CanopySurfaceContainerHighDark = Color(0xFF233028)
val CanopyOnSurfaceDark = Color(0xFFE6ECE7)
val CanopyOnSurfaceVariantDark = Color(0xFFAEBCB1)
val CanopyOutlineDark = Color(0xFF3E4E43)
val CanopyOutlineVariantDark = Color(0xFF2B3830)

val ErrorDark = Color(0xFFF2B8B5)
val ErrorContainerDark = Color(0xFF8C1D18)

// Semantic Class Status Tokens
data class LeafClassPalette(
    val accent: Color,
    val container: Color,
    val onContainer: Color
)

fun leafClassPalette(leafClass: GuavaLeafClass, isDark: Boolean): LeafClassPalette {
    return if (!isDark) {
        when (leafClass) {
            GuavaLeafClass.HEALTHY -> LeafClassPalette(
                accent = Color(0xFF16794B),
                container = Color(0xFFE4F6EC),
                onContainer = Color(0xFF0B3D22)
            )
            GuavaLeafClass.ANTHRACNOSE -> LeafClassPalette(
                accent = Color(0xFFC6382E),
                container = Color(0xFFFDECEB),
                onContainer = Color(0xFF611510)
            )
            GuavaLeafClass.NUTRIENT_STRESS -> LeafClassPalette(
                accent = Color(0xFFB66E08),
                container = Color(0xFFFFF4DF),
                onContainer = Color(0xFF543100)
            )
            GuavaLeafClass.PEST_DAMAGE -> LeafClassPalette(
                accent = Color(0xFFC95522),
                container = Color(0xFFFFEDE4),
                onContainer = Color(0xFF5F2207)
            )
            GuavaLeafClass.SOOTY_MOLD -> LeafClassPalette(
                accent = Color(0xFF4E5E55),
                container = Color(0xFFECF1EE),
                onContainer = Color(0xFF212B25)
            )
        }
    } else {
        when (leafClass) {
            GuavaLeafClass.HEALTHY -> LeafClassPalette(
                accent = Color(0xFF7CD69F),
                container = Color(0xFF144227),
                onContainer = Color(0xFFC9F5D9)
            )
            GuavaLeafClass.ANTHRACNOSE -> LeafClassPalette(
                accent = Color(0xFFFF8A80),
                container = Color(0xFF5D1916),
                onContainer = Color(0xFFFFDAD6)
            )
            GuavaLeafClass.NUTRIENT_STRESS -> LeafClassPalette(
                accent = Color(0xFFFFCC70),
                container = Color(0xFF543506),
                onContainer = Color(0xFFFFE8BE)
            )
            GuavaLeafClass.PEST_DAMAGE -> LeafClassPalette(
                accent = Color(0xFFFFA073),
                container = Color(0xFF5C260E),
                onContainer = Color(0xFFFFDFD1)
            )
            GuavaLeafClass.SOOTY_MOLD -> LeafClassPalette(
                accent = Color(0xFFB5C4BA),
                container = Color(0xFF2D3832),
                onContainer = Color(0xFFE0EAE3)
            )
        }
    }
}
