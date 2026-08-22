package at.htlleonding;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Stufe 5 - Benutzerlisten.
 *
 * Der Unterschied zu allen vorigen Stufen in einem Satz: Hier geht es zum
 * ersten Mal um Personen, die gerade NICHT angemeldet sind. Alles andere -
 * Klasse, Rolle, Name - steht im Token der angemeldeten Person und braucht
 * keinen Aufruf beim Server (siehe /api/my-class).
 *
 * Deshalb braucht genau dieser Endpunkt ein Dienstkonto, und deshalb bekommt
 * ein Schuelerprojekt es nicht nebenbei. An diesem einen Geheimnis haengt der
 * Blick auf die Konten der Schule.
 */
@Path("/api/groups")
@Produces(MediaType.APPLICATION_JSON)
public class GroupsResource {

    /** Seitengroesse fuer die Mitgliederabfrage. */
    private static final int SEITE = 100;

    /**
     * Notbremse. Die groesste Klasse hat 73 Mitglieder; wer hier anschlaegt,
     * hat eine Gruppe erwischt, die keine Klasse ist - oder eine Schleife.
     */
    private static final int OBERGRENZE = 2000;

    @Inject
    @RestClient
    KeycloakAdminApi admin;

    /**
     * Alle Gruppen als flache Liste, nach Pfad sortiert.
     *
     * Flach und nicht als Baum, weil die Anzeige es so braucht und weil der
     * Pfad die Hierarchie ohnehin traegt: /classes/IF/5BHIF.
     */
    @GET
    @RolesAllowed("teacher")
    public Response groups() {
        try {
            List<Map<String, Object>> flach = new ArrayList<>();
            for (Group g : admin.topLevelGroups(0, 200, false)) {
                sammeln(g, flach);
            }
            flach.sort(Comparator.comparing(m -> (String) m.get("path")));
            return Response.ok(flach).build();
        } catch (ClientWebApplicationException e) {
            return fehler(e);
        }
    }

    /** Die Mitglieder einer Gruppe, ueber alle Seiten hinweg. */
    @GET
    @Path("/{id}/members")
    @RolesAllowed("teacher")
    public Response members(@PathParam("id") String id) {
        try {
            List<User> alle = new ArrayList<>();
            int first = 0;
            List<User> seite;
            do {
                seite = admin.members(id, first, SEITE, true);
                alle.addAll(seite);
                first += SEITE;
                // Eine Seite, die nicht voll ist, war die letzte. Ohne diese
                // Abbruchbedingung fragt man ewig weiter - Keycloak antwortet
                // auf jede weitere Seite freundlich mit einer leeren Liste.
            } while (seite.size() == SEITE && alle.size() < OBERGRENZE);

            long aktiv = alle.stream().filter(u -> u.enabled() != null && u.enabled()).count();
            return Response.ok(Map.of(
                    "count", alle.size(),
                    "active", aktiv,
                    // Deaktivierte Konten gehoeren sichtbar dazu: In manchen
                    // Abteilungen ist Deaktivieren die Praxis beim Austritt,
                    // in anderen bleibt das Konto aktiv liegen. Eine Liste
                    // ohne diese Zahl wird falsch gelesen.
                    "disabled", alle.size() - aktiv,
                    "members", alle)).build();
        } catch (ClientWebApplicationException e) {
            return fehler(e);
        }
    }

    /**
     * Der Fehler des Servers, uebersetzt in einen, der die Ursache benennt.
     *
     * Ohne diese Uebersetzung meldet Quarkus einen 500er, und die Fehlersuche
     * beginnt in der eigenen Anwendung - waehrend die Ursache ein fehlendes
     * Recht am Dienstkonto ist.
     */
    private Response fehler(ClientWebApplicationException e) {
        int status = e.getResponse().getStatus();
        String hinweis = switch (status) {
            case 401 -> "Das Dienstkonto konnte sich nicht anmelden. Stimmt das Client Secret "
                      + "in KC_DEMO_BACKEND_SECRET, und ist der Dienstkonto-Ablauf am Client "
                      + "demo-backend eingeschaltet?";
            case 403 -> "Das Dienstkonto ist angemeldet, darf aber nicht lesen. Es braucht die "
                      + "Rolle view-users aus dem Client realm-management.";
            case 404 -> "Diese Gruppe gibt es nicht (mehr).";
            default  -> "Unerwartete Antwort der Verwaltungsschnittstelle.";
        };
        return Response.status(status == 401 || status == 403 ? 502 : status)
                .entity(Map.of(
                        // 502 und nicht 401/403 weitergereicht: Der Fehler liegt
                        // zwischen Backend und Keycloak, nicht beim Aufrufer.
                        // Wer ihn durchreicht, schickt das Frontend in eine
                        // Anmeldeschleife, die nichts repariert.
                        "error", "Verwaltungsschnittstelle antwortet " + status,
                        "hint", hinweis))
                .build();
    }

    /** Baum in eine flache Liste falten - rekursiv ueber /children. */
    private void sammeln(Group g, List<Map<String, Object>> ziel) {
        int kinder = g.subGroupCount() == null ? 0 : g.subGroupCount();
        ziel.add(Map.of(
                "id", g.id(),
                "name", g.name(),
                "path", g.path(),
                "level", g.path().chars().filter(c -> c == '/').count() - 1,
                // Nur Blaetter tragen Mitglieder: /classes und /classes/IF sind
                // Ordner, /classes/IF/5BHIF ist die Klasse.
                "leaf", kinder == 0));
        if (kinder > 0) {
            for (Group kind : admin.children(g.id(), 0, 200, false)) {
                sammeln(kind, ziel);
            }
        }
    }
}
