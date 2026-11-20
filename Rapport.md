# Rapport TD Sécurité - Banque

**BEN HASSINE Nour El Houda**  
TD : sécuriser une appli de virement (Spring Boot 4, Java 25, Angular)  
20/11/2026 - dépôt : https://github.com/Nour6310/td-securite

## Ce que j'ai fait

| Partie | État | Commit |
|---|---|---|
| Constat de départ | fait | - |
| Partie 1 : Basic Auth | fait (Q1 à Q7 + CSRF) | `5bb984f` |
| Partie 2 : Angular | fait (Q1 à Q4) | `5c58884` |
| Partie 3 : rôles | fait (Q1 à Q5), en TDD côté serveur | `fb7b5d5` |
| Tests | 23 tests, tous verts | `fb7b5d5`, `9147ea1` |
| Bonus | tests `@WithMockUser` + réponses aux questions |

Je n'ai pas touché à `DonneesDemo`. J'ai supprimé `UtilisateurCourantFake` comme demandé. Dans les fichiers fournis, j'ai seulement ajouté du code, sans réécrire l'existant.

---

## Constat de départ

J'ai lancé l'API sans sécurité :

```
curl.exe localhost:8080/api/comptes
curl.exe -X POST localhost:8080/api/virements/2/valider
```

Résultat : je voyais les 3 comptes sans être connectée, et le virement 2 a été validé avec `"validePar":"alice"`, donc une cliente a validé son propre virement. En relançant la validation j'ai eu un 409 (je ne l'ai vu qu'avec `curl -v`, sinon curl n'affiche rien).

---

## Partie 1 : Basic Auth

**Q1.** Après avoir ajouté `spring-boot-starter-security`, toute l'API répond **401** avec l'en-tête `WWW-Authenticate: Basic`. Spring crée tout seul un utilisateur `user` avec un mot de passe affiché au démarrage (différent à chaque fois), et ajoute un cookie `JSESSIONID`.

**Q2.** J'ai créé `config/SecurityConfig` avec un bean `BCryptPasswordEncoder`. C'est lui qui compare le mot de passe envoyé avec le hash en base.

**Q3.** J'ai créé `BanqueUserDetailsService`, qui cherche l'utilisateur par son login et donne à Spring son hash et son rôle. Après ça, le mot de passe généré n'apparaît plus au démarrage.  
Tests avec curl : `alice:alice123` donne 200, `alice:mauvais` donne 401.  
(Au premier essai alice avait 401 alors que le mot de passe était bon : l'API n'avait pas fini de redémarrer. En relançant, 200.)

**Q4.** J'ai écrit la `SecurityFilterChain` : `/api/**` authentifié, `.httpBasic()`, et `STATELESS`. Sans identifiants : 401 et plus de cookie `JSESSIONID`.

**Piège CSRF.** Avec bruno, `POST /valider` donnait **403** alors qu'il était bien connecté. C'était la protection CSRF qui attendait un jeton. Je l'ai désactivée (`csrf.disable()`).  
Pourquoi c'est possible ici : le CSRF utilise un cookie que le navigateur envoie tout seul. Notre API n'a pas de cookie de session et c'est le front qui met lui-même l'en-tête `Authorization`, donc un autre site ne peut pas l'envoyer à notre place.

**Q5.** Avec `curl.exe -v -u alice:alice123 localhost:8080/api/me` j'ai vu l'en-tête `Authorization: Basic YWxpY2U6YWxpY2UxMjM=`. Je l'ai décodé en PowerShell :

```
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("YWxpY2U6YWxpY2UxMjM="))
```

Ça donne `alice:alice123`. **Ce n'est pas du chiffrement**, c'est juste du Base64 : pas de clé, n'importe qui peut le décoder. Le mot de passe passe donc en clair à chaque requête, il faut obligatoirement du HTTPS.

**Q6.** Avant, `/api/me` avec bruno répondait `alice`, et quand bruno validait on enregistrait `validePar: alice`. J'ai créé `UtilisateurCourantSecurity` qui lit le login dans le `SecurityContext`, et j'ai supprimé le faux. Maintenant `/api/me` répond `bruno (CONSEILLER)` et `validePar: bruno`.

**Q7.** J'ai simulé le preflight du navigateur avec curl (`-X OPTIONS` + en-tête `Origin: http://localhost:4200`) : **401** et pas d'`Access-Control-Allow-Origin`. Spring Security passait avant la config CORS de Spring MVC.  
J'ai ajouté `.cors(withDefaults())` et un bean `CorsConfigurationSource`. Après : preflight **200** avec `Access-Control-Allow-Origin: http://localhost:4200`, et un GET sans identifiants reste en 401.

---

## Partie 2 : Angular

Pour lancer le front j'ai dû installer Node.js et autoriser les scripts PowerShell pour `npm`.

**Q1.** Dans `AuthService.connecter`, j'appelle `GET /api/me` avec `Authorization: Basic ` + `btoa(login:motDePasse)`. Si le serveur répond 200, je garde l'en-tête dans une variable (en mémoire, pas de localStorage) et je remplis `utilisateur`. Si 401, la page de login affiche « Identifiant ou mot de passe incorrect ».  
(Mes premiers essais échouaient toujours : l'API n'était pas lancée.)

**Q2.** J'ai écrit `authInterceptor` : il ajoute l'en-tête à toutes les requêtes vers l'API, et sur un 401 il déconnecte et renvoie vers `/login`. Je n'ai pas eu à modifier `banque.ts`, c'est l'intérêt de l'intercepteur.

