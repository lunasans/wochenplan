package de.wochenplan.app.data.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Laesst eine Woche vorschlagen. Als Schnittstelle, damit sie sich ersetzen laesst. */
interface WeekPlanner {
    suspend fun plan(context: WeekPlanContext, apiKey: String): WeekPlanProposal
}

/**
 * Fragt Claude nach einem Wochenvorschlag.
 *
 * Der Schluessel gehoert dem Benutzer und liegt verschluesselt auf dem Geraet;
 * die App spricht direkt mit der API, ohne Zwischenstelle.
 */
class ClaudeWeekPlanner(
    private val model: String = DEFAULT_MODEL,
) : WeekPlanner {

    override suspend fun plan(context: WeekPlanContext, apiKey: String): WeekPlanProposal =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                throw WeekPlanException("Es ist kein API-Schluessel hinterlegt.")
            }

            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(WeekPlanPrompt.systemPrompt())
                .addUserMessage(WeekPlanPrompt.userPrompt(context))
                .build()

            val text = try {
                val response = client.messages().create(params)
                response.content()
                    .mapNotNull { block -> block.text().orElse(null) }
                    .joinToString("\n") { it.text() }
            } catch (error: UnauthorizedException) {
                throw WeekPlanException("Der API-Schluessel wurde nicht akzeptiert.", error)
            } catch (error: PermissionDeniedException) {
                throw WeekPlanException("Dieser Schluessel darf das Modell nicht verwenden.", error)
            } catch (error: RateLimitException) {
                throw WeekPlanException("Zu viele Anfragen. Bitte gleich noch einmal versuchen.", error)
            } catch (error: AnthropicIoException) {
                throw WeekPlanException("Keine Verbindung zur KI. Bitte Internetverbindung pruefen.", error)
            } catch (error: AnthropicServiceException) {
                throw WeekPlanException("Die KI hat mit einem Fehler geantwortet.", error)
            }

            WeekPlanParser.parse(text)
        }

    companion object {
        /**
         * Thinking bleibt bewusst unkonfiguriert: Claude Opus 5 denkt dann von sich
         * aus adaptiv mit, was fuer das Abwaegen einer Wochenplanung gewuenscht ist.
         */
        const val DEFAULT_MODEL = "claude-opus-5"
        private const val MAX_TOKENS = 8_000L
    }
}
