package com.hexvane.aetherhaven.community;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Uploads the sheet and the existing skin export together, without publishing either asset. */
public final class TownsfolkSubmissionService {
    private TownsfolkSubmissionService() { }
    public record Result(boolean success, String message) { }

    public static Result submit(String apiBaseUrl, UUID player, String playerName, JsonObject character, JsonObject model) {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        String sheet = gson.toJson(character), skin = gson.toJson(model);
        // Retrying the same payload after a lost response must not create a second submission.
        String requestId = UUID.nameUUIDFromBytes((player + "\n" + sheet + "\n" + skin).getBytes(StandardCharsets.UTF_8)).toString();
        String boundary = "Aetherhaven" + UUID.randomUUID().toString().replace("-", "");
        String body = part(boundary, "townsfolk", sheet) + part(boundary, "model", skin) + "--" + boundary + "--\r\n";
        var response = CommunityHttpClient.postMultipartResult(apiBaseUrl.replaceAll("/+$", "") + "/api/v1/townsfolk-submissions",
            Map.of("X-Player-Uuid", player.toString(), "X-Player-Name", playerName, "X-Submission-Id", requestId),
            boundary, body.getBytes(StandardCharsets.UTF_8));
        if (response.isSuccess()) {
            try {
                var receipt = JsonParser.parseString(response.getBody()).getAsJsonObject();
                if (receipt.get("submissionId").getAsString().equals(requestId)) {
                    boolean portraitReady = receipt.has("portraitStatus") && receipt.get("portraitStatus").getAsString().equals("ready");
                    return new Result(true, portraitReady
                        ? "Submitted! Website admins can now download your character, appearance and portrait."
                        : "Character and appearance saved. Your portrait needs an admin retry on the website.");
                }
            } catch (RuntimeException ignored) { }
            return new Result(false, "The website did not confirm the upload. Your sheet is still here; please retry.");
        }
        return new Result(false, switch (response.getStatusCode()) {
            case 429 -> "Too many submissions. Please try again later; your sheet is still here.";
            case 404 -> "This website has not enabled the townsfolk creator yet. Your sheet is still here.";
            case 400, 413 -> "The website could not accept this sheet or appearance. Check your choices and try again.";
            default -> "Could not reach the website or save your submission. Your sheet is still here; please retry.";
        });
    }

    private static String part(String boundary, String field, String json) {
        return "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + field
            + ".json\"\r\nContent-Type: application/json\r\n\r\n" + json + "\r\n";
    }
}
