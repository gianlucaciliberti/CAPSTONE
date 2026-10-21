package it.squadra.gestionale.services;

import static org.assertj.core.api.Assertions.assertThat;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.services.EmailService.EmailBody;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Template Thymeleaf delle email: composizione, escape dei contenuti, versione testuale. */
class EmailTemplateServiceTest {

    private final EmailTemplateService templates = new EmailTemplateService(new AppProperties(
            ZoneId.of("Europe/Rome"), "https://squadra.example/",
            new AppProperties.Cors(List.of("https://squadra.example")),
            new AppProperties.Security(new AppProperties.Jwt("x", "test", Duration.ofMinutes(15), Duration.ofDays(1)), true),
            new AppProperties.RateLimit(false, List.of()),
            new AppProperties.BootstrapAdmin(null, null, null, null),
            new AppProperties.Mail(AppProperties.MailMode.LOG, "noreply@squadra.example", "ASD Pallone d'Oro", 5, false,
                    new AppProperties.Mailjet(null, null, null)),
            new AppProperties.OpenAi(false, null, null, Duration.ofSeconds(20))));

    @Test
    void callupPublishedContainsMatchDetailsLinkAndPlayersInBothVersions() {
        EmailBody body = templates.render("callup-published", callupVariables());

        assertThat(body.html())
                .contains("<!DOCTYPE html>", "ASD Pallone d&#39;Oro", "Mario", "Real Borgata",
                        "sabato 7 novembre 2026 alle ore 15:30", "ore <span>14:45</span>",
                        "href=\"https://squadra.example/matches/7\"", "Luca Verdi, Mario Rossi")
                .doesNotContain("th:text", "th:if", "th:replace", "xmlns:th");
        assertThat(body.text())
                .contains("Ciao Mario,", "vs Real Borgata", "Ritrovo: ore 14:45",
                        "Vedi la convocazione: https://squadra.example/matches/7", "Convocati (2): Luca Verdi, Mario Rossi")
                .doesNotContain("<", "[(");
    }

    @Test
    void userTextIsEscapedInHtml() {
        Map<String, Object> variables = callupVariables();
        variables.put("notes", "<script>alert('x')</script> porta i parastinchi");
        variables.put("firstName", "<b>Mario</b>");

        EmailBody body = templates.render("callup-updated", variables);

        assertThat(body.html()).doesNotContain("<script>", "<b>Mario</b>")
                .contains("&lt;script&gt;", "&lt;b&gt;Mario&lt;/b&gt;");
    }

    @Test
    void optionalPartsAreOmittedWhenMissing() {
        Map<String, Object> variables = callupVariables();
        variables.put("meetingTime", null);
        variables.put("notes", null);

        EmailBody body = templates.render("callup-published", variables);

        assertThat(body.text()).doesNotContain("Ritrovo", "Note:");
        assertThat(body.html()).doesNotContain("Ritrovo:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"callup-published", "callup-updated", "callup-removed", "callup-withdrawn",
            "account-approved", "email-verification", "password-reset", "payment-request", "payment-reminder"})
    void everyTemplateRendersInBothFormats(String template) {
        Map<String, Object> variables = callupVariables();
        variables.putAll(paymentVariables());
        variables.put("link", "https://squadra.example/verify-email?token=abc");
        variables.put("loginUrl", "https://squadra.example/login");
        variables.put("minutes", 30);
        variables.put("reason", "Campo impraticabile");
        variables.put("changeSummary", "orario di ritrovo");

        EmailBody body = templates.render(template, variables);

        assertThat(body.html()).startsWith("<!DOCTYPE html>").contains("Ciao <span>Mario</span>");
        assertThat(body.text()).startsWith("Ciao Mario,").endsWith("-- ASD Pallone d'Oro\n");
    }

    @Test
    void paymentEmailsShowAmountDueDateAndOverdueNotice() {
        EmailBody request = templates.render("payment-request", paymentVariables());
        assertThat(request.text()).contains("Campo ottobre", "Importo: 15,00", "Scadenza: sabato 31 ottobre 2026",
                "Note: IBAN IT00", "Vedi i tuoi pagamenti: https://squadra.example/payments");

        Map<String, Object> variables = paymentVariables();
        variables.put("overdue", true);
        variables.put("dueDate", null);
        EmailBody reminder = templates.render("payment-reminder", variables);
        assertThat(reminder.text()).contains("risulta scaduta").doesNotContain("ancora da pagare", "Scadenza:");
        assertThat(reminder.html()).contains("<strong>scaduta</strong>").doesNotContain("Scadenza:");
    }

    @Test
    void datesAreFormattedInItalianInTeamTimeZone() {
        assertThat(templates.formatDateTime(Instant.parse("2026-06-15T19:00:00Z")))
                .isEqualTo("lunedì 15 giugno 2026 alle ore 21:00");
        assertThat(templates.formatDate(LocalDate.of(2026, 10, 31))).isEqualTo("sabato 31 ottobre 2026");
        assertThat(templates.formatEuro(1250)).isEqualTo("12,50 €");
        assertThat(templates.link("/login")).isEqualTo("https://squadra.example/login");
    }

    private Map<String, Object> paymentVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", "Richiesta di pagamento");
        variables.put("firstName", "Mario");
        variables.put("description", "Campo ottobre");
        variables.put("amount", templates.formatEuro(1500));
        variables.put("dueDate", "sabato 31 ottobre 2026");
        variables.put("notes", "IBAN IT00");
        variables.put("paymentsUrl", "https://squadra.example/payments");
        variables.put("overdue", false);
        return variables;
    }

    private static Map<String, Object> callupVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", "Convocazione");
        variables.put("firstName", "Mario");
        variables.put("opponent", "Real Borgata");
        variables.put("kickoff", "sabato 7 novembre 2026 alle ore 15:30");
        variables.put("venueName", "Campo comunale");
        variables.put("venueAddress", "Via Roma 1");
        variables.put("mapsUrl", "https://www.google.com/maps/search/?api=1&query=Campo");
        variables.put("meetingTime", "14:45");
        variables.put("notes", "Maglia bianca");
        variables.put("callupUrl", "https://squadra.example/matches/7");
        variables.put("players", List.of("Luca Verdi", "Mario Rossi"));
        return variables;
    }
}
