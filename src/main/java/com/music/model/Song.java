package com.music.model;

public class Song {
    private final long id;
    private final String name;
    private final String artist;
    private final String album;
    private final String artworkUrl;
    private final String previewUrl;
    private final String storeUrl;

    public Song(long id, String name, String artist, String album, String artworkUrl, String previewUrl, String storeUrl) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.album = album;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.storeUrl = storeUrl;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getArtworkUrl() { return artworkUrl; }
    public String getPreviewUrl() { return previewUrl; }
    public String getStoreUrl() { return storeUrl; }
    public boolean hasPreview() { return previewUrl != null && !previewUrl.isBlank(); }
    public String toString() { return name + " - " + artist; }
}