**Q3.** J'ai écrit `authGuard` sur les routes `comptes`. En allant sur `/comptes/1` sans être connectée, j'arrive sur `/login?retour=%2Fcomptes%2F1`, et après connexion je reviens directement sur FR76-0001. Il n'y a plus de 401 dans la console car la guard bloque avant l'appel à l'API.

**Q4.** Mauvais mot de passe : message d'erreur, pas de redirection.  
Rechargement (F5) en étant connectée : je reviens sur `/login`. C'est parce que les identifiants sont en mémoire, ils sont perdus quand la page se recharge. C'est voulu : en Basic Auth on stocke le vrai mot de passe, le mettre dans le localStorage serait dangereux (faille XSS), et un cookie ferait revenir le problème du CSRF.

---

## Partie 3 : les rôles

Pour cette partie j'ai travaillé en TDD comme au TD 1 : j'écris le test MockMvc, je le lance et il est rouge (la faille existe), puis je corrige et il passe au vert.

**Règle 1 / Q1 : seul un conseiller valide.**  
Test `unClientNePeutPasValiderUnVirement` : rouge avec `Status expected:<403> but was:<200>`.  
Correction : `.requestMatchers(HttpMethod.POST, "/api/virements/*/valider").hasRole("CONSEILLER")` dans la chaîne.  
Après : vert. Vérifié aussi avec curl : alice 403, bruno 200 avec `validePar: bruno`.

**Règle 2 / Q2 et Q3 : voir un compte = titulaire ou conseiller de la même agence.**  
5 tests rouges : alice voyait le compte et les virements de charles, bruno (Lyon) voyait le compte de diane (Paris), et les listes renvoyaient les 3 comptes.  
Correction : j'ai créé `AccesComptes.peutVoir()`, qui regarde l'utilisateur courant (pas seulement son rôle), et je l'utilise dans `CompteService` (un compte et la liste) et dans `VirementService.lister`.  
Après : vert. Avec curl : alice sur le compte 2 donne 404, sur le compte 1 donne 200 ; la liste d'alice a 1 compte, celle de bruno 2.

**Règle 3 : créer un virement = titulaire du compte.**  
2 tests rouges : alice pouvait faire un virement depuis le compte de charles, et bruno depuis le compte d'alice.  
Correction dans `VirementService.creer` : compte qu'on ne voit pas, 404 ; compte qu'on voit sans en être titulaire, 403.  
Après : vert.

**Q4.** Côté Angular, j'ai ajouté `roleGuard` (lit `data: { roles: [...] }` sur la route) et le bouton « Valider » ne s'affiche que pour un conseiller (`estConseiller()`). Vérifié : bruno voit le bouton, alice non.

**Q5.** J'ai remis le bouton pour tout le monde (en enlevant `estConseiller()` le temps du test), je me suis connectée en alice et j'ai cliqué : la requête `POST /api/virements/2/valider` part bien mais le serveur répond **403**, et la page affiche « La validation a été refusée ». Même chose avec curl. Ensuite j'ai remis `estConseiller()`.  
Ce qui protège, c'est le serveur (`hasRole("CONSEILLER")`). Cacher le bouton, c'est juste pour l'affichage.

---

## Tests

| Type | Fichier | Nombre |
|---|---|---|
| Unitaire (Mockito, sans Spring ni base) | `AccesComptesTest` | 6 |
| Intégration (MockMvc, vraie base, vraie sécurité) | `SecuriteIT` | 16 |
| Fourni | `BanqueApplicationTests` | 1 |

**23 tests, tous verts.**

- Partie 3 : tests écrits avant le code (TDD).
- Partie 1 : je n'ai pas écrit les tests au fur et à mesure, je les ai ajoutés après coup.
- Partie 2 (Angular) : pas de tests automatiques, vérifié à la main dans le navigateur.
- `AccesComptesTest` était vert directement, alors j'ai cassé `AccesComptes` exprès pour vérifier qu'il échoue : les tests `unClientNeVoitPasLeCompteDUnAutreClient` et `unConseillerNeVoitPasLesComptesDUneAutreAgence` sont devenus rouges, puis j'ai remis le code.

**Problème rencontré** : avec `spring-security-test`, mes tests `@WithMockUser` recevaient 401. En Spring Boot 4 il faut `spring-boot-starter-security-test` pour que MockMvc récupère l'utilisateur simulé.

---

## Bonus

**404 plutôt que 403 sur le compte d'un autre ?**  
Un 403 veut dire « ce compte existe mais il n'est pas à toi ». En essayant les numéros un par un, on pourrait savoir quels comptes existent. Avec un 404 on n'apprend rien.

**BCrypt plutôt que SHA-256 ?**  
SHA-256 est très rapide, donc si la base est volée on peut tester énormément de mots de passe par seconde. BCrypt est lent exprès (coût réglable) et ajoute un sel, donc deux mots de passe identiques n'ont pas le même hash.  
Le problème en Basic Auth : le mot de passe est renvoyé à chaque requête, donc BCrypt est recalculé à chaque fois. Je l'ai mesuré avec `curl -w "%{time_total}"` : `/api/me` sans identifiants **0,026 s**, avec `alice:alice123` **0,200 s**, à peu près 8 fois plus lent, et ça à chaque requête.

**Passer en JWT ou en session ?**  
Dans les deux cas on ne vérifie le mot de passe qu'une fois, à la connexion.
- JWT : le serveur renvoie un jeton signé, le front l'envoie dans `Authorization: Bearer ...`, et on remplace `.httpBasic` par la vérification du jeton. On reste stateless, pas besoin de CSRF, mais il faut gérer l'expiration du jeton.
- Session : on enlève `STATELESS`, le navigateur renvoie le cookie `JSESSIONID` tout seul. Du coup il faut **remettre la protection CSRF**. Avantage : on reste connecté après un F5.

