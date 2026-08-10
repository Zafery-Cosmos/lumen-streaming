package app.lumen.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Marge latérale des écrans, fournie par le Shell selon la largeur.
 *
 * Elle valait 48 dp en dur PARTOUT : confortable sur un écran de bureau,
 * absurde sur un téléphone, où elle mangeait un sixième de la largeur utile.
 */
val LocalSidePadding = compositionLocalOf { 48.dp }

/**
 * Vrai en layout compact (téléphone), fourni par le Shell selon la largeur.
 *
 * Sert à ne pas dupliquer le titre en texte sous une affiche qui l'affiche
 * déjà : sur un grand écran la légende passe inaperçue, sur téléphone elle
 * mange une ligne entière par carte visible à l'écran.
 */
val LocalCompactLayout = compositionLocalOf { false }
