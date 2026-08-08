package app.lumen

import android.annotation.SuppressLint
import android.content.Context

/**
 * Contexte applicatif, posé au démarrage de l'activité.
 *
 * Les actual non-composables (ouvrir une URL, télécharger, verrou Wi-Fi)
 * n'ont pas accès à LocalContext : ce point unique le leur fournit.
 */
@SuppressLint("StaticFieldLeak")
object AndroidCtx {
    lateinit var app: Context
}
