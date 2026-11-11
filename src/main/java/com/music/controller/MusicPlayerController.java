package com.music.controller;

import com.music.model.Song;
import com.music.music_service.MusicService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class MusicPlayerController implements AutoCloseable {
    @FXML private ImageView artwork;
    @FXML private Label artworkStatus;
    @FXML private Label titleLabel;
    @FXML private Label artistLabel;
    @FXML private Label albumLabel;
    @FXML private Button playButton;
    @FXML private Button stopButton;

    private final MusicService musicService = new MusicService();

    @FXML
    private void initialize() {
        //when playback is changed call the function update buttons
        //we have to use a callback functuion as we don't know how long the async function will take
        musicService.setOnPlaybackChanged(this::updateButtons);

        //when the song finishes loading call the showSong funciton
        musicService.loadSong("Coldplay", "Yellow", this::showSong);
    }

    //Display the song information
    private void showSong(Song song) {
        titleLabel.setText(song.getName());
        artistLabel.setText(song.getArtist());
        albumLabel.setText(song.getAlbum() == null ? "" : song.getAlbum());
        showArtwork(song.getArtworkUrl());
    }

    private void showArtwork(String url) {
        if (url != null) {
            try {
                artwork.setImage(new Image(url, true));
                return;
            } catch (IllegalArgumentException exception) {
                
            }
        }
        
        artwork.setImage(null);
        artworkStatus.setText("Artwork unavailable");
    }

    @FXML
    private void playPreview() { musicService.play(); }

    @FXML
    private void pausePreview() { musicService.pause(); }

    @FXML
    private void stopPreview() { musicService.stop(); }

    /** The service decides which playback actions are currently available. */
    private void updateButtons() {
        playButton.setDisable(!musicService.canPlay());
        stopButton.setDisable(!musicService.canStop());
    }

    @Override
    public void close() { musicService.close(); }
}
