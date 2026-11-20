package com.example.banque.virement;
import com.example.banque.compte.AccesComptes;
import org.springframework.security.access.AccessDeniedException;

import com.example.banque.compte.Compte;
import com.example.banque.compte.CompteRepository;
import com.example.banque.utilisateur.out.UtilisateurCourant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class VirementService {

    private static final int TAILLE_PAGE = 20;

    private final VirementRepository virements;
    private final CompteRepository comptes;
    private final UtilisateurCourant utilisateurCourant;
    private final AccesComptes acces;

    public VirementService(VirementRepository virements, CompteRepository comptes, UtilisateurCourant utilisateurCourant, AccesComptes acces) {
        this.virements = virements;
        this.comptes = comptes;
        this.utilisateurCourant = utilisateurCourant;
        this.acces = acces;
    }

    @Transactional(readOnly = true)
    public List<VirementDto> lister(Long compteId, int page) {
        // regle 2 : on ne voit les virements que d'un compte qu'on a le droit de voir (sinon 404)
        comptes.findById(compteId)
                .filter(acces::peutVoir)
                .orElseThrow(() -> new NoSuchElementException("Compte " + compteId + " introuvable"));
        var pageable = PageRequest.of(page, TAILLE_PAGE, Sort.by(Sort.Direction.DESC, "id"));
        return virements.findByCompteId(compteId, pageable).stream().map(VirementDto::de).toList();
    }

    public VirementDto creer(NouveauVirement demande) {
        Compte compte = comptes.findById(demande.compteId())
                // un compte qu'on ne peut pas voir est "introuvable" (404)
                .filter(acces::peutVoir)
                .orElseThrow(() -> new NoSuchElementException("Compte " + demande.compteId() + " introuvable"));
        // regle 3 : on le voit, mais seul le titulaire peut le debiter (403)
        if (!acces.estTitulaireCourant(compte)) {
            throw new AccessDeniedException("Seul le titulaire du compte peut faire un virement");
        }
        var virement = new Virement(compte, demande.ibanDestinataire(), demande.montant());
        return VirementDto.de(virements.save(virement));
    }

    public VirementDto valider(Long id) {
        Virement virement = virements.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Virement " + id + " introuvable"));
        String login = utilisateurCourant.get()
                .orElseThrow(() -> new IllegalStateException("Personne n'est connecté"))
                .getLogin();
        virement.valider(login);
        return VirementDto.de(virement);
    }
}
