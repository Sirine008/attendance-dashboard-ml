"""Phase 2 — profil des données de pointage → docs/data_profile.md (+ graphiques docs/img/).

Usage : python -m analysis.profile_data [chemin.xls]
"""
from __future__ import annotations

import sys
from datetime import date, timedelta
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

from pipeline.calendar_tn import PUBLIC_HOLIDAYS, SPECIAL_PERIODS, period_kind  # noqa: E402
from pipeline.raw_reader import read_punches  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
IMG = DOCS / "img"
DUP_SECONDS = 120
TOLERANCE_MIN = 10
DATA_START = date(2024, 1, 1)
WEEKDAYS = ["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"]


def hm(minutes: float) -> str:
    if pd.isna(minutes):
        return "—"
    m = int(round(minutes))
    return f"{m // 60}h{m % 60:02d}"


def md_table(df: pd.DataFrame, index: bool = True) -> str:
    d = df.reset_index() if index else df
    cols = [str(c) for c in d.columns]
    lines = ["| " + " | ".join(cols) + " |", "|" + "---|" * len(cols)]
    for _, row in d.iterrows():
        lines.append("| " + " | ".join(str(v) for v in row.values) + " |")
    return "\n".join(lines)


def main(path: Path) -> None:
    IMG.mkdir(parents=True, exist_ok=True)
    punches, hires = read_punches(path)
    out: list[str] = []
    w = out.append

    w("# Profil des données de pointage\n")
    w(f"> Généré automatiquement par `ml-service/analysis/profile_data.py` à partir de `{path.relative_to(ROOT).as_posix()}`. Ne pas éditer à la main.\n")

    # 1. Structure
    w("## 1. Structure du fichier\n")
    w(f"- {punches['sheet'].nunique()} feuilles (une par collaborateur) : {', '.join(sorted(punches['sheet'].unique()))}.")
    n = f"{len(punches):,}".replace(",", " ")
    w(f"- **{n} pointages** au total (la spec annonçait ~12 400 : il s'agissait d'une estimation, le fichier contient {n} lignes horodatées).")
    w(f"- Période couverte : du {punches.ts.min():%d/%m/%Y} au {punches.ts.max():%d/%m/%Y}.")
    w("- En-tête en ligne 2 (`_Time | User_ID | User_Name | Terminal_Name`) ; la feuille `TTTTTT` a une 1re colonne `ordre`, les autres une colonne vide → colonnes détectées par nom.")
    w("- `User_ID` est identique au nom de la feuille pour toutes les lignes.")
    if hires:
        w(f"- **Notes d'embauche trouvées dans le fichier** (colonne hors en-tête) : " + ", ".join(f"`{k}` → {v}" for k, v in hires.items()) + ". Elles sont utilisées comme dates d'embauche réelles ; les autres dates d'embauche sont fictives (seed).")
    w("")
    per_emp = punches.groupby("sheet").agg(pointages=("ts", "size"), premier=("ts", "min"), dernier=("ts", "max"), jours_avec_pointage=("date", "nunique"))
    per_emp["premier"] = per_emp["premier"].dt.strftime("%d/%m/%Y")
    per_emp["dernier"] = per_emp["dernier"].dt.strftime("%d/%m/%Y")
    w(md_table(per_emp))
    w("")

    # 2. Terminaux
    w("## 2. Terminaux\n")
    w("`Terminal_Name` = sens + porte, avec espaces multiples (`Sortie  TB`). Normalisation : trim + espaces simples ; 1er mot → sens (`Entr…` = IN, `Sortie` = OUT), reste → porte.\n")
    term = punches.groupby(["terminal", "direction"]).size().unstack(fill_value=0)
    term["frontière bâtiment"] = ["oui" if t in ("TB", "TA") else "non (zone interne)" for t in term.index]
    w(md_table(term))
    internal = (~punches.boundary).mean()
    w(f"\n- {internal:.1%} des pointages concernent des portes internes (SS1/SS2) : ce sont des mouvements entre zones, **pas des sorties du bâtiment**.\n")

    # 3. Doublons
    w("## 3. Doublons\n")
    exact = punches.duplicated(["user_id", "ts", "terminal"]).sum()
    p = punches.sort_values(["user_id", "ts"]).copy()
    p["gap_s"] = p.groupby("user_id").ts.diff().dt.total_seconds()
    same = (p.groupby("user_id").direction.shift() == p.direction) & (p.groupby("user_id").terminal.shift() == p.terminal)
    near = {s: int(((p.gap_s <= s) & same).sum()) for s in (5, 30, 60, 120, 300)}
    w(f"- Doublons exacts (même seconde, même porte) : **{exact}**.")
    w("- Doublons proches (même sens, même porte, écart ≤ s secondes) : " + ", ".join(f"≤{k}s : {v}" for k, v in near.items()) + ".")
    w(f"- La courbe sature dès 60 s : on retient **{DUP_SECONDS} s** comme seuil de dédoublonnage (paramètre `app_settings.duplicate_window_seconds`). Les doublons sont conservés dans `raw_punch` mais marqués et ignorés dans les calculs.\n")
    p["is_dup"] = (p.gap_s <= DUP_SECONDS) & same
    clean = p[~p.is_dup]

    # 4. Journées
    w("## 4. Qualité des journées\n")
    bd = clean[clean.boundary]
    cnt = bd.groupby(["user_id", "date"]).size()
    days_all = clean.groupby(["user_id", "date"]).size()
    first_in = bd[bd.direction == "IN"].groupby(["user_id", "date"]).ts.min()
    w(f"- Journées avec au moins un pointage : {len(days_all)}.")
    w(f"- Journées avec un nombre **impair** de pointages frontière (pointage manquant) : **{int((cnt % 2 == 1).sum())}** ({(cnt % 2 == 1).mean():.1%}).")
    w(f"- Journées avec pointages mais sans entrée frontière : {len(days_all) - len(first_in)}.")
    wk = clean[clean.ts.dt.weekday >= 5].groupby(["user_id", "date"]).size()
    w(f"- Journées de week-end travaillées : {len(wk)} (" + ", ".join(f"{u} {d:%a %d/%m/%Y}" for (u, d) in wk.index) + ").")
    w("")

    # 5. Calendrier
    w("## 5. Jours fériés détectés\n")
    all_days = pd.bdate_range(punches.date.min(), punches.date.max())
    present = set(clean.date)
    empty_days = [d.date() for d in all_days if d not in present]
    w("Jours ouvrés (lun–ven) où **personne** n'a pointé, comparés au calendrier `public_holiday` :\n")
    w("| Date | Jour | Dans le calendrier |\n|---|---|---|")
    for d in empty_days:
        w(f"| {d:%d/%m/%Y} | {WEEKDAYS[d.weekday()]} | {PUBLIC_HOLIDAYS.get(d, '**non**')} |")
    worked_hol = sorted({d.date() for d in present if d.date() in PUBLIC_HOLIDAYS and d.weekday() < 5})
    w("")
    w("- Fériés du calendrier tombant un jour ouvré mais avec des pointages : " + (", ".join(f"{d:%d/%m/%Y}" for d in worked_hol) or "aucun") + ".")
    w("- Le 14/01/2025 (mardi) est travaillé : confirme que la Fête de la Révolution est désormais le **17 décembre** (décret 2021), pas le 14 janvier.\n")

    # 6. Absences
    w("## 6. Jours sans pointage et absences\n")
    end = clean.date.max().date()
    rows = []
    for emp in sorted(clean.user_id.unique()):
        start = DATA_START
        if emp in hires:
            d, m, y = map(int, hires[emp].split("/"))
            start = date(y, m, d)
        emp_days = set(clean.loc[clean.user_id == emp, "date"].dt.date)
        cur = start
        while cur <= end:
            if cur.weekday() < 5 and cur not in PUBLIC_HOLIDAYS:
                rows.append({"emp": emp, "date": cur, "absent": cur not in emp_days})
            cur += timedelta(days=1)
    wd = pd.DataFrame(rows)
    wd["run_id"] = (wd.absent != wd.groupby("emp").absent.shift()).cumsum()
    wd["run_len"] = wd.groupby("run_id").absent.transform("size")
    wd["kind"] = np.where(~wd.absent, "PRESENT", np.where(wd.run_len >= 3, "ABSENCE_LONGUE", "ABSENCE_PONCTUELLE"))
    tot = len(wd)
    w(f"- Jours-personnes ouvrés (du début de suivi au {end:%d/%m/%Y}) : **{tot}** ; sans pointage : {int(wd.absent.sum())} ({wd.absent.mean():.1%}).")
    w(f"- dont `ABSENCE_PONCTUELLE` (1–2 jours consécutifs) : **{int((wd.kind == 'ABSENCE_PONCTUELLE').sum())}** ({(wd.kind == 'ABSENCE_PONCTUELLE').mean():.1%}) ;")
    w(f"- dont `ABSENCE_LONGUE` (≥ 3 jours, congé probable) : **{int((wd.kind == 'ABSENCE_LONGUE').sum())}** ({(wd.kind == 'ABSENCE_LONGUE').mean():.1%}).\n")
    ab = wd.groupby("emp").agg(jours_ouvres=("date", "size"), sans_pointage=("absent", "sum"),
                               ponctuelles=("kind", lambda s: (s == "ABSENCE_PONCTUELLE").sum()),
                               longues=("kind", lambda s: (s == "ABSENCE_LONGUE").sum()))
    runs = wd[wd.absent].groupby("run_id").agg(emp=("emp", "first"), n=("run_len", "first"))
    ab["plus_longue_série"] = runs.groupby("emp").n.max()
    ab["taux_ponctuelle"] = (ab.ponctuelles / ab.jours_ouvres).map("{:.1%}".format)
    w(md_table(ab.fillna(0)))
    w("\nRépartition des séries d'absence par longueur (jours ouvrés consécutifs) :\n")
    rl = runs.n.value_counts().sort_index()
    w(md_table(pd.DataFrame({"longueur": rl.index, "nb_séries": rl.values}), index=False))
    wd["weekday"] = pd.to_datetime(wd.date).dt.weekday
    by_wd = wd.groupby("weekday").apply(lambda g: (g.kind == "ABSENCE_PONCTUELLE").mean(), include_groups=False)
    w("\nTaux d'absence ponctuelle par jour de semaine : " + ", ".join(f"{WEEKDAYS[i]} {v:.1%}" for i, v in by_wd.items()) + ".\n")
    hol = sorted(PUBLIC_HOLIDAYS)
    wd_dates = pd.to_datetime(wd.date)
    def near_holiday(d: date) -> str:
        if d + timedelta(days=1) in PUBLIC_HOLIDAYS or (d.weekday() == 4 and d + timedelta(days=3) in PUBLIC_HOLIDAYS):
            return "veille"
        if d - timedelta(days=1) in PUBLIC_HOLIDAYS or (d.weekday() == 0 and d - timedelta(days=3) in PUBLIC_HOLIDAYS):
            return "lendemain"
        return "autre"
    wd["near_hol"] = [near_holiday(d) for d in wd.date]
    nh = wd.groupby("near_hol").apply(lambda g: pd.Series({"jours": len(g), "taux_ponctuelle": f"{(g.kind == 'ABSENCE_PONCTUELLE').mean():.1%}"}), include_groups=False)
    w("Effet calendrier (veille / lendemain de jour férié, en tenant compte des week-ends) :\n")
    w(md_table(nh))
    w(f"\n_Remarque : {len(hol)} fériés seulement → effectifs faibles, effet à interpréter avec prudence._\n")
    fig, ax = plt.subplots(figsize=(6, 3))
    ax.bar([WEEKDAYS[i] for i in by_wd.index], by_wd.values * 100, color="#D71920")
    ax.set_ylabel("% jours ouvrés"); ax.set_title("Absences ponctuelles par jour de semaine")
    fig.tight_layout(); fig.savefig(IMG / "absence_weekday.png", dpi=120); plt.close(fig)
    monthly = wd.assign(m=pd.to_datetime(wd.date).dt.to_period("M")).groupby(["m", "kind"]).size().unstack(fill_value=0)
    monthly = monthly.div(monthly.sum(axis=1), axis=0) * 100
    fig, ax = plt.subplots(figsize=(8, 3))
    for k, c in (("ABSENCE_PONCTUELLE", "#D71920"), ("ABSENCE_LONGUE", "#F6A800")):
        if k in monthly:
            ax.plot(monthly.index.astype(str), monthly[k], marker="o", label=k, color=c)
    ax.set_ylabel("% jours ouvrés"); ax.legend(); ax.tick_params(axis="x", rotation=60)
    ax.set_title("Absences par mois"); fig.tight_layout(); fig.savefig(IMG / "absence_monthly.png", dpi=120); plt.close(fig)
    w("![Absences par jour de semaine](img/absence_weekday.png)\n\n![Absences par mois](img/absence_monthly.png)\n")

    # 7. Horaires
    w("## 7. Horaires d'arrivée\n")
    fi = first_in.reset_index()
    fi["min"] = fi.ts.dt.hour * 60 + fi.ts.dt.minute + fi.ts.dt.second / 60
    fi["period"] = [period_kind(d.date()) or "NORMAL" for d in fi.date]
    sched = []
    for emp, g in fi.groupby("user_id"):
        g = g.sort_values("date")
        first60 = g[g.date < g.date.min() + pd.Timedelta(days=60)]
        ref = round(first60["min"].median() / 15) * 15
        late = (g["min"] > ref + TOLERANCE_MIN)
        sched.append({"collaborateur": emp, "médiane 1re entrée": hm(g["min"].median()),
                      "médiane 60 premiers jours": hm(first60["min"].median()), "horaire de référence (¼ h)": hm(ref),
                      "P10–P90": f"{hm(g['min'].quantile(.1))}–{hm(g['min'].quantile(.9))}",
                      "taux de retard (réf + 10 min)": f"{late.mean():.1%}",
                      "retard moyen quand en retard": f"{(g['min'][late] - ref).mean():.0f} min"})
    w("Horaire de référence par défaut = médiane de la 1re entrée sur les 60 premiers jours de suivi, arrondie au quart d'heure (règle de la spec).\n")
    w(md_table(pd.DataFrame(sched), index=False))
    w("\nLes taux de retard élevés de certains collaborateurs (MMMMMM, WWWWWW) viennent d'une dérive de l'heure d'arrivée après les 60 premiers jours : la règle de la spec est appliquée telle quelle, l'horaire reste modifiable par RH dans l'administration.")
    per = fi.pivot_table(index="user_id", columns="period", values="min", aggfunc="median").map(hm)
    w("\nMédiane de 1re entrée par type de période :\n")
    w(md_table(per))
    w("\n**Constat** : les heures d'arrivée ne changent pas significativement pendant le Ramadan ni l'été (juillet–août). Les périodes spéciales sont donc créées dans `special_period` **sans horaire imposé par défaut** (l'horaire personnel reste la référence) ; RH peut y saisir un horaire propre qui remplacera alors l'horaire personnel.\n")
    fig, ax = plt.subplots(figsize=(8, 3.5))
    emps = sorted(fi.user_id.unique())
    ax.boxplot([fi.loc[fi.user_id == e, "min"] / 60 for e in emps], showfliers=False)
    ax.set_xticks(range(1, len(emps) + 1), emps, rotation=30)
    ax.set_ylabel("heure"); ax.set_title("Heure de 1re entrée par collaborateur")
    fig.tight_layout(); fig.savefig(IMG / "arrival_boxplot.png", dpi=120); plt.close(fig)
    w("![Heures d'arrivée](img/arrival_boxplot.png)\n")

    # 8. Heures travaillées
    w("## 8. Heures travaillées (journées complètes)\n")
    hours = []
    for (emp, d), g in bd.groupby(["user_id", "date"]):
        seq = list(zip(g.direction, g.ts))
        total, ok, i = 0.0, len(seq) % 2 == 0, 0
        while i + 1 < len(seq):
            if seq[i][0] == "IN" and seq[i + 1][0] == "OUT":
                total += (seq[i + 1][1] - seq[i][1]).total_seconds() / 60
                i += 2
            else:
                ok = False
                i += 1
        hours.append({"emp": emp, "complete": ok, "min": total})
    h = pd.DataFrame(hours)
    hs = h[h.complete].groupby("emp")["min"].agg(["median", "mean", "count"])
    hs = pd.DataFrame({"médiane": hs["median"].map(hm), "moyenne": hs["mean"].map(hm), "journées complètes": hs["count"],
                       "journées incomplètes": h[~h.complete].groupby("emp").size().reindex(hs.index, fill_value=0)})
    w(md_table(hs))
    w("\nLes journées incomplètes (pointage impair ou séquence IN/OUT incohérente) sont marquées `incomplete = true` ; leurs heures ne sont pas inventées et elles sont exclues des moyennes d'heures.\n")

    # 9. Synthèse
    w("## 9. Synthèse pour la modélisation\n")
    w(f"- Échantillon très petit : 8 collaborateurs, {tot} jours-personnes ouvrés, {int((wd.kind == 'ABSENCE_PONCTUELLE').sum())} jours d'absence ponctuelle (classe positive rare, ~{(wd.kind == 'ABSENCE_PONCTUELLE').mean():.0%}).")
    w("- Une grande part des jours sans pointage sont des absences longues (congés probables) : exclues de la cible ponctuelle.")
    w("- Horaires hétérogènes → retard calculé relativement à l'horaire de référence personnel.")
    w("- Pas de congés déclarés ni de hiérarchie dans le fichier → table `leave` vide au départ, hiérarchie fictive (seed).")
    (DOCS / "data_profile.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"OK: {DOCS / 'data_profile.md'}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "data" / "raw" / "Pointage_PFA.xls")
