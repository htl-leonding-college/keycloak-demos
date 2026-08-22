package at.htlleonding;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Die Stufen 1 bis 4 als je ein Endpunkt.
 *
 * Der Aufbau ist Absicht: Jeder Endpunkt zeigt genau einen Schritt, und der
 * Unterschied zum vorigen ist eine Zeile oder eine Annotation.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

    /** Praefix, an dem eine Klassengruppe erkennbar ist. Siehe D33. */
    private static final String KLASSEN_DACH = "/klassen/";

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    // -------------------------------------------------------------------------
    // Stufe 1/2 - ohne Anmeldung erreichbar.
    // Dient als Gegenprobe: Wer hier 200 bekommt und bei /geschuetzt 401,
    // hat einen laufenden Server und ein fehlendes Token - nicht umgekehrt.
    // -------------------------------------------------------------------------
    @GET
    @Path("/oeffentlich")
    public Map<String, Object> oeffentlich() {
        return Map.of(
                "nachricht", "Dieser Endpunkt braucht kein Token.",
                "hinweis", "Wenn dieser Aufruf gelingt und /api/geschuetzt mit 401 "
                         + "antwortet, funktioniert der Server und es fehlt das Token.");
    }

    // -------------------------------------------------------------------------
    // Stufe 3 - geschuetzter Endpunkt.
    //
    // @Authenticated genuegt. Die eigentliche Arbeit - Signatur pruefen,
    // Ablauf pruefen, Aussteller pruefen, Zielgruppe pruefen - erledigt die
    // OIDC-Erweiterung anhand von application.properties. Wer das von Hand
    // nachbaut, macht genau die Fehler, gegen die diese Demo argumentiert.
    // -------------------------------------------------------------------------
    @GET
    @Path("/geschuetzt")
    @Authenticated
    public Map<String, Object> geschuetzt() {
        return Map.of(
                "angemeldetAls", jwt.getName(),
                "aussteller", jwt.getIssuer(),
                "zielgruppe", jwt.getAudience(),
                // azp = authorized party: WELCHE Anwendung hat das Token geholt.
                // aud sagt, fuer wen es gedacht ist; azp, wer es besorgt hat.
                // Bei einem gestohlenen Token aus einem anderen Projekt weicht
                // azp ab, auch wenn aud passt.
                "ausgestelltFuer", jwt.getClaim("azp"),
                "laeuftAbUm", jwt.getExpirationTime(),
                "rollen", identity.getRoles(),
                "gruppen", gruppen());
    }

    // -------------------------------------------------------------------------
    // Stufe 4a - Autorisierung ueber die Klassengruppe.
    //
    // Die Klasse steht im Token, weil der Client Scope "klassen" sie als
    // groups-Claim mitgibt (Task 7.8). Kein Abruf beim Server noetig - das ist
    // der Punkt: Wer die angemeldete Person autorisieren will, braucht keine
    // Benutzerliste und damit kein Dienstkonto.
    // -------------------------------------------------------------------------
    @GET
    @Path("/meine-klasse")
    @Authenticated
    public Map<String, Object> meineKlasse() {
        List<String> klassen = gruppen().stream()
                .filter(g -> g.startsWith(KLASSEN_DACH))
                .toList();

        if (klassen.isEmpty()) {
            // KEIN Fehler. Das ist normativ so verlangt: Zwischen Import und
            // naechstem Gruppenabgleich hat eine Person keine Klasse, und
            // lokale Konten haben nie eine. Eine Anwendung, die hier abbricht,
            // faellt am ersten Schultag um.
            return Map.of(
                    "klasse", "",
                    "hinweis", "Dieses Konto gehoert keiner Klassengruppe an. "
                             + "Das ist ein gueltiger Zustand: lokale Konten und "
                             + "Lehrkraefte haben keine, neu importierte noch nicht.");
        }

        // Der volle Pfad ist /klassen/<ABTEILUNG>/<KLASSE>.
        String[] teile = klassen.get(0).substring(KLASSEN_DACH.length()).split("/");
        return Map.of(
                "abteilung", teile.length > 0 ? teile[0] : "",
                "klasse", teile.length > 1 ? teile[1] : "",
                "vollerPfad", klassen.get(0),
                "alleKlassen", klassen);
    }

    // -------------------------------------------------------------------------
    // Stufe 4b - Autorisierung ueber eine Rolle.
    //
    // Ohne die Rolle antwortet der Server mit 403, nicht mit 401: Das Token ist
    // gueltig, es reicht nur nicht. Der Unterschied ist der halbe Lehrinhalt.
    // -------------------------------------------------------------------------
    @GET
    @Path("/nur-lehrer")
    @RolesAllowed("lehrer")
    public Map<String, Object> nurLehrer() {
        return Map.of(
                "nachricht", "Diesen Endpunkt sieht nur, wer die Rolle 'lehrer' hat.",
                "angemeldetAls", jwt.getName());
    }

    /** Der groups-Claim, robust gegen "gar nicht vorhanden". */
    private List<String> gruppen() {
        Object roh = jwt.getClaim("groups");
        if (roh == null) {
            return List.of();
        }
        if (roh instanceof Iterable<?> it) {
            return java.util.stream.StreamSupport.stream(it.spliterator(), false)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return List.of(roh.toString());
    }
}
