import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API } from '../api/banque';

export interface Utilisateur {
  readonly login: string;
  readonly roles: readonly string[];
}

/**
 * Garde l'utilisateur connecte et ses identifiants.
 * Les identifiants restent EN MEMOIRE (pas de localStorage, pas de cookie) :
 * ils disparaissent quand on recharge la page.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly utilisateur = signal<Utilisateur | null>(null);

  /** La valeur de l'en-tete Authorization ("Basic ..."), null si personne n'est connecte. */
  private enTete: string | null = null;

  /** Utilise par l'intercepteur pour ajouter l'en-tete aux requetes vers l'API. */
  get authorization(): string | null {
    return this.enTete;
  }

  /**
   * Verifie les identifiants aupres du serveur (GET /api/me avec l'en-tete
   * Basic) et memorise l'utilisateur. Echoue (throw) si le serveur repond 401.
   */
  async connecter(login: string, motDePasse: string): Promise<void> {
    // btoa = encodage Base64 (ce n'est PAS du chiffrement)
    const enTete = 'Basic ' + btoa(`${login}:${motDePasse}`);
    const moi = await firstValueFrom(
      this.http.get<Utilisateur>(`${API}/me`, { headers: { Authorization: enTete } }),
    );
    // on ne retient les identifiants qu'une fois verifies par le serveur
    this.enTete = enTete;
    this.utilisateur.set(moi);
  }

  deconnecter(): void {
    this.enTete = null;
    this.utilisateur.set(null);
  }
}
