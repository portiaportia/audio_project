package com.music.music_service;

import com.music.model.Song;

import java.util.List;
import java.util.Locale;

/** Keeps recording-selection rules separate from HTTP and the user interface. */
public class SongMatcher {

    public static Song findBest(List<Song> songs, String artist, String title) {
        Song bestSong = null;
        int bestScore = -1;

        for (Song song : songs) {
            int score = score(song, artist, title);
            if (score > bestScore) {
                bestSong = song;
                bestScore = score;
            }
        }
        return bestSong;
    }

    private static int score(Song song, String artist, String title) {
        // A cover by another artist is not a match.
        if (!normalize(song.getArtist()).equals(normalize(artist))) {
            return -1;
        }

        String requestedTitle = normalize(title);
        String candidateTitle = normalize(song.getName());
        int score;

        if (candidateTitle.equals(requestedTitle)) {
            score = 100;
        } else if (normalize(baseTitle(song.getName())).equals(requestedTitle)) {
            // Allow labeled editions, such as "Yellow (Remastered)".
            score = 70;
        } else {
            // "Yellow Submarine" should not match "Yellow".
            return -1;
        }

        // Prefer a usable preview, then an exact title and a studio version.
        if (song.hasPreview()) { score += 300; }
        String edition = candidateTitle + " "
                + (song.getAlbum() == null ? "" : normalize(song.getAlbum()));
        if (edition.matches(".*\\b(live|remix|karaoke|tribute|instrumental)\\b.*")) {
            score -= 30;
        }
        return score;
    }

    private static String baseTitle(String title) {
        return title.split("\\s+[\\(\\[]|\\s+-\\s+", 2)[0];
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
