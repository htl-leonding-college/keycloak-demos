import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { OidcSecurityService } from 'angular-auth-oidc-client';

const BACKEND = 'http://localhost:8080';

@Component({
  selector: 'app-root',
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly oidc = inject(OidcSecurityService);
  private readonly http = inject(HttpClient);

  /** Signal der Bibliothek — true, sobald ein gueltiges Token vorliegt. */
  protected readonly angemeldet = this.oidc.authenticated;

  protected readonly claims = signal('— noch nicht angemeldet —');
  protected readonly antwort = signal('— noch kein Aufruf —');

  constructor() {
    // Muss beim Start laufen: Nach der Rueckkehr vom Anmeldeserver steht der
    // Autorisierungscode in der Adresszeile, und erst checkAuth() tauscht ihn
    // gegen die Tokens. Ohne diesen Aufruf passiert nach dem Anmelden nichts,
    // und zwar ohne Fehlermeldung.
    this.oidc.checkAuth().subscribe(() => this.claimsZeigen());
  }

  protected anmelden(): void {
    this.oidc.authorize();
  }

  protected abmelden(): void {
    this.oidc.logoff().subscribe(() => {
      this.claims.set('— abgemeldet —');
      this.antwort.set('— noch kein Aufruf —');
    });
  }

  /**
   * Stufe 2 — der Mittelteil des Access-Tokens, entschluesselt: gar nicht.
   * Base64 ist Kodierung, keine Verschluesselung. Absichtlich das ACCESS-Token
   * und nicht userData: Das ist das Token, das ans Backend geht und dort
   * geprueft wird. Was die Bibliothek als userData anbietet, stammt aus dem
   * ID-Token und beantwortet eine andere Frage.
   */
  private claimsZeigen(): void {
    this.oidc.getAccessToken().subscribe((token) => {
      if (!token) {
        this.claims.set('— noch nicht angemeldet —');
        return;
      }
      const teil = token.split('.')[1];
      const roh = atob(teil.replace(/-/g, '+').replace(/_/g, '/'));
      const text = new TextDecoder().decode(
        Uint8Array.from(roh, (z) => z.charCodeAt(0)),
      );
      this.claims.set(JSON.stringify(JSON.parse(text), null, 2));
    });
  }

  /**
   * Stufe 3 — der Statuscode ist die Aussage, nicht der Rumpf.
   * 401 heisst "kein gueltiges Token", 403 hiesse "gueltiges Token, aber
   * fehlende Berechtigung". Deshalb steht er immer mit da.
   */
  protected rufen(pfad: string): void {
    this.antwort.set('…');
    this.http.get(BACKEND + pfad, { observe: 'response' }).subscribe({
      next: (a) => this.antwort.set(`${a.status}\n\n${JSON.stringify(a.body, null, 2)}`),
      error: (f: HttpErrorResponse) =>
        this.antwort.set(`${f.status || 'kein Netz'}\n\n${JSON.stringify(f.error, null, 2)}`),
    });
  }
}
