#!/usr/bin/env python3
"""Extrait apple/App/Resources/Localizable.xcstrings vers les 5
android/app/src/main/res/values{,-en,-es,-de,-it}/strings.xml, plus une
table de correspondance committée (android/l10n-correspondence.json) —
texte source français -> nom de ressource stable, pour que relancer ce
script plus tard (nouvelles clés côté Apple) ne réordonne/renomme jamais
les ressources déjà référencées depuis le Kotlin (doc 11, étape G).

À lancer depuis la racine du monorepo : python3 Scripts/extract-android-strings.py

Portée : le CHROME de l'interface (232 clés Localizable.xcstrings). Le
contenu des jeux (spec/games/*.json) est une source de vérité séparée, déjà
partagée telle quelle et lue directement par :catalog au runtime — rien à
extraire ici pour lui.

Après extraction, le câblage effectif (remplacer chaque littéral Kotlin par
stringResource(R.string.<nom>)) reste manuel — ce script ne touche jamais
les fichiers .kt. Une passe de repérage utile :
  grep -rnE '"[^"]*\\$\\{?[a-zA-Z]|"[^"]*[àâäéèêëïîôöùûüçÀÂÉÈ]' \\
    android/app/src/main/kotlin/com/quimene/app --include='*.kt' \\
    | grep -v 'stringResource(R\\.string\\.'
"""
import json
import os
import re
import unicodedata
import xml.sax.saxutils as sx
from collections import OrderedDict

XCSTRINGS = "apple/App/Resources/Localizable.xcstrings"
TABLE_OUT = "android/l10n-correspondence.json"
RES_DIR = "android/app/src/main/res"
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
    tmp = key
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

    tmp = PLACEHOLDER_RE.sub(repl, tmp)
    tmp = strip_accents(tmp)
    tmp = tmp.lower()
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
    """%@ / %lld -> %1$s / %1$d positionnels (ordre d'apparition), comme
    Android l'exige dès qu'il y a plus d'un spécificateur. Les indices
    explicites Swift (%1$@) sont respectés tels quels."""
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


def main():
    with open(XCSTRINGS, encoding="utf-8") as f:
        data = json.load(f)
    strings = data["strings"]

    used_names = set()
    table = OrderedDict()

    for key in sorted(strings.keys()):
        if key.strip() in ("", "—"):
            continue  # pas une chaîne de chrome traduisible - séparateur/valeur vide
        entry = strings[key]
        localizations = entry.get("localizations", {})
        name = slugify(key)
        base_name = name
        n = 2
        while name in used_names:
            name = f"{base_name}_{n}"
            n += 1
        used_names.add(name)

        fr = localizations.get("fr", {}).get("stringUnit", {}).get("value", key)
        translations = {"fr": fr}
        for lang in LANGS:
            loc = localizations.get(lang, {}).get("stringUnit", {}).get("value")
            translations[lang] = loc if loc else fr  # repli sur le français si absent
        table[name] = {"key": key, **translations}

    with open(TABLE_OUT, "w", encoding="utf-8") as f:
        json.dump(table, f, ensure_ascii=False, indent=2)
        f.write("\n")

    def write_strings_xml(lang_code, lang_key):
        path = f"{RES_DIR}/values{'-' + lang_code if lang_code else ''}/strings.xml"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write("<resources>\n")
            for name, entry in table.items():
                value = convert_format(entry[lang_key])
                f.write(f'    <string name="{name}">{xml_escape(value)}</string>\n')
            f.write("</resources>\n")
        print(f"wrote {path} ({len(table)} strings)")

    write_strings_xml("", "fr")
    for lang in LANGS:
        write_strings_xml(lang, lang)

    print(f"correspondence table: {TABLE_OUT}")
    print(f"total resource names: {len(table)}")


if __name__ == "__main__":
    main()
