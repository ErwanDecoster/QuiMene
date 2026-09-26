#!/usr/bin/env python3
"""Génère les android/app/src/main/res/values{,-en,-es,-de,-it}/strings.xml à partir de
apple/App/Resources/Localizable.xcstrings (traductions faites une seule fois, côté Apple —
doc 11, étape G).

À lancer depuis la racine du monorepo : python3 Scripts/extract-android-strings.py

- android/l10n-correspondence.json (committé) associe chaque clé du catalogue Apple à un nom de
  ressource Android. Un nom déjà attribué ne change jamais : relancer le script après l'ajout de
  clés côté Apple ne renomme rien de ce que le Kotlin référence déjà.
- strings.xml ne contient que les ressources réellement référencées par le code Kotlin
  (`R.string.<nom>`) : aucune chaîne inutilisée dans l'APK. Pour utiliser une nouvelle chaîne
  du catalogue Apple, chercher son nom dans la table de correspondance, le référencer depuis
  le Kotlin, puis relancer ce script.
- Les textes propres à Android (sans équivalent Apple) vivent dans strings_android.xml, dans les
  mêmes dossiers : maintenus à la main, jamais touchés par ce script.
- Pluriels : une clé à variations plurielles devient une ressource `<plurals>` (`R.plurals.<nom>`,
  à lire avec `pluralStringResource`). Quand une phrase accorde plusieurs nombres (substitutions
  `%#@…@` du catalogue), Android ne sachant accorder qu'une quantité par ressource, elle devient
  une `<string>` modèle (`R.string.<nom>`) dont chaque nombre est une ressource
  `R.plurals.<nom>_<substitution>` à insérer comme texte.

Le contenu des jeux (spec/games) est une source de vérité séparée, lue telle quelle par :catalog.
"""
import glob
import json
import os
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
import xml.sax.saxutils as sx
from collections import OrderedDict

XCSTRINGS = "apple/App/Resources/Localizable.xcstrings"
TABLE_OUT = "android/l10n-correspondence.json"
RES_DIR = "android/app/src/main/res"
KOTLIN_SOURCES = "android/app/src/main/kotlin"
LANGS = ["en", "es", "de", "it"]

PLACEHOLDER_RE = re.compile(r"%(\d+\$)?(@|lld|ld|d|f|u|s)")

# Mots réservés Java/Kotlin — AAPT refuse un nom de ressource qui en est un
# (le R généré serait un identifiant Java invalide).
RESERVED_WORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null",
    "fun", "val", "var", "when", "object", "is", "in", "as", "typealias",
}


def strip_accents(s: str) -> str:
    nfkd = unicodedata.normalize("NFKD", s)
    return "".join(c for c in nfkd if not unicodedata.combining(c))


def slugify(key: str) -> str:
    # Remplace les jetons de spécificateur par des mots avant de simplifier,
    # pour garder un nom lisible plutôt qu'un trou.
    counter = [0]

    def repl(m):
        counter[0] += 1
        kind = m.group(2)
        if kind in ("lld", "ld", "d", "u"):
            word = "count"
        elif kind == "f":
            word = "num"
        else:
            word = "value"
        return f"_{word}{counter[0]}_"

    tmp = PLACEHOLDER_RE.sub(repl, key)
    tmp = strip_accents(tmp).lower()
    tmp = re.sub(r"[^a-z0-9]+", "_", tmp)
    tmp = re.sub(r"_+", "_", tmp).strip("_")
    if not tmp:
        tmp = "empty"
    if tmp[0].isdigit():
        tmp = "n_" + tmp
    if tmp in RESERVED_WORDS:
        tmp = tmp + "_label"
    # Tronque proprement sur une frontière de mot, garde un nom lisible.
    if len(tmp) > 60:
        tmp = tmp[:60].rsplit("_", 1)[0]
    return tmp


