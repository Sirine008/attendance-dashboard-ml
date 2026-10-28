import { TestBed } from '@angular/core/testing';
import { RiskBadgeComponent } from './risk-badge.component';

describe('RiskBadgeComponent', () => {
  function render(inputs: Record<string, unknown>): HTMLElement {
    const fixture = TestBed.createComponent(RiskBadgeComponent);
    for (const [k, v] of Object.entries(inputs)) {
      fixture.componentRef.setInput(k, v);
    }
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('combine icône et libellé pour le niveau élevé, avec une probabilité arrondie', () => {
    const el = render({ level: 'ELEVE', probability: 0.3412, showProbability: true });
    expect(el.textContent).toContain('Élevé');
    expect(el.querySelector('mat-icon')?.textContent).toContain('north');
    expect(el.textContent).toContain('≈ 35 %');
  });

  it('affiche « historique insuffisant » sans probabilité', () => {
    const el = render({ status: 'INSUFFICIENT_HISTORY', probability: 0.5 });
    expect(el.textContent).toContain('Historique insuffisant');
    expect(el.textContent).not.toContain('%');
  });
});
