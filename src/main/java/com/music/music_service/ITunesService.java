package com.music.music_service;

import com.music.model.Song;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;

/** Public iTunes Search API. No user login or API key is used. */
public class ITunesService {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Looks up one best match; never falls back to a different artist. */
    public Song findSong(String artist, String title)
            throws IOException, InterruptedException {
        if (artist == null || artist.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("An artist and song title are required.");
        }
        ArrayList<Song> results = searchSongs(artist + " " + title);
        Song song = SongMatcher.findBest(results, artist, title);
        if (song == null) {
            throw new IOException("No matching recording found for " + artist + " - " + title);
        }
        return song;
    }

    /** Fetch candidates internally; the screen receives only the best match. */
    private ArrayList<Song> searchSongs(String search)
            throws IOException, InterruptedException {
        if (search == null || search.isBlank()) {
            throw new IllegalArgumentException("Enter an artist or song name.");
        }
        String encoded = URLEncoder.encode(search.trim(), StandardCharsets.UTF_8);
        String url = "https://itunes.apple.com/search?term=" + encoded
                + "&media=music&entity=song&country=US&limit=50";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            throw new IOException("Too many searches. Wait a minute and try again.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("iTunes returned HTTP " + response.statusCode() + ".");
        }
        return parseSongs(response.body());
    }

    // Package access keeps JSON parsing independently testable.
    static ArrayList<Song> parseSongs(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement resultsElement = root.get("results");
            if (resultsElement == null || !resultsElement.isJsonArray()) {
                throw new IOException("The response did not contain a song list.");
            }
            JsonArray results = resultsElement.getAsJsonArray();
            ArrayList<Song> songs = new ArrayList<>();
            for (JsonElement element : results) {
                JsonObject item = element.getAsJsonObject();
                if (!"song".equals(text(item, "kind"))) { continue; }
                String name = text(item, "trackName");
                String artist = text(item, "artistName");
                if (name == null || artist == null) { continue; }
                long id = item.has("trackId") && !item.get("trackId").isJsonNull()
                        ? item.get("trackId").getAsLong() : 0;
                String storeUrl = text(item, "trackViewUrl");
                // Do not offer promotional previews without a corresponding store link.
                String preview = isWebUrl(storeUrl) ? text(item, "previewUrl") : null;
                if (!isWebUrl(preview)) { preview = null; }
                songs.add(new Song(id, name, artist, text(item, "collectionName"),
                        text(item, "artworkUrl100"), preview, storeUrl));
            }
            return songs;
        } catch (RuntimeException exception) {
            throw new IOException("Could not read the iTunes search response.", exception);
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) { return null; }
        String text = value.getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    public static boolean isWebUrl(String url) {
        if (url == null) { return false; }
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) { return false; }
    }
}
