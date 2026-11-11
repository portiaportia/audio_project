package com.music.music_service;

import com.music.model.Song;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/**
 * Simple API for the controller: loads one song asynchronously and controls its
 * preview. This JavaFX service hides HTTP, matching, threads and MediaPlayer.
 * Call its public methods on the JavaFX application thread. All registered
 * callbacks are invoked on that thread. Each controller owns its own instance.
 */
public class MusicService implements AutoCloseable {
    private enum State {
        IDLE, LOADING, READY, PLAYING, PAUSED, STOPPED, FINISHED, ERROR, CLOSED
    }

    private final ITunesService catalog = new ITunesService();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "music-lookup");
        worker.setDaemon(true);
        return worker;
    });

    private Consumer<String> onStatusChanged = message -> { };
    private Runnable onPlaybackChanged = () -> { };
    private Task<Song> lookup;
    private MediaPlayer player;
    private Song song;
    private State state = State.IDLE;
    private boolean finished;

    /** Registers a listener for user-readable loading/playback messages. */
    public void setOnStatusChanged(Consumer<String> listener) {
        onStatusChanged = Objects.requireNonNull(listener);
    }

    /** Registers a listener used to refresh the enabled state of buttons. */
    public void setOnPlaybackChanged(Runnable listener) {
        onPlaybackChanged = Objects.requireNonNull(listener);
    }

    /**
     * Finds the best recording in the background and prepares its audio preview.
     * Starting another lookup cancels the previous lookup and stops its audio.
     *
     * @param artist requested artist name
     * @param title requested song title
     * @param onLoaded receives the song so the controller can display its details
     */
    public void loadSong(String artist, String title, Consumer<Song> onLoaded) {
        requireFxThread();
        if (state == State.CLOSED) {
            throw new IllegalStateException("This music service has been closed.");
        }
        Objects.requireNonNull(onLoaded);
        if (lookup != null) { lookup.cancel(true); }
        disposePlayer();
        song = null;
        changeState(State.LOADING, "Finding the best matching recording...");

        Task<Song> task = new Task<>() {
            @Override
            protected Song call() throws Exception {
                return catalog.findSong(artist, title);
            }
        };
        lookup = task;
        task.setOnSucceeded(event -> {
            if (lookup != task || state == State.CLOSED) { return; }
            song = task.getValue();
            onLoaded.accept(song);
            // A callback could start another lookup or close the screen.
            if (lookup == task && state != State.CLOSED) { preparePreview(); }
        });
        task.setOnFailed(event -> {
            if (lookup == task && state != State.CLOSED) {
                fail("Could not load song: " + errorMessage(task.getException()));
            }
        });
        executor.submit(task);
    }

    private void preparePreview() {
        if (!song.hasPreview()) {
            fail("This recording has no preview available.");
            return;
        }
        changeState(State.LOADING, "Loading preview...");
        try {
            Media media = new Media(song.getPreviewUrl());
            MediaPlayer nextPlayer = new MediaPlayer(media);
            player = nextPlayer;
            nextPlayer.setOnReady(() -> {
                if (player == nextPlayer) {
                    changeState(State.READY, "Ready. Press Play preview.");
                }
            });
            nextPlayer.setOnPlaying(() -> {
                if (player == nextPlayer) {
                    changeState(State.PLAYING, "Playing preview...");
                }
            });
            nextPlayer.setOnPaused(() -> {
                if (player == nextPlayer) { changeState(State.PAUSED, "Paused."); }
            });
            nextPlayer.setOnStopped(() -> {
                if (player == nextPlayer && !finished) {
                    changeState(State.STOPPED, "Stopped.");
                }
            });
            nextPlayer.setOnEndOfMedia(() -> {
                if (player == nextPlayer) {
                    finished = true;
                    changeState(State.FINISHED, "Preview finished. You can play it again.");
                }
            });
            nextPlayer.setOnStalled(() -> {
                if (player == nextPlayer) { onStatusChanged.accept("Buffering preview..."); }
            });
            nextPlayer.setOnError(() -> previewFailed(nextPlayer, nextPlayer.getError()));
            nextPlayer.setOnHalted(() -> previewFailed(nextPlayer, nextPlayer.getError()));
            media.setOnError(() -> previewFailed(nextPlayer, media.getError()));
            if (media.getError() != null) {
                previewFailed(nextPlayer, media.getError());
            } else if (nextPlayer.getError() != null) {
                previewFailed(nextPlayer, nextPlayer.getError());
            }
        } catch (RuntimeException exception) {
            fail("Could not prepare preview: " + errorMessage(exception));
        }
    }

    /** Plays or resumes the current preview when it is ready. */
    public void play() {
        requireFxThread();
        if (!canPlay()) { return; }
        if (finished) {
            finished = false;
            player.seek(Duration.ZERO);
        }
        player.play();
    }

    /** Pauses playback without returning to the beginning. */
    public void pause() {
        requireFxThread();
        if (canPause()) { player.pause(); }
    }

    /** Stops playback and returns to the beginning. */
    public void stop() {
        requireFxThread();
        if (canStop()) {
            finished = false;
            player.stop();
            changeState(State.STOPPED, "Stopped.");
        }
    }

    /** Returns whether Play should currently be enabled. */
    public boolean canPlay() {
        return player != null && (state == State.READY || state == State.PAUSED
                || state == State.STOPPED || state == State.FINISHED);
    }

    /** Returns whether Pause should currently be enabled. */
    public boolean canPause() { return player != null && state == State.PLAYING; }

    /** Returns whether Stop should currently be enabled. */
    public boolean canStop() {
        return player != null && (state == State.PLAYING
                || state == State.PAUSED || state == State.FINISHED);
    }

    /** Returns whether the loaded song has an external store page. */
    public boolean canOpenStore() {
        return song != null && ITunesService.isWebUrl(song.getStoreUrl());
    }

    private void previewFailed(MediaPlayer failedPlayer, Throwable error) {
        if (player == failedPlayer) {
            fail("Could not play preview: " + errorMessage(error));
        }
    }

    private void fail(String message) {
        disposePlayer();
        changeState(State.ERROR, message);
    }

    private void changeState(State next, String message) {
        state = next;
        onStatusChanged.accept(message);
        onPlaybackChanged.run();
    }

    private void disposePlayer() {
        MediaPlayer previous = player;
        player = null;
        finished = false;
        if (previous != null) { previous.dispose(); }
    }

    private static String errorMessage(Throwable error) {
        return error == null || error.getMessage() == null
                ? "An audio or network error occurred." : error.getMessage();
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Use MusicService from the JavaFX application thread.");
        }
    }

    /** Cancels pending work and releases audio resources. Safe to call twice. */
    @Override
    public void close() {
        requireFxThread();
        if (state == State.CLOSED) { return; }
        if (lookup != null) { lookup.cancel(true); }
        executor.shutdownNow();
        disposePlayer();
        song = null;
        changeState(State.CLOSED, "Closed.");
    }
}
