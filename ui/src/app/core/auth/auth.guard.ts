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

/**
 * Laisse entrer seulement si l'utilisateur a l'un des roles de la route : data: { roles: [...] }.
 * Ce n'est qu'un confort d'affichage : c'est le serveur qui protege vraiment.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const rolesAutorises = (route.data['roles'] as string[] | undefined) ?? [];
  const utilisateur = auth.utilisateur();
  if (utilisateur && (rolesAutorises.length === 0 || rolesAutorises.some((r) => utilisateur.roles.includes(r)))) {
    return true;
  }
  return inject(Router).createUrlTree(['/login']);
};
