import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Laisse entrer seulement un utilisateur connecte.
 * Sinon : redirection vers /login?retour=<url demandee>, pour y revenir apres la connexion.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.utilisateur()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { retour: state.url } });
};
