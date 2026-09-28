package com.quimene.app.ui

import androidx.annotation.StringRes
import com.quimene.app.R
import com.quimene.domain.stats.Badge

/** Miroir de l'extension `Badge.Kind.label` (`ResultsView.swift`). */
@get:StringRes
val Badge.Kind.label: Int
    get() =
        when (this) {
            Badge.Kind.Winner -> R.string.vainqueur
            Badge.Kind.Metronome -> R.string.le_metronome
            Badge.Kind.Rollercoaster -> R.string.les_montagnes_russes
            Badge.Kind.Comeback -> R.string.la_remontada
            Badge.Kind.Kamikaze -> R.string.le_kamikaze
            Badge.Kind.Unshakeable -> R.string.imperturbable
            Badge.Kind.PhotoFinish -> R.string.photo_finish
            Badge.Kind.Sniper -> R.string.le_sniper
            Badge.Kind.Boulet -> R.string.le_boulet
            Badge.Kind.Landslide -> R.string.le_fosse
        }
