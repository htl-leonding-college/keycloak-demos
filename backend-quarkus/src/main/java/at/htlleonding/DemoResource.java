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
import java.util.stream.StreamSupport;

/**
 * Die Stufen 1 bis 4 als je ein Endpunkt.
 *
 * Der Aufbau ist Absicht: Jeder Endpunkt zeigt genau einen Schritt, und der
 * Unterschied zum vorigen ist eine Zeile oder eine Annotation.
 *
 * Bezeichner sind englisch, Kommentare deutsch - Endpunktpfade und
 * JSON-Felder liest jedes Schuelerprojekt, die Erklaerungen nur wer hier
 * hereinschaut.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

    /** Praefix, an dem eine Klassengruppe erkennbar ist. Siehe D33. */
    private static final String CLASSES_PREFIX = "/classes/";

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    // -------------------------------------------------------------------------
    // Stufe 1/2 - ohne Anmeldung erreichbar.
    // Dient als Gegenprobe: Wer hier 200 bekommt und bei /protected 401, hat
    // einen laufenden Server und ein fehlendes Token - nicht umgekehrt.
    // -------------------------------------------------------------------------
    @GET
    @Path("/public")
    public Map<String, Object> publicEndpoint() {
        return Map.of(
                "message", "Dieser Endpunkt braucht kein Token.",
                "hint", "Wenn dieser Aufruf gelingt und /api/protected mit 401 "
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
    @Path("/protected")
    @Authenticated
    public Map<String, Object> protectedEndpoint() {
        return Map.of(
                "username", jwt.getName(),
                "issuer", jwt.getIssuer(),
                "audience", jwt.getAudience(),
                // azp = authorized party: WELCHE Anwendung hat das Token geholt.
                // aud sagt, fuer wen es gedacht ist; azp, wer es besorgt hat.
                // Bei einem Token aus einem anderen Projekt weicht azp ab,
                // auch wenn aud passt.
                "authorizedParty", jwt.getClaim("azp"),
                "expiresAt", jwt.getExpirationTime(),
                "roles", identity.getRoles(),
                "groups", groups());
    }

    // -------------------------------------------------------------------------
    // Stufe 4a - Autorisierung ueber die Klassengruppe.
    //
    // Die Klasse steht im Token, weil der Client Scope "classes" sie als
    // groups-Claim mitgibt (Task 7.8). Kein Abruf beim Server noetig - das ist
    // der Punkt: Wer die angemeldete Person autorisieren will, braucht keine
    // Benutzerliste und damit kein Dienstkonto.
    // -------------------------------------------------------------------------
    @GET
    @Path("/my-class")
    @Authenticated
    public Map<String, Object> myClass() {
        List<String> classes = groups().stream()
                .filter(g -> g.startsWith(CLASSES_PREFIX))
                .toList();

        if (classes.isEmpty()) {
            // KEIN Fehler. Das ist normativ so verlangt: Zwischen Import und
            // naechstem Gruppenabgleich hat eine Person keine Klasse, und
            // lokale Konten sowie Lehrkraefte haben nie eine. Eine Anwendung,
            // die hier abbricht, faellt am ersten Schultag um.
            return Map.of(
                    "class", "",
                    "hint", "Dieses Konto gehoert keiner Klassengruppe an. Das ist "
                          + "ein gueltiger Zustand: lokale Konten und Lehrkraefte "
                          + "haben keine, neu importierte noch nicht.");
        }

        // Der volle Pfad ist /classes/<DEPARTMENT>/<CLASS>.
        String[] parts = classes.get(0).substring(CLASSES_PREFIX.length()).split("/");
        return Map.of(
                "department", parts.length > 0 ? parts[0] : "",
                "class", parts.length > 1 ? parts[1] : "",
                "fullPath", classes.get(0),
                "allClasses", classes);
    }

    // -------------------------------------------------------------------------
    // Stufe 4b - Autorisierung ueber eine Rolle.
    //
    // Ohne die Rolle antwortet der Server mit 403, nicht mit 401: Das Token ist
    // gueltig, es reicht nur nicht. Der Unterschied ist der halbe Lehrinhalt.
    // -------------------------------------------------------------------------
    @GET
    @Path("/teachers-only")
    @RolesAllowed("teacher")
    public Map<String, Object> teachersOnly() {
        return Map.of(
                "message", "Diesen Endpunkt sieht nur, wer die Rolle 'teacher' hat.",
                "username", jwt.getName());
    }

    /** Der groups-Claim, robust gegen "gar nicht vorhanden". */
    private List<String> groups() {
        Object raw = jwt.getClaim("groups");
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Iterable<?> it) {
            return StreamSupport.stream(it.spliterator(), false)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return List.of(raw.toString());
    }
}
