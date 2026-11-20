package com.example.banque.compte;

import com.example.banque.utilisateur.ConseillerRepository;
import com.example.banque.utilisateur.Role;
import com.example.banque.utilisateur.Utilisateur;
import com.example.banque.utilisateur.out.UtilisateurCourant;
import org.springframework.stereotype.Component;

/**
 * Qui a le droit de voir quel compte ?
 * - un CLIENT : seulement les comptes dont il est le titulaire ;
 * - un CONSEILLER : les comptes de SON agence.
 * On regarde l'utilisateur courant, pas seulement son role.
 */
@Component
public class AccesComptes {

    private final UtilisateurCourant utilisateurCourant;
    private final ConseillerRepository conseillers;

    public AccesComptes(UtilisateurCourant utilisateurCourant, ConseillerRepository conseillers) {
        this.utilisateurCourant = utilisateurCourant;
        this.conseillers = conseillers;
    }

    public boolean peutVoir(Compte compte) {
        Utilisateur utilisateur = utilisateurCourant.get().orElse(null);
        if (utilisateur == null) {
            return false;
        }
        if (utilisateur.getRole() == Role.CLIENT) {
            return estTitulaire(compte, utilisateur);
        }
        if (utilisateur.getRole() == Role.CONSEILLER) {
            return conseillers.findByUtilisateurLogin(utilisateur.getLogin())
                    .map(conseiller -> conseiller.getAgence().getId().equals(compte.getAgence().getId()))
                    .orElse(false);
        }
        return false;
    }
    /** Regle 3 : seul le titulaire du compte (un client) peut faire un virement depuis ce compte. */
    public boolean estTitulaireCourant(Compte compte) {
        return utilisateurCourant.get()
                .map(utilisateur -> utilisateur.getRole() == Role.CLIENT && estTitulaire(compte, utilisateur))
                .orElse(false);
    }
    private boolean estTitulaire(Compte compte, Utilisateur utilisateur) {
        return compte.getTitulaire().getUtilisateur().getLogin().equals(utilisateur.getLogin());
    }
}
