import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { LogLevel, authInterceptor, provideAuth } from 'angular-auth-oidc-client';

/**
 * Dieselbe Anmeldung wie im Vanilla-Frontend, nur von einer Bibliothek
 * gefahren. Wer beide Dateien nebeneinanderlegt, sieht, was die Bibliothek
 * abnimmt — und was sie NICHT abnimmt: Der Server prueft weiterhin PKCE,
 * Weiterleitungsziel und Zielgruppe, und das Backend prueft die Signatur.
 * Eine Bibliothek im Browser ist Bequemlichkeit, keine Sicherheitsgrenze.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    // Der Interceptor haengt das Token an — aber nur an Adressen aus
    // "secureRoutes". Ohne diese Liste ginge das Token an jeden Host, den die
    // App jemals aufruft; das ist genau der Weg, auf dem Tokens abfliessen.
    provideHttpClient(withInterceptors([authInterceptor()])),

    provideAuth({
      config: {
        authority: 'https://auth-dev.htl-leonding.ac.at/realms/htl-leonding',
        clientId: 'demo-frontend',

        // Muss EXAKT einem Eintrag der Redirect-URI-Liste des Clients
        // entsprechen. Am Client stehen 8000, 4200 und 5173 — deshalb laeuft
        // diese App auf 4200 und nirgends sonst.
        redirectUrl: window.location.origin,
        postLogoutRedirectUri: window.location.origin,

        // "code" ist Authorization Code Flow. PKCE macht die Bibliothek von
        // sich aus; "response_type=token" (implizit) und
        // "code_challenge_method=plain" lehnt der Realm ohnehin ab — das
        // entscheiden die Client Policies auf dem Server, nicht der Client.
        responseType: 'code',
        scope: 'openid profile email',

        // Das Access-Token lebt fuenf Minuten. Ohne Erneuerung faellt die
        // Demo mitten im Unterricht in 401. Im Vanilla-Frontend stehen dafuer
        // dreissig Zeilen offen im Quelltext; hier sind es diese drei Zeilen.
        silentRenew: true,
        useRefreshToken: true,
        renewTimeBeforeTokenExpiresInSeconds: 30,

        secureRoutes: ['http://localhost:8080/'],
        logLevel: LogLevel.Warn,
      },
    }),
  ],
};
