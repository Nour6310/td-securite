import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API } from '../api/banque';
import { AuthService } from './auth.service';

/**
 * Ajoute l'en-tete Authorization (Basic) aux requetes vers l'API.
 * Sur un 401 : deconnecte et renvoie vers /login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // les requetes qui ne vont pas vers notre API ne sont pas touchees
  if (!req.url.startsWith(API)) {
    return next(req);
  }

  const auth = inject(AuthService);
  const router = inject(Router);

  // la tentative de connexion porte deja son propre en-tete : on ne le remplace pas
  const tentativeDeConnexion = req.headers.has('Authorization');
  const enTete = auth.authorization;
  const requete =
    enTete && !tentativeDeConnexion ? req.clone({ setHeaders: { Authorization: enTete } }) : req;

  return next(requete).pipe(
    catchError((erreur: unknown) => {
      // 401 sur une requete normale : identifiants absents ou plus valables
      if (erreur instanceof HttpErrorResponse && erreur.status === 401 && !tentativeDeConnexion) {
        auth.deconnecter();
        void router.navigateByUrl('/login');
      }
      return throwError(() => erreur);
    }),
  );
};
