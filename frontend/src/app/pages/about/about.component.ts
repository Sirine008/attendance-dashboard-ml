import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-about',
  imports: [MatIconModule],
  template: `
    <div class="page narrow">
      <header class="page-header">
        <div>
          <h1>À propos du modèle</h1>
          <p>Comment sont produites les estimations de risque d'absence, et comment (ne pas) les utiliser.</p>
        </div>
      </header>

      <section class="card usage" aria-labelledby="usage">
        <h2 id="usage"><mat-icon aria-hidden="true">handshake</mat-icon>Usage autorisé</h2>
        <p><strong>Ce module est un outil d'aide à l'anticipation pour l'organisation du travail</strong> (planifier une réunion, prévoir un
          relais, assurer la continuité de service). <strong>Ce n'est pas un outil d'évaluation ni de sanction.</strong></p>
        <ul>
          <li>Interdit : noter, comparer ou classer des personnes, motiver une décision RH, un entretien disciplinaire ou une rémunération.</li>
          <li>Une estimation est une <em>probabilité statistique</em>, jamais une certitude ni un reproche.</li>
          <li>Les facteurs affichés sont des facteurs <strong>associés</strong> à l'estimation, jamais des <em>causes</em>.</li>
          <li>Aucune estimation n'est affichée pour un collaborateur ayant moins de 30 jours ouvrés d'historique.</li>
        </ul>
      </section>

      <section class="card" aria-labelledby="method">
        <h2 id="method"><mat-icon aria-hidden="true">schema</mat-icon>La méthode, simplement</h2>
        <ol>
          <li><strong>Des pointages aux journées.</strong> Les passages aux portes du bâtiment sont regroupés par jour : présence, heure d'arrivée,
            retard par rapport à l'horaire habituel de la personne, heures de présence. Un jour ouvré sans aucun pointage est une absence ;
            3 jours consécutifs ou plus sont considérés comme un congé probable et traités à part.</li>
          <li><strong>Ce que l'on cherche à estimer.</strong> La probabilité d'une absence ponctuelle (1 ou 2 jours) le prochain jour ouvré,
            et au moins une sur la semaine suivante.</li>
          <li><strong>Les indices utilisés.</strong> Uniquement le passé : absences récentes, habitudes selon le jour de la semaine,
            retards, heures de présence, calendrier (veille ou lendemain de férié, ponts, Ramadan, été).</li>
          <li><strong>Comparer avant de faire confiance.</strong> Deux règles simples servent de référence (taux d'absence habituel de la
            personne, et ce taux pour le jour de la semaine). Des modèles d'apprentissage (régression logistique, gradient boosting) sont
            entraînés sur 2024 et testés sur 2025. <strong>Un modèle qui ne fait pas mieux que la règle simple n'est pas utilisé.</strong></li>
          <li><strong>Des probabilités honnêtes.</strong> Les probabilités sont calibrées (« 20 % » doit correspondre à environ 1 cas sur 5)
            puis traduites en trois niveaux : faible, modéré, élevé.</li>
        </ol>
        <div class="notice">
          <mat-icon aria-hidden="true">fact_check</mat-icon>
          <span>Résultat actuel : avec seulement 8 collaborateurs et environ 130 jours d'absence ponctuelle, aucun modèle appris ne bat
            la règle « taux habituel pour ce jour de semaine » en validation. C'est donc cette règle, transparente, qui produit les
            estimations. Le détail chiffré est disponible dans la page « Santé du modèle » (RH / Admin) et dans la model card du projet.</span>
        </div>
      </section>

      <section class="card" aria-labelledby="limits">
        <h2 id="limits"><mat-icon aria-hidden="true">report</mat-icon>Limites connues</h2>
        <ul>
          <li>Très peu de données : les performances varient fortement d'un mois à l'autre.</li>
          <li>Pas de données de congés dans le fichier source : les absences « longues » sont une déduction ; certains congés d'un ou deux
            jours sont comptés comme absences ponctuelles tant qu'ils ne sont pas déclarés dans l'administration.</li>
          <li>La hiérarchie, les équipes et la plupart des dates d'embauche sont fictives (démonstration).</li>
          <li>Les données s'arrêtent en mai 2025 : les estimations portent sur les jours suivant la dernière journée importée.</li>
          <li>Les collaborateurs marqués « Synthétique » sont générés pour la démonstration et n'entrent pas dans les métriques officielles.</li>
        </ul>
      </section>

      <section class="card" aria-labelledby="privacy">
        <h2 id="privacy"><mat-icon aria-hidden="true">shield_person</mat-icon>Protection des données personnelles</h2>
        <p>Conformément à la <strong>loi organique tunisienne n° 2004-63</strong> du 27 juillet 2004 portant sur la protection des données à
          caractère personnel :</p>
        <ul>
          <li><strong>Minimisation</strong> : seuls les horodatages de passage aux portes sont utilisés ; aucune donnée de santé ni motif d'absence.</li>
          <li><strong>Accès restreint par rôle</strong> : un manager ne voit que son équipe ; l'administration est réservée aux RH et administrateurs.</li>
          <li><strong>Traçabilité</strong> : chaque consultation de fiche individuelle est enregistrée dans le journal d'audit.</li>
        </ul>
      </section>
    </div>
  `,
  styles: `
    .narrow { max-width: 920px; }
    h2 { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; font-size: 1.05rem; font-weight: 650; }
    h2 mat-icon { color: var(--brand-red); }
    .usage { border-left: 4px solid var(--brand-yellow); }
    p, li { line-height: 1.55; }
    ul, ol { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 6px; }
    .notice { margin-top: 14px; }
  `,
})
export class AboutComponent {}
