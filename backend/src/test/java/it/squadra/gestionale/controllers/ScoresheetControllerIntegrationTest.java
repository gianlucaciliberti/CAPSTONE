package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.MatchEvent;
import it.squadra.gestionale.entities.MatchEvent.MatchEventType;
import it.squadra.gestionale.entities.MatchEvent.TeamSide;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.MatchEventRepository;
import it.squadra.gestionale.repositories.MatchRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Risultato ufficiale e tabellino: permessi, regole sugli eventi, coerenza, concorrenza, audit e storico. */
@IntegrationTest
class ScoresheetControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private MatchRepository matches;
    @Autowired
    private MatchEventRepository events;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User coach;
    private User paolo;
    private Match match;

    @BeforeEach
    void setUp() {
        admin = user("Anna", "Admin", Role.ADMIN);
        coach = user("Carlo", "Coach", Role.COACH);
        paolo = user("Paolo", "Bianchi", Role.PLAYER);
        match = matches.saveAndFlush(new Match("Avversari", Instant.now().minus(2, ChronoUnit.HOURS),
                "Campo", "Via Roma 1", null, admin));
    }

    @Nested
    class Result {

        @Test
        void onlyAdminRecordsResultEveryoneReadsIt() throws Exception {
            result(coach, 2, 1, match.getVersion()).andExpect(status().isForbidden());
            result(paolo, 2, 1, match.getVersion()).andExpect(status().isForbidden());

            result(admin, 2, 1, match.getVersion())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.teamGoals").value(2))
                    .andExpect(jsonPath("$.opponentGoals").value(1));

            perform(paolo, get(url("/scoresheet")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.teamGoals").value(2));
            Match stored = matches.findById(match.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(MatchStatus.COMPLETED);
            assertThat(stored.getResultUpdatedBy().getId()).isEqualTo(admin.getId());
        }

        @Test
        void correctionIsAuditedWithPreviousValue() throws Exception {
            long version = version(result(admin, 2, 1, match.getVersion()));
            result(admin, 3, 1, version).andExpect(status().isOk());

            assertThat(jdbc.queryForList("select details::text from audit_logs where action = 'MATCH_RESULT_RECORDED' "
                    + "order by id", String.class))
                    .hasSize(2)
                    .last().asString().contains("\"teamGoals\": 2", "\"teamGoals\": 3");
        }

        @Test
        void staleVersionIsConflict() throws Exception {
            long old = match.getVersion();
            result(admin, 2, 1, old).andExpect(status().isOk());
            result(admin, 0, 0, old).andExpect(status().isConflict());
        }

        @Test
        void futureOrCancelledMatchesHaveNoResult() throws Exception {
            Match future = matches.saveAndFlush(new Match("Futura", Instant.now().plus(3, ChronoUnit.DAYS),
                    "Campo", "Via Roma 1", null, admin));
            perform(admin, put("/api/v1/matches/" + future.getId() + "/result").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"teamGoals\":1,\"opponentGoals\":0,\"version\":0}"))
                    .andExpect(status().isUnprocessableContent());

            match.cancel(null, admin, Instant.now());
            match = matches.saveAndFlush(match);
            result(admin, 1, 0, match.getVersion()).andExpect(status().isUnprocessableContent());
        }

        @Test
        void negativeOrMissingGoalsAreValidationErrors() throws Exception {
            perform(admin, put(url("/result")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"teamGoals\":-1,\"version\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.teamGoals").exists())
                    .andExpect(jsonPath("$.fieldErrors.opponentGoals").exists());
        }
    }

    @Nested
    class Events {

        @Test
        void adminAddsGoalsOwnGoalsAndCardsForPlayersGuestsAndOpponents() throws Exception {
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d,\"minute\":12}".formatted(paolo.getId()))
                    .andExpect(status().isCreated());
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerName\":\"Gianni (ospite)\",\"minute\":30}")
                    .andExpect(status().isCreated());
            addEvent("{\"eventType\":\"OWN_GOAL\",\"teamSide\":\"OPPONENT\",\"playerName\":\"N. 5\",\"minute\":44}")
                    .andExpect(status().isCreated());
            addEvent("{\"eventType\":\"YELLOW_CARD\",\"teamSide\":\"OPPONENT\",\"minute\":50,\"notes\":\"proteste\"}")
                    .andExpect(status().isCreated());
            addEvent("{\"eventType\":\"OWN_GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d}".formatted(paolo.getId()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.events.length()").value(5))
                    .andExpect(jsonPath("$.events[0].playerDisplayName").value("Paolo Bianchi"))
                    .andExpect(jsonPath("$.events[1].playerDisplayName").value("Gianni (ospite)"))
                    // Gol nostri: 2 gol + autogol avversario; gol avversari: il nostro autogol.
                    .andExpect(jsonPath("$.computedTeamGoals").value(3))
                    .andExpect(jsonPath("$.computedOpponentGoals").value(1));

            assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'MATCH_EVENT_CREATED'",
                    Integer.class)).isEqualTo(5);
        }

        @Test
        void eventRulesAreEnforced() throws Exception {
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"OPPONENT\",\"playerId\":%d}".formatted(paolo.getId()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString("avversari")));
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\"}")
                    .andExpect(status().isUnprocessableContent());
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d,\"playerName\":\"x\"}"
                    .formatted(paolo.getId()))
                    .andExpect(status().isUnprocessableContent());
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerName\":\"x\",\"minute\":151}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.minute").exists());
            addEvent("{\"teamSide\":\"TEAM\",\"playerName\":\"x\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.eventType").exists());
        }

        @Test
        void playersCannotEditTheScoresheet() throws Exception {
            perform(paolo, post(url("/events")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerName\":\"x\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void updateAndDeleteAreAuditedAndVersioned() throws Exception {
            String json = addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d,\"minute\":10}"
                    .formatted(paolo.getId())).andReturn().getResponse().getContentAsString();
            long eventId = ((Number) JsonPath.read(json, "$.events[0].id")).longValue();
            long version = ((Number) JsonPath.read(json, "$.events[0].version")).longValue();

            perform(admin, put(url("/events/" + eventId)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d,\"minute\":15,\"version\":%d}"
                            .formatted(paolo.getId(), version)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.events[0].minute").value(15));
            perform(admin, put(url("/events/" + eventId)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d,\"minute\":20,\"version\":%d}"
                            .formatted(paolo.getId(), version)))
                    .andExpect(status().isConflict());

            perform(admin, delete(url("/events/" + eventId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.events.length()").value(0));
            assertThat(events.findById(eventId)).isEmpty();
            assertThat(jdbc.queryForObject("select details::text from audit_logs where action = 'MATCH_EVENT_DELETED'",
                    String.class)).contains("Paolo Bianchi", "\"minute\": 15");
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'MATCH_EVENT_UPDATED'",
                    Integer.class)).isEqualTo(1);
        }

        @Test
        void eventOfAnotherMatchIsNotFound() throws Exception {
            Match other = matches.saveAndFlush(new Match("Altra", Instant.now().minus(1, ChronoUnit.DAYS),
                    "Campo", "Via Roma 1", null, admin));
            String json = perform(admin, post("/api/v1/matches/" + other.getId() + "/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerName\":\"x\"}"))
                    .andReturn().getResponse().getContentAsString();
            long eventId = ((Number) JsonPath.read(json, "$.events[0].id")).longValue();

            perform(admin, delete(url("/events/" + eventId))).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Consistency {

        @Test
        void mismatchBetweenResultAndEventsIsReported() throws Exception {
            result(admin, 3, 1, match.getVersion());
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d}".formatted(paolo.getId()))
                    .andExpect(jsonPath("$.consistent").value(false))
                    .andExpect(jsonPath("$.warnings[0]").value("Gol della squadra: risultato 3, nel tabellino 1."))
                    .andExpect(jsonPath("$.warnings[1]").value("Gol dell'avversario: risultato 1, nel tabellino 0."));
        }

        @Test
        void matchingEventsAreConsistent() throws Exception {
            result(admin, 1, 1, match.getVersion());
            addEvent("{\"eventType\":\"GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d}".formatted(paolo.getId()));
            addEvent("{\"eventType\":\"OWN_GOAL\",\"teamSide\":\"TEAM\",\"playerId\":%d}".formatted(paolo.getId()))
                    .andExpect(jsonPath("$.consistent").value(true))
                    .andExpect(jsonPath("$.warnings.length()").value(0));
        }

        @Test
        void eventsWithoutResultAndDoubleYellowWithoutRedAreReported() throws Exception {
            addEvent("{\"eventType\":\"YELLOW_CARD\",\"teamSide\":\"TEAM\",\"playerId\":%d}".formatted(paolo.getId()));
            addEvent("{\"eventType\":\"YELLOW_CARD\",\"teamSide\":\"TEAM\",\"playerId\":%d}".formatted(paolo.getId()))
                    .andExpect(jsonPath("$.warnings[0]").value(containsString("risultato ufficiale non è ancora registrato")));

            result(admin, 0, 0, matches.findById(match.getId()).orElseThrow().getVersion())
                    .andExpect(jsonPath("$.warnings[0]").value("Un giocatore ha due ammonizioni ma nessuna espulsione."));
        }
    }

    @Nested
    class History {

        @Test
        void resultsListOnlyCompletedMatchesNewestFirst() throws Exception {
            completed("Vecchia", 30, 1, 2);
            completed("Recente", 3, 2, 2);
            Match cancelled = matches.saveAndFlush(new Match("Annullata", Instant.now().minus(5, ChronoUnit.DAYS),
                    "Campo", "Via Roma 1", null, admin));
            cancelled.cancel(null, admin, Instant.now());
            matches.saveAndFlush(cancelled);

            perform(paolo, get("/api/v1/matches/results"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].opponent").value("Recente"))
                    .andExpect(jsonPath("$.content[0].teamGoals").value(2))
                    .andExpect(jsonPath("$.content[1].opponent").value("Vecchia"));
        }

        @Test
        void statsSumCompletedMatchesAndOurPlayersOnly() throws Exception {
            Match win = completed("Vinta", 10, 3, 1);
            Match loss = completed("Persa", 5, 0, 2);
            completed("Pari", 2, 1, 1);
            event(win, "GOAL", "TEAM", paolo, null);
            event(win, "GOAL", "TEAM", paolo, null);
            event(win, "GOAL", "TEAM", null, "Gianni ospite");
            event(loss, "OWN_GOAL", "TEAM", null, " gianni OSPITE ");
            event(loss, "YELLOW_CARD", "TEAM", paolo, null);
            event(loss, "GOAL", "OPPONENT", null, "Rossi");
            // Partita ancora senza risultato: i suoi eventi non entrano nelle statistiche.
            event(match, "RED_CARD", "TEAM", paolo, null);

            perform(paolo, get("/api/v1/matches/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.played").value(3))
                    .andExpect(jsonPath("$.wins").value(1))
                    .andExpect(jsonPath("$.draws").value(1))
                    .andExpect(jsonPath("$.losses").value(1))
                    .andExpect(jsonPath("$.goalsFor").value(4))
                    .andExpect(jsonPath("$.goalsAgainst").value(4))
                    .andExpect(jsonPath("$.players.length()").value(2))
                    .andExpect(jsonPath("$.players[0].name").value("Paolo Bianchi"))
                    .andExpect(jsonPath("$.players[0].playerId").value(paolo.getId()))
                    .andExpect(jsonPath("$.players[0].goals").value(2))
                    .andExpect(jsonPath("$.players[0].yellowCards").value(1))
                    .andExpect(jsonPath("$.players[0].redCards").value(0))
                    .andExpect(jsonPath("$.players[1].name").value("Gianni ospite"))
                    .andExpect(jsonPath("$.players[1].goals").value(1))
                    .andExpect(jsonPath("$.players[1].ownGoals").value(1));
        }

        private Match completed(String opponent, int daysAgo, int team, int opponentGoals) {
            Match m = new Match(opponent, Instant.now().minus(daysAgo, ChronoUnit.DAYS), "Campo", "Via Roma 1", null, admin);
            m.recordResult(team, opponentGoals, admin, Instant.now());
            return matches.saveAndFlush(m);
        }

        private void event(Match m, String type, String side, User player, String name) {
            events.saveAndFlush(new MatchEvent(m, MatchEventType.valueOf(type), TeamSide.valueOf(side), player, name,
                    null, null, admin));
        }
    }

    private String url(String suffix) {
        return "/api/v1/matches/" + match.getId() + suffix;
    }

    private ResultActions result(User actor, int team, int opponent, long version) throws Exception {
        return perform(actor, put(url("/result")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamGoals\":%d,\"opponentGoals\":%d,\"version\":%d}".formatted(team, opponent, version)));
    }

    private ResultActions addEvent(String json) throws Exception {
        return perform(admin, post(url("/events")).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long version(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.matchVersion"))
                .longValue();
    }

    private User user(String firstName, String lastName, Role role) {
        User user = new User(firstName, lastName, (firstName + "." + lastName + "@example.com").toLowerCase(), "hash");
        user.setRole(role);
        user.changeStatus(AccountStatus.ACTIVE, null, Instant.now());
        return users.saveAndFlush(user);
    }

    private ResultActions perform(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor).value()));
    }
}
