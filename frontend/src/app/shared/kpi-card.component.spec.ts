import { TestBed } from '@angular/core/testing';
import { KpiCardComponent } from './kpi-card.component';

describe('KpiCardComponent', () => {
  function render(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(KpiCardComponent);
    for (const [k, v] of Object.entries(inputs)) {
      fixture.componentRef.setInput(k, v);
    }
    fixture.detectChanges();
    return fixture;
  }

  it('affiche la valeur et une variation favorable quand le taux de présence augmente', () => {
    const f = render({ label: 'Taux de présence', value: '91,0 %', delta: 0.025, deltaKind: 'points', goodWhen: 'up' });
    const el: HTMLElement = f.nativeElement;
    expect(el.textContent).toContain('91,0 %');
    expect(el.querySelector('.delta')?.classList).toContain('good');
    expect(el.querySelector('.delta')?.textContent).toContain('+2,5 pt');
  });

  it('marque comme défavorable une hausse des absences (jamais la couleur seule : flèche + libellé ARIA)', () => {
    const f = render({ label: 'Absences', value: '7', delta: 2, goodWhen: 'down' });
    const delta = f.nativeElement.querySelector('.delta') as HTMLElement;
    expect(delta.classList).toContain('bad');
    expect(delta.getAttribute('aria-label')).toContain('défavorable');
    expect(delta.textContent).toContain('arrow_upward');
  });

  it('affiche un squelette pendant le chargement et « pas de comparaison » sans période précédente', () => {
    expect(render({ label: 'X', loading: true }).nativeElement.querySelector('.skeleton')).not.toBeNull();
    expect(render({ label: 'X', value: '3', delta: null }).nativeElement.textContent).toContain('pas de comparaison');
  });
});
