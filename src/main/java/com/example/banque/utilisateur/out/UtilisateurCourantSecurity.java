package com.example.banque.utilisateur.out;

import com.example.banque.utilisateur.Utilisateur;
import com.example.banque.utilisateur.UtilisateurRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * L'utilisateur courant = celui que Spring Security a authentifie pour la requete en cours.
 * Spring le range dans le SecurityContext apres avoir verifie l'en-tete Basic.
 */
@Component
public class UtilisateurCourantSecurity implements UtilisateurCourant {

    private final UtilisateurRepository utilisateurs;

    public UtilisateurCourantSecurity(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    public Optional<Utilisateur> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // personne de connecte (ou utilisateur anonyme) : vide
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        // getName() = le login envoye dans l'en-tete Basic
        return utilisateurs.findByLogin(authentication.getName());
    }
}
