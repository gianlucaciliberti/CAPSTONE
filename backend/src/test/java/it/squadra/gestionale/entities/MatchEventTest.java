package it.squadra.gestionale.entities;

import static org.assertj.core.api.Assertions.assertThat;

import it.squadra.gestionale.enums.MatchEventType;
import it.squadra.gestionale.enums.TeamSide;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MatchEventTest {

    @ParameterizedTest(name = "{0} di {1}: per noi={2}, per avversario={3}")
    @CsvSource({
            "GOAL,        TEAM,     true,  false",
            "GOAL,        OPPONENT, false, true",
            "OWN_GOAL,    TEAM,     false, true",
            "OWN_GOAL,    OPPONENT, true,  false",
            "YELLOW_CARD, TEAM,     false, false",
            "RED_CARD,    OPPONENT, false, false"
    })
    void assignsGoalsToTheCorrectSide(MatchEventType type, TeamSide side, boolean forTeam, boolean forOpponent) {
        MatchEvent event = new MatchEvent(null, type, side, null, null, 10, null, null);

        assertThat(event.countsForTeam()).isEqualTo(forTeam);
        assertThat(event.countsForOpponent()).isEqualTo(forOpponent);
    }
}
