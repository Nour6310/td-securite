package com.example.banque.utilisateur.out;

import com.example.banque.utilisateur.Utilisateur;
import com.example.banque.utilisateur.UtilisateurRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Explique a Spring Security comment trouver un utilisateur par son login.
 * Spring compare ensuite le mot de passe recu au hash avec le PasswordEncoder.
 */
@Service
public class BanqueUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurs;

    public BanqueUserDetailsService(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        Utilisateur utilisateur = utilisateurs.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur inconnu : " + login));
        return User.withUsername(utilisateur.getLogin())
                .password(utilisateur.getMotDePasse())   // le hash BCrypt
                .roles(utilisateur.getRole().name())     // CLIENT -> ROLE_CLIENT
                .build();
    }
}
