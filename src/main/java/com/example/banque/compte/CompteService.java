package com.example.banque.compte;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class CompteService {

    private final CompteRepository comptes;
    private final AccesComptes acces;

    public CompteService(CompteRepository comptes, AccesComptes acces) {
        this.comptes = comptes;
        this.acces = acces;
    }

    /** Seulement les comptes que l'utilisateur courant a le droit de voir. */
    public List<CompteDto> lister() {
        return comptes.findAll().stream()
                .filter(acces::peutVoir)
                .map(CompteDto::de)
                .toList();
    }

    /** Un compte interdit est traite comme absent (404) : on ne revele pas qu'il existe. */
    public CompteDto trouver(Long id) {
        return comptes.findById(id)
                .filter(acces::peutVoir)
                .map(CompteDto::de)
                .orElseThrow(() -> new NoSuchElementException("Compte " + id + " introuvable"));
    }
}