def convert_format(swift_text: str) -> str:
    """%@ / %lld -> %1$s / %1$d positionnels (ordre d'apparition), comme Android l'exige dès
    qu'il y a plus d'un spécificateur. Les indices explicites Swift (%1$@) sont respectés."""
    positions = {"next": 1}

    def repl(m):
        explicit = m.group(1)
        kind = m.group(2)
        android_kind = "d" if kind in ("lld", "ld", "d", "u") else ("f" if kind == "f" else "s")
        if explicit:
            idx = int(explicit.rstrip("$"))
        else:
            idx = positions["next"]
            positions["next"] += 1
        return f"%{idx}${android_kind}"

    return PLACEHOLDER_RE.sub(repl, swift_text)


def xml_escape(s: str) -> str:
    # Le gras Markdown (**mot**) d'Apple (Text/LocalizedStringKey le parse) n'a pas
    # d'équivalent dans les Text() Compose de ce projet, qui n'utilisent pas
    # AnnotatedString pour ces chaînes — retiré plutôt qu'affiché tel quel.
    s = s.replace("**", "")
    s = sx.escape(s)
    s = s.replace("'", "\\'").replace('"', '\\"')
    # Android traite @ et ? en tête de valeur comme des références - échapper si besoin.
    if s.startswith(("@", "?")):
        s = "\\" + s
    return s


# Quantités écrites pour chaque langue (règles CLDR d'Android) : « many » sert aux grands
# nombres en français, espagnol et italien ; la forme « other » la remplace si le catalogue ne la
# donne pas.
QUANTITIES = {
    "fr": ["one", "many", "other"],
    "en": ["one", "other"],
    "es": ["one", "many", "other"],
    "de": ["one", "other"],
    "it": ["one", "many", "other"],
}


def android_only_resources() -> set:
    """(type, nom) des ressources maintenues à la main dans strings_android.xml."""
    resources = set()
    for path in glob.glob(f"{RES_DIR}/values*/strings_android.xml"):
        for node in ET.parse(path).getroot():
            if node.get("name"):
                resources.add((node.tag, node.get("name")))
    return resources


def referenced_resources() -> set:
    """(type, nom) de chaque `R.string.<nom>` / `R.plurals.<nom>` du code Kotlin."""
    pattern = re.compile(r"\bR\.(string|plurals)\.([a-z0-9_]+)")
    resources = set()
    for path in glob.glob(f"{KOTLIN_SOURCES}/**/*.kt", recursive=True):
        with open(path, encoding="utf-8") as f:
            resources.update(pattern.findall(f.read()))
    return resources


def plural_forms(variations: dict) -> dict:
    return {quantity: form["stringUnit"]["value"] for quantity, form in variations["plural"].items()}


def translation(localization: dict):
    """Une langue d'une clé, sous la forme qu'Android sait écrire (spécificateurs Swift conservés,
    traduits à l'écriture) : une chaîne ; `{quantité: phrase}` pour un pluriel ; `{"template":
    …, "parts": {substitution: {quantité: …}}}` pour une phrase qui accorde plusieurs nombres."""
    if "variations" in localization:
        return plural_forms(localization["variations"])
    main = localization.get("stringUnit", {}).get("value")
    substitutions = localization.get("substitutions", {})
    if main is None or not substitutions:
        return main
    if len(substitutions) == 1:
        ((name, sub),) = substitutions.items()
        return {
            quantity: main.replace(f"%#@{name}@", form.replace("%arg", f"%{sub['argNum']}$lld"))
            for quantity, form in plural_forms(sub["variations"]).items()
        }
    template = main
    for name, sub in substitutions.items():
        template = template.replace(f"%#@{name}@", f"%{sub['argNum']}$@")
    parts = {
        name.lower(): {quantity: form.replace("%arg", "%lld") for quantity, form in plural_forms(sub["variations"]).items()}
        for name, sub in substitutions.items()
    }
    return {"template": template, "parts": parts}


def kind(value) -> str:
    if isinstance(value, str):
        return "string"
    return "composite" if "template" in value else "plurals"


def produced_resources(name: str, value) -> set:
    if kind(value) == "string":
        return {("string", name)}
    if kind(value) == "plurals":
        return {("plurals", name)}
    return {("string", name)} | {("plurals", f"{name}_{part}") for part in value["parts"]}


