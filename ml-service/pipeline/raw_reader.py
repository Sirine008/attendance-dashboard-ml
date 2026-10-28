"""Lecture du fichier de pointage brut (.xls, une feuille par collaborateur).

Utilisé pour l'exploration (phase 2) et la calibration du générateur synthétique.
L'import de référence en base est fait par le backend Java (même logique de normalisation).
"""
from __future__ import annotations

import re
from pathlib import Path

import pandas as pd
import xlrd

BOUNDARY_TERMINALS = {"TB", "TA"}
HIRE_NOTE = re.compile(r"embauche\D*(\d{2}/\d{2}/\d{4})", re.IGNORECASE)


def normalize_terminal(raw: str) -> tuple[str, str]:
    """'Sortie  SS1 B' -> ('OUT', 'SS1 B'). Le 1er mot donne le sens (Entrée/Sortie)."""
    parts = " ".join(str(raw).split()).split(" ", 1)
    direction = "IN" if parts[0].lower().startswith("entr") else "OUT"
    return direction, parts[1] if len(parts) > 1 else ""


def read_punches(path: str | Path) -> tuple[pd.DataFrame, dict[str, str]]:
    """Retourne (pointages, notes d'embauche {feuille: 'dd/mm/yyyy'})."""
    book = xlrd.open_workbook(str(path))
    rows, hires = [], {}
    for sheet in book.sheets():
        header_row = next(r for r in range(min(10, sheet.nrows)) if "_Time" in [str(v).strip() for v in sheet.row_values(r)])
        header = [str(v).strip() for v in sheet.row_values(header_row)]
        col = {name: i for i, name in enumerate(header) if name}
        for r in range(header_row + 1, sheet.nrows):
            values = sheet.row_values(r)
            for v in values:
                m = HIRE_NOTE.search(str(v))
                if m:
                    hires[sheet.name] = m.group(1)
            t = str(values[col["_Time"]]).strip()
            if not t:
                continue
            direction, terminal = normalize_terminal(values[col["Terminal_Name"]])
            rows.append(
                {
                    "sheet": sheet.name,
                    "user_id": str(values[col["User_ID"]]).strip(),
                    "user_name": str(values[col["User_Name"]]).strip(),
                    "ts": pd.to_datetime(t, format="%d/%m/%Y %H:%M:%S"),
                    "terminal_raw": values[col["Terminal_Name"]],
                    "direction": direction,
                    "terminal": terminal,
                }
            )
    df = pd.DataFrame(rows).sort_values(["user_id", "ts"]).reset_index(drop=True)
    df["boundary"] = df["terminal"].isin(BOUNDARY_TERMINALS)
    df["date"] = df["ts"].dt.normalize()
    return df, hires
