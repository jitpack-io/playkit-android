package com.kaltura.playkit.player;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.kaltura.playkit.AudioTrackInfo;
import com.kaltura.playkit.BaseTrackInfo;
import com.kaltura.playkit.PKLog;
import com.kaltura.playkit.TextTrackInfo;
import com.kaltura.playkit.TracksInfo;
import com.kaltura.playkit.VideoTrackInfo;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.SelectionOverride;


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Responsible for generating/sorting/holding and changing track info.
 * Created by anton.afanasiev on 22/11/2016.
 */

public class TrackSelectionHelper {

    private static final PKLog log = PKLog.get("TrackSelectionHelper");

    private static final TrackSelection.Factory FIXED_FACTORY = new FixedTrackSelection.Factory();

    public static final int TRACK_VIDEO = 0;
    public static final int TRACK_AUDIO = 1;
    public static final int TRACK_TEXT = 2;
    public static final int TRACK_ADAPTIVE = -1;

    private static final int RENDERER_INDEX = 0;
    private static final int GROUP_INDEX = 1;
    private static final int TRACK_INDEX = 2;
    private static final int TRACK_RENDERERS_AMOUNT = 3;

    private static final String ADAPTIVE_PREFIX = "adaptive";
    private static final String VIDEO_PREFIX = "Video:";
    private static final String AUDIO_PREFIX = "Audio:";
    private static final String TEXT_PREFIX = "Text:";

    private final MappingTrackSelector selector;
    private MappingTrackSelector.MappedTrackInfo mappedTrackInfo;
    private final TrackSelection.Factory adaptiveTrackSelectionFactory;
    private ExoPlayerWrapper.TracksInfoReadyListener tracksReadyListener;

    private List<BaseTrackInfo> videoTracksInfo = new ArrayList<>();
    private List<BaseTrackInfo> audioTracksInfo = new ArrayList<>();
    private List<BaseTrackInfo> textTracksInfo = new ArrayList<>();


    /**
     * @param selector                      The track selector.
     * @param adaptiveTrackSelectionFactory A factory for adaptive video {@link TrackSelection}s,
     *                                      or null if the selection helper should not support adaptive video.
     */
    public TrackSelectionHelper(MappingTrackSelector selector,
                                TrackSelection.Factory adaptiveTrackSelectionFactory) {
        this.selector = selector;
        this.adaptiveTrackSelectionFactory = adaptiveTrackSelectionFactory;
    }


    /**
     * Prepare {@link TracksInfo} object for application.
     * When the object is created, notify {@link ExoPlayerWrapper} about that,
     * and pass the {@link TracksInfo} as parameter.
     */
    public void prepareTracksInfo() {
        mappedTrackInfo = selector.getCurrentSelections().info;
        TracksInfo tracksInfo = buildTracksInfo();

        if (tracksReadyListener != null) {
            tracksReadyListener.onTracksInfoReady(tracksInfo);
        }
    }

    /**
     * Actually build {@link TracksInfo} object, based on the loaded manifest into Exoplayer.
     * This method knows how to filter unsupported/unknown formats, and create adaptive option when this is possible.
     */
    private TracksInfo buildTracksInfo() {
        TrackGroupArray trackGroupArray;
        TrackGroup trackGroup;
        Format format;
        //run through the all renders.
        for (int rendererIndex = 0; rendererIndex < TRACK_RENDERERS_AMOUNT; rendererIndex++) {

            //the trackGroupArray of the current renderer.
            trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);

            //run through the all track groups in current renderer.
            for (int groupIndex = 0; groupIndex < trackGroupArray.length; groupIndex++) {

                // the track group of the current trackGroupArray.
                trackGroup = trackGroupArray.get(groupIndex);

                //run through the all tracks in current trackGroup.
                for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {

                    // the format of the current trackGroup.
                    format = trackGroup.getFormat(trackIndex);
                    maybeAddAdaptiveTrack(rendererIndex, groupIndex, format);

                    //filter all the unsupported and unknown formats.
                    if (isFormatSupported(rendererIndex, groupIndex, trackIndex) && format.id != null) {
                        String uniqueId = getUniqueId(rendererIndex, groupIndex, trackIndex);
                        switch (rendererIndex) {
                            case TRACK_VIDEO:
                                videoTracksInfo.add(new VideoTrackInfo(uniqueId, format.bitrate, format.width, format.height, false));
                                break;
                            case TRACK_AUDIO:
                                audioTracksInfo.add(new AudioTrackInfo(uniqueId, format.language, format.bitrate, false));
                                break;

                            case TRACK_TEXT:
                                textTracksInfo.add(new TextTrackInfo(uniqueId, format.language));
                                break;
                        }
                    }
                }
            }
        }