def main():
    with open(XCSTRINGS, encoding="utf-8") as f:
        strings = json.load(f)["strings"]

    previous = {}
    if os.path.exists(TABLE_OUT):
        with open(TABLE_OUT, encoding="utf-8") as f:
            previous = {entry["key"]: name for name, entry in json.load(f).items()}

    android_only = android_only_resources()
    reserved = {name for _, name in android_only}
    keys = [
        k for k in sorted(strings)
        if strings[k].get("shouldTranslate") is not False and k.strip()
    ]
    # Les noms déjà attribués d'abord, pour qu'une nouvelle clé ne puisse jamais les prendre.
    used_names = {previous[k] for k in keys if k in previous}
    collisions = used_names & reserved
    if collisions:
        sys.exit(f"error: noms présents à la fois dans le catalogue et strings_android.xml : {sorted(collisions)}")
    used_names |= reserved

    table = OrderedDict()
    for key in keys:
        name = previous.get(key)
        if name is None:
            base = name = slugify(key)
            n = 2
            while name in used_names:
                name = f"{base}_{n}"
                n += 1
            used_names.add(name)
        localizations = strings[key].get("localizations", {})
        entry = {"key": key, "fr": translation(localizations["fr"]) if "fr" in localizations else key}
        for lang in LANGS:
            value = translation(localizations.get(lang, {}))
            if not value:
                sys.exit(f"error: « {key} » n'a pas de traduction « {lang} » dans le catalogue Apple")
            if kind(value) != kind(entry["fr"]):
                sys.exit(f"error: « {key} » n'a pas la même forme (chaîne, pluriel) en « {lang} » et en français")
            entry[lang] = value
        table[name] = entry

    with open(TABLE_OUT, "w", encoding="utf-8") as f:
        json.dump(table, f, ensure_ascii=False, indent=2)
        f.write("\n")

    referenced = referenced_resources()
    produced = set().union(*(produced_resources(name, entry["fr"]) for name, entry in table.items()))
    missing = sorted(referenced - produced - android_only)
    if missing:
        sys.exit(f"error: référencés par le Kotlin mais absents du catalogue et de strings_android.xml : {missing}")
    emitted = [name for name, entry in table.items() if produced_resources(name, entry["fr"]) & referenced]

    def plurals_xml(name: str, forms: dict, lang_key: str) -> str:
        items = "".join(
            f'        <item quantity="{quantity}">{xml_escape(convert_format(forms.get(quantity, forms["other"])))}</item>\n'
            for quantity in QUANTITIES[lang_key]
        )
        return f'    <plurals name="{name}">\n{items}    </plurals>\n'

    def write_strings_xml(lang_code, lang_key):
        path = f"{RES_DIR}/values{'-' + lang_code if lang_code else ''}/strings.xml"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write("<!-- Généré par Scripts/extract-android-strings.py depuis le catalogue Apple : ne pas\n")
            f.write("     modifier à la main (textes propres à Android : strings_android.xml). -->\n")
            if lang_code:
                f.write("<resources>\n")
            else:
                f.write('<resources xmlns:tools="http://schemas.android.com/tools" tools:locale="fr">\n')
            for name in emitted:
                value = table[name][lang_key]
                if kind(value) == "string":
                    f.write(f'    <string name="{name}">{xml_escape(convert_format(value))}</string>\n')
                elif kind(value) == "plurals":
                    f.write(plurals_xml(name, value, lang_key))
                else:
                    f.write(f'    <string name="{name}">{xml_escape(convert_format(value["template"]))}</string>\n')
                    for part, forms in value["parts"].items():
                        f.write(plurals_xml(f"{name}_{part}", forms, lang_key))
            f.write("</resources>\n")

    write_strings_xml("", "fr")
    for lang in LANGS:
        write_strings_xml(lang, lang)

    print(f"{len(table)} clés dans {TABLE_OUT}, {len(emitted)} utilisées par Android écrites dans strings.xml")


if __name__ == "__main__":
    main()
