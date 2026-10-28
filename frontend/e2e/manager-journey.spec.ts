import { expect, test } from '@playwright/test';

const SHOTS = '../docs/screenshots';

/** Parcours manager : connexion → vue d'ensemble → fiche collaborateur → risques (captures desktop et mobile). */
test('un manager consulte son équipe, une fiche puis les risques', async ({ page }, info) => {
  const shot = async (name: string) => {
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: `${SHOTS}/${info.project.name}-${name}.png`, fullPage: true });
  };
  const isMobile = info.project.name === 'mobile';

  // 1. Connexion
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: "Tableau de bord d'assiduité" })).toBeVisible();
  await shot('01-connexion');
  await page.getByLabel('Identifiant').fill('manager.a');
  await page.getByLabel('Mot de passe', { exact: true }).fill('Demo@2025');
  await page.getByRole('button', { name: 'Se connecter' }).click();

  // 2. Vue d'ensemble : périmètre limité à l'équipe du manager
  await expect(page).toHaveURL(/\/overview$/);
  await expect(page.getByRole('heading', { name: "Vue d'ensemble" })).toBeVisible();
  await expect(page.getByText('Équipe Études A (fictive)').first()).toBeVisible();
  await expect(page.getByText('Taux de présence').first()).toBeVisible();
  await expect(page.locator('app-kpi-card .skeleton')).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(800); // fin des animations ECharts
  await shot('02-vue-ensemble');

  // 3. Collaborateurs : uniquement l'équipe A
  if (isMobile) {
    await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
  }
  await page.getByRole('link', { name: 'Collaborateurs' }).click();
  await expect(page.getByText('4 collaborateur(s)')).toBeVisible();
  await expect(page.getByText('Collaborateur V')).toHaveCount(0); // équipe B : jamais visible
  await shot('03-collaborateurs');

  // 4. Fiche collaborateur
  await page.getByRole('row', { name: /Collaborateur Y/ }).click();
  await expect(page.getByRole('heading', { name: /Collaborateur Y/ })).toBeVisible();
  await expect(page.getByRole('grid', { name: 'Jours du mois' })).toBeVisible();
  await expect(page.getByText('Facteurs associés à l\'estimation').first()).toBeVisible();
  await expect(page.getByText('Estimation statistique, non une certitude').first()).toBeVisible();
  await page.getByRole('gridcell', { name: /15 mai 2025/ }).click();
  await expect(page.getByRole('heading', { name: /Pointages du jeudi 15 mai 2025/ })).toBeVisible();
  await expect(page.locator('.punches li').first()).toBeVisible();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(800);
  await shot('04-fiche-collaborateur');

  // 5. Risques & prédictions
  if (isMobile) {
    await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
  }
  await page.getByRole('link', { name: 'Risques & prédictions' }).click();
  await expect(page.getByRole('heading', { name: 'Risques & prédictions' })).toBeVisible();
  await page.getByRole('radio', { name: 'Tous' }).click();
  await expect(page.locator('article.risk')).toHaveCount(4);
  await expect(page.getByText('Estimation statistique, non une certitude').first()).toBeVisible();
  await shot('05-risques');
});

test('la page Administration est refusée à un manager', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Identifiant').fill('manager.b');
  await page.getByLabel('Mot de passe', { exact: true }).fill('Demo@2025');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/overview$/);
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/overview$/);
});