        return new TracksInfo(videoTracksInfo, audioTracksInfo, textTracksInfo);
    }

    /**
     * If such an option exist, this method creates an adaptive object for the specified renderer.
     * @param rendererIndex - the index of the renderer that this adaptive object refer.
     * @param groupIndex - the index of the group this adaptive object refer.
     * @param format - the actual format of the adaptive object.
     */
    private void maybeAddAdaptiveTrack(int rendererIndex, int groupIndex, Format format) {
        String uniqueId = getUniqueId(rendererIndex, groupIndex, TRACK_ADAPTIVE);
        if (isAdaptive(rendererIndex, groupIndex) && !adaptiveTrackInfoAlreadyExist(uniqueId, rendererIndex)) {
            switch (rendererIndex) {
                case TRACK_VIDEO:
                    videoTracksInfo.add(new VideoTrackInfo(uniqueId, 0, 0, 0, true));
                    break;
                case TRACK_AUDIO:
                    audioTracksInfo.add(new AudioTrackInfo(uniqueId, format.language, 0, true));
                    break;
                case TRACK_TEXT:
                    textTracksInfo.add(new TextTrackInfo(uniqueId, format.language));
                    break;
            }
        }
    }

    /**
     * Build uniqueId based on the track indexes.
     * @param rendererIndex - renderer index of the current track.
     * @param groupIndex - group index of the current track.
     * @param trackIndex - actual track index.
     * @return - uniqueId that represent current track.
     */
    private String getUniqueId(int rendererIndex, int groupIndex, int trackIndex) {
        String rendererPrefix = "";
        switch (rendererIndex){
            case TRACK_VIDEO:
                rendererPrefix = VIDEO_PREFIX;
                break;
            case TRACK_AUDIO:
                rendererPrefix = AUDIO_PREFIX;
                break;
            case TRACK_TEXT:
                rendererPrefix = TEXT_PREFIX;
                break;
        }
        StringBuilder uniqueStringBuilder = new StringBuilder(rendererPrefix);
        uniqueStringBuilder.append(rendererIndex);
        uniqueStringBuilder.append(",");
        uniqueStringBuilder.append(groupIndex);
        uniqueStringBuilder.append(",");
        if(trackIndex == TRACK_ADAPTIVE){
            uniqueStringBuilder.append(ADAPTIVE_PREFIX);
        }else{
            uniqueStringBuilder.append(trackIndex);
        }
        return uniqueStringBuilder.toString();
    }

    /**
     * Change currently playing track with the new one.
     *
     * @param uniqueId - unique identifier of the track to apply.
     */
    public void changeTrack(String uniqueId) {
        log.i("change track to uniqueID -> " + uniqueId);
        mappedTrackInfo = selector.getCurrentSelections().info;
        int[] uniqueTrackId = convertUniqueId(uniqueId);
        int rendererIndex = uniqueTrackId[RENDERER_INDEX];

        SelectionOverride override = retrieveOverrideSelection(uniqueTrackId);
        overrideTrack(rendererIndex, override);
    }

    /**
     * @param uniqueId - the uniqueId to convert.
     * @return - int[] that consist from indexes that are readable to Exoplayer.
     */
    private int[] convertUniqueId(String uniqueId) {
        int[] convertedUniqueId = new int[3];
        String splitUniqueId = removePrefix(uniqueId);
        String[] strArray = splitUniqueId.split(",");

        for (int i = 0; i < strArray.length; i++) {
            if(strArray[i].equals(ADAPTIVE_PREFIX)){
                convertedUniqueId[i] = TRACK_ADAPTIVE;
            }else {
                convertedUniqueId[i] = Integer.parseInt(strArray[i]);
            }
        }
        return convertedUniqueId;
    }

    /**
     * Build the the {@link SelectionOverride} object, based on the uniqueId. This {@link SelectionOverride}
     * will be feeded later to the Exoplayer in order to switch to the new track.
     * This method decide if it should create adaptive override or fixed.
     * @param uniqueId - the unique id of the track that will override the existing one.
     * @return - the {@link SelectionOverride} which will override the existing selection.
     */
    private SelectionOverride retrieveOverrideSelection(int[] uniqueId) {

        SelectionOverride override;

        int rendererIndex = uniqueId[RENDERER_INDEX];
        int groupIndex = uniqueId[GROUP_INDEX];
        int trackIndex = uniqueId[TRACK_INDEX];

        boolean isAdaptive = trackIndex == TRACK_ADAPTIVE ? true : false;

        if (isAdaptive) {

            List<Integer> adaptiveTrackIndexesList = new ArrayList<>();
            int[] adaptiveTrackIndexes;

            switch (rendererIndex) {
                case TRACK_VIDEO:

                    VideoTrackInfo videoTrackInfo;

                    for (int i = 1; i < videoTracksInfo.size(); i++) {
                        videoTrackInfo = (VideoTrackInfo) videoTracksInfo.get(i);
                        if (getIndexFromUniqueId(videoTrackInfo.getUniqueId(), GROUP_INDEX) == groupIndex) {
                            adaptiveTrackIndexesList.add(getIndexFromUniqueId(videoTrackInfo.getUniqueId(), TRACK_INDEX));
                        }
                    }
                    break;
                case TRACK_AUDIO:
                    AudioTrackInfo audioTrackInfo;
                    for (int i = 1; i < audioTracksInfo.size(); i++) {
                        audioTrackInfo = (AudioTrackInfo) audioTracksInfo.get(i);
                        if (getIndexFromUniqueId(audioTrackInfo.getUniqueId(), GROUP_INDEX) == groupIndex) {
                            adaptiveTrackIndexesList.add(getIndexFromUniqueId(audioTrackInfo.getUniqueId(), TRACK_INDEX));
                        }
                    }
                    break;
            }

            adaptiveTrackIndexes = convertAdaptiveListToArray(adaptiveTrackIndexesList);
            override = new SelectionOverride(adaptiveTrackSelectionFactory, groupIndex, adaptiveTrackIndexes);
        } else {
            override = new SelectionOverride(FIXED_FACTORY, groupIndex, trackIndex);
        }

        return override;
    }


    /**
     * Actually doing the override acrion on the track.
     * @param rendererIndex - renderer index on which we want to apply the change.
     * @param override - the new selection with which we want to override the currently active track.
     */
    private void overrideTrack(int rendererIndex, SelectionOverride override) {
        //if renderer is disabled we will hide it.
        boolean isRendererDisabled = selector.getRendererDisabled(rendererIndex);
        selector.setRendererDisabled(rendererIndex, isRendererDisabled);
        if (override != null) {
            //actually change track.
            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            selector.setSelectionOverride(rendererIndex, trackGroups, override);
        } else {
            //clear all the selections if the override is null.
            selector.clearSelectionOverrides(rendererIndex);
        }
    }

    /**
     * Checks if adaptive track for the specified group was created.
     * @param uniqueId - unique id.
     * @param rendererIndex - renderer index.
     * @return - true, if adaptive {@link BaseTrackInfo} object already exist for this group.
     */
    private boolean adaptiveTrackInfoAlreadyExist(String uniqueId, int rendererIndex) {
        switch (rendererIndex){
            case TRACK_VIDEO:
                for(BaseTrackInfo trackInfo : videoTracksInfo){
                    if(trackInfo.getUniqueId().equals(uniqueId)){
                        return true;
                    }
                }
                break;
            case TRACK_AUDIO:
                for(BaseTrackInfo trackInfo : audioTracksInfo){
                    if(trackInfo.getUniqueId().equals(uniqueId)){
                        return true;
                    }
                }
                break;
            case TRACK_TEXT:
                for(BaseTrackInfo trackInfo : textTracksInfo){
                    if(trackInfo.getUniqueId().equals(uniqueId)){
                        return true;
                    }
                }
                break;
        }
        return false;
    }

    private int getIndexFromUniqueId(String uniqueId, int groupIndex) {
        String uniqueIdWithoutPrefix = removePrefix(uniqueId);
        String[] strArray = uniqueIdWithoutPrefix.split(Pattern.quote(","));
        if(strArray[groupIndex].equals(TRACK_ADAPTIVE)){
            return -1;
        }

        return Integer.valueOf(strArray[groupIndex]);
    }


    private String removePrefix(String uniqueId) {
        String[] strArray = uniqueId.split(":");
        //always return the second element of the splitString.
        return strArray[1];
    }

    private int[] convertAdaptiveListToArray(List<Integer> adaptiveTrackIndexesList) {
        int[] adaptiveTrackIndexes = new int[adaptiveTrackIndexesList.size()];
        for (int i = 0; i < adaptiveTrackIndexes.length; i++) {
            adaptiveTrackIndexes[i] = adaptiveTrackIndexesList.get(i);
        }

        return adaptiveTrackIndexes;
    }

    private boolean isFormatSupported(int rendererCount, int groupIndex, int trackIndex) {
        return mappedTrackInfo.getTrackFormatSupport(rendererCount, groupIndex, trackIndex)
                == RendererCapabilities.FORMAT_HANDLED;
    }

    public boolean isAdaptive(int rendererIndex, int groupIndex) {
        TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);
        return adaptiveTrackSelectionFactory != null
                && mappedTrackInfo.getAdaptiveSupport(rendererIndex, groupIndex, false)
                != RendererCapabilities.ADAPTIVE_NOT_SUPPORTED
                && trackGroupArray.get(groupIndex).length > 1;
    }

    public void setTracksReadyListener(ExoPlayerWrapper.TracksInfoReadyListener tracksReadyListener) {
        this.tracksReadyListener = tracksReadyListener;
    }


    public void release() {
        tracksReadyListener = null;
        videoTracksInfo.clear();
        audioTracksInfo.clear();
        textTracksInfo.clear();
    }

}
