package com.quimene.app.ui

import com.quimene.domain.stats.Badge

/** Miroir de l'extension `Badge.Kind.label` (`ResultsView.swift`). */
val Badge.Kind.label: String
    get() =
        when (this) {
            Badge.Kind.Winner -> "Vainqueur"
            Badge.Kind.Metronome -> "Le Métronome"
            Badge.Kind.Rollercoaster -> "Les montagnes russes"
            Badge.Kind.Comeback -> "La remontada"
            Badge.Kind.Kamikaze -> "Le kamikaze"
            Badge.Kind.Unshakeable -> "Imperturbable"
            Badge.Kind.PhotoFinish -> "Photo finish"
            Badge.Kind.Sniper -> "Le Sniper"
            Badge.Kind.Boulet -> "Le Boulet"
            Badge.Kind.Landslide -> "Le Fossé"
        }
