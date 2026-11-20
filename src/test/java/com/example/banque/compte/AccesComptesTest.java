package com.example.banque.compte;

import com.example.banque.utilisateur.Adresse;
import com.example.banque.utilisateur.Client;
import com.example.banque.utilisateur.Conseiller;
import com.example.banque.utilisateur.ConseillerRepository;
import com.example.banque.utilisateur.Role;
import com.example.banque.utilisateur.Utilisateur;
import com.example.banque.utilisateur.out.UtilisateurCourant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

/**
 * Test unitaire : tout est mocke (Mockito), sans Spring ni base.
 * On teste la decision "qui a le droit de voir quel compte".
 */
class AccesComptesTest {

    AccesComptes acces;

    UtilisateurCourant utilisateurCourant;
    ConseillerRepository conseillers;

    Agence lyon;
    Agence paris;
    Utilisateur alice;
    Utilisateur bruno;
    Compte compteAlice;
    Compte compteCharles;
    Compte compteDiane;

    @BeforeEach
    void setUp() {
        utilisateurCourant = Mockito.mock(UtilisateurCourant.class);
        conseillers = Mockito.mock(ConseillerRepository.class);
        acces = new AccesComptes(utilisateurCourant, conseillers);

        // les agences n'ont pas de setId : on force l'id comme le ferait la base
        lyon = new Agence("Lyon Part-Dieu");
        ReflectionTestUtils.setField(lyon, "id", 1L);
        paris = new Agence("Paris Opera");
        ReflectionTestUtils.setField(paris, "id", 2L);

        var adresse = new Adresse("rue", "ville", "00000");
        alice = new Utilisateur("alice", "hash", Role.CLIENT);
        var charles = new Utilisateur("charles", "hash", Role.CLIENT);
        var diane = new Utilisateur("diane", "hash", Role.CLIENT);
        bruno = new Utilisateur("bruno", "hash", Role.CONSEILLER);

        compteAlice = new Compte("FR76-0001", lyon, new Client(alice, "Alice", adresse));
        compteCharles = new Compte("FR76-0002", lyon, new Client(charles, "Charles", adresse));
        compteDiane = new Compte("FR76-0003", paris, new Client(diane, "Diane", adresse));

        Mockito.when(conseillers.findByUtilisateurLogin("bruno"))
                .thenReturn(Optional.of(new Conseiller(bruno, "Bruno", lyon)));
    }

    @Test
    void unClientVoitSonPropreCompte() {
        Mockito.when(utilisateurCourant.get()).thenReturn(Optional.of(alice));
        Assertions.assertThat(acces.peutVoir(compteAlice)).isTrue();
    }

    @Test
    void unClientNeVoitPasLeCompteDUnAutreClient() {
        Mockito.when(utilisateurCourant.get()).thenReturn(Optional.of(alice));
        Assertions.assertThat(acces.peutVoir(compteCharles)).isFalse();
    }

    @Test
    void unConseillerVoitLesComptesDeSonAgence() {
        Mockito.when(utilisateurCourant.get()).thenReturn(Optional.of(bruno));
        Assertions.assertThat(acces.peutVoir(compteAlice)).isTrue();
        Assertions.assertThat(acces.peutVoir(compteCharles)).isTrue();
    }

    @Test
    void unConseillerNeVoitPasLesComptesDUneAutreAgence() {
        Mockito.when(utilisateurCourant.get()).thenReturn(Optional.of(bruno));
        Assertions.assertThat(acces.peutVoir(compteDiane)).isFalse();
    }

    @Test
    void personneNEstConnecteDoncPersonneNeVoit() {
        Mockito.when(utilisateurCourant.get()).thenReturn(Optional.empty());
        Assertions.assertThat(acces.peutVoir(compteAlice)).isFalse();
    }

    @Test
    void seulLeTitulaireDuComptePeutFaireUnVirement() {
        Mockito.when(utilisateurCourant.get()).thenReturn(Optional.of(alice));
        Assertions.assertThat(acces.estTitulaireCourant(compteAlice)).isTrue();

        Mockito.when(utilisateurCourant.get()).thenReturn(Optional.of(bruno));
        Assertions.assertThat(acces.estTitulaireCourant(compteAlice)).isFalse();
    }
}
