package com.example.banque;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * Tests de securite de l'API (MockMvc + vraie base H2 + vraie chaine Spring Security).
 * Donnees de demo : alice (CLIENT, compte 1 FR76-0001, Lyon), charles (CLIENT, compte 2 FR76-0002, Lyon),
 * diane (CLIENT, compte 3 FR76-0003, Paris), bruno (CONSEILLER, agence Lyon Part-Dieu).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecuriteIT {

    @Autowired
    MockMvc mockMvc;

    // ---------- Partie 1 : Basic Auth ----------

    @Test
    void sansIdentifiantsLApiRepond401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comptes"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void avecLeBonMotDePasseOnEstReconnu() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/me").with(httpBasic("alice", "alice123")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.login").value("alice"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.roles[0]").value("CLIENT"));
    }

    @Test
    void avecUnMauvaisMotDePasseOnObtient401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/me").with(httpBasic("alice", "faux")))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void lePreflightCorsDuFrontPasseSansIdentifiants() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.options("/api/me")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    // ---------- Partie 3, regle 1 : seul un conseiller valide ----------

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void unClientNePeutPasValiderUnVirement() throws Exception {
        // virement 2 : sur le compte d'alice, EN_ATTENTE
        mockMvc.perform(MockMvcRequestBuilders.post("/api/virements/2/valider"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(username = "bruno", roles = "CONSEILLER")
    void unConseillerValideUnVirementEtOnGardeSonNom() throws Exception {
        // virement 3 : sur le compte de charles, EN_ATTENTE
        mockMvc.perform(MockMvcRequestBuilders.post("/api/virements/3/valider"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.statut").value("VALIDE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.validePar").value("bruno"));
    }

    // ---------- Partie 3, regle 2 : voir un compte = titulaire ou conseiller de la meme agence ----------

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void unClientVoitSonPropreCompte() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comptes/1"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.numero").value("FR76-0001"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void unClientNeVoitPasLeCompteDUnAutreClient() throws Exception {
        // compte 2 = charles : alice ne doit meme pas savoir qu'il existe
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comptes/2"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void unClientNeVoitPasLesVirementsDUnAutreClient() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/virements").param("compte", "2"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @WithMockUser(username = "bruno", roles = "CONSEILLER")
    void unConseillerVoitUnCompteDeSonAgence() throws Exception {
        // compte 2 = charles, agence Lyon Part-Dieu comme bruno
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comptes/2"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(username = "bruno", roles = "CONSEILLER")
    void unConseillerNeVoitPasUnCompteDUneAutreAgence() throws Exception {
        // compte 3 = diane, agence Paris Opera
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comptes/3"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void laListeDUnClientNeContientQueSesComptes() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comptes"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].numero").value("FR76-0001"));
    }

    @Test
    @WithMockUser(username = "bruno", roles = "CONSEILLER")
    void laListeDUnConseillerNeContientQueLesComptesDeSonAgence() throws Exception {
        // Lyon Part-Dieu : alice et charles, pas diane (Paris)
        mockMvc.perform(MockMvcRequestBuilders.get("/api/comptes"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2));
    }
    
    // ---------- Partie 3, regle 3 : seul le titulaire cree un virement ----------

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void unClientCreeUnVirementDepuisSonCompte() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/virements").content("""
                        { "compteId": 1, "ibanDestinataire": "FR76-9999", "montant": 1000 }
                        """).contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.statut").value("EN_ATTENTE"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void unClientNePeutPasCreerUnVirementDepuisLeCompteDUnAutre() throws Exception {
        // compte 2 = charles : alice ne le voit pas, donc 404
        mockMvc.perform(MockMvcRequestBuilders.post("/api/virements").content("""
                        { "compteId": 2, "ibanDestinataire": "FR76-9999", "montant": 1000 }
                        """).contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @WithMockUser(username = "bruno", roles = "CONSEILLER")
    void unConseillerNePeutPasCreerDeVirement() throws Exception {
        // bruno voit le compte 1 (meme agence) mais n'en est pas le titulaire : 403
        mockMvc.perform(MockMvcRequestBuilders.post("/api/virements").content("""
                        { "compteId": 1, "ibanDestinataire": "FR76-9999", "montant": 1000 }
                        """).contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }
}
