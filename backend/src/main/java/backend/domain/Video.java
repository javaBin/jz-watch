package backend.domain;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.data.annotation.TypeDef;
import java.time.Instant;
import java.util.List;

@MappedEntity("videos")
public class Video {

    @Id
    private String id;
    private String conferenceId;
    private String title;

    @MappedProperty("abstract")
    private String abstractText;

    private String intendedAudience;
    private String vimeoId;
    private String language;
    private String format;
    private Integer talkLengthMinutes;
    private String room;
    private Instant startTime;
    private Instant endTime;

    @TypeDef(type = DataType.STRING_ARRAY)
    private List<String> keywords;

    @TypeDef(type = DataType.STRING_ARRAY)
    private List<String> aiKeywordsEn;

    @TypeDef(type = DataType.STRING_ARRAY)
    private List<String> aiKeywordsNo;

    private String titleEn;
    private String abstractEn;
    private String intendedAudienceEn;
    private String titleNo;
    private String abstractNo;
    private String intendedAudienceNo;

    private Integer baseScore;
    private Integer viewBoost;

    private String thumbnailUrl;
    private Integer videoDuration;

    private Instant createdAt;
    private Instant updatedAt;

    @TypeDef(type = DataType.JSON)
    @MappedProperty("sleeping_pill_data")
    private String sleepingPillData;

    @TypeDef(type = DataType.JSON)
    @MappedProperty("vimeo_oembed_data")
    private String vimeoOembedData;

    public Video() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConferenceId() {
        return conferenceId;
    }

    public void setConferenceId(String conferenceId) {
        this.conferenceId = conferenceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getIntendedAudience() {
        return intendedAudience;
    }

    public void setIntendedAudience(String intendedAudience) {
        this.intendedAudience = intendedAudience;
    }

    public String getVimeoId() {
        return vimeoId;
    }

    public void setVimeoId(String vimeoId) {
        this.vimeoId = vimeoId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Integer getTalkLengthMinutes() {
        return talkLengthMinutes;
    }

    public void setTalkLengthMinutes(Integer talkLengthMinutes) {
        this.talkLengthMinutes = talkLengthMinutes;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }


    public List<String> getAiKeywordsEn() {
        return aiKeywordsEn;
    }

    public void setAiKeywordsEn(List<String> aiKeywordsEn) {
        this.aiKeywordsEn = aiKeywordsEn;
    }

    public List<String> getAiKeywordsNo() {
        return aiKeywordsNo;
    }

    public void setAiKeywordsNo(List<String> aiKeywordsNo) {
        this.aiKeywordsNo = aiKeywordsNo;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getAbstractEn() {
        return abstractEn;
    }

    public void setAbstractEn(String abstractEn) {
        this.abstractEn = abstractEn;
    }

    public String getIntendedAudienceEn() {
        return intendedAudienceEn;
    }

    public void setIntendedAudienceEn(String intendedAudienceEn) {
        this.intendedAudienceEn = intendedAudienceEn;
    }

    public String getTitleNo() {
        return titleNo;
    }

    public void setTitleNo(String titleNo) {
        this.titleNo = titleNo;
    }

    public String getAbstractNo() {
        return abstractNo;
    }

    public void setAbstractNo(String abstractNo) {
        this.abstractNo = abstractNo;
    }

    public String getIntendedAudienceNo() {
        return intendedAudienceNo;
    }

    public void setIntendedAudienceNo(String intendedAudienceNo) {
        this.intendedAudienceNo = intendedAudienceNo;
    }

    public Integer getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(Integer baseScore) {
        this.baseScore = baseScore;
    }

    public Integer getViewBoost() {
        return viewBoost;
    }

    public void setViewBoost(Integer viewBoost) {
        this.viewBoost = viewBoost;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Integer getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(Integer videoDuration) {
        this.videoDuration = videoDuration;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSleepingPillData() {
        return sleepingPillData;
    }

    public void setSleepingPillData(String sleepingPillData) {
        this.sleepingPillData = sleepingPillData;
    }

    public String getVimeoOembedData() {
        return vimeoOembedData;
    }

    public void setVimeoOembedData(String vimeoOembedData) {
        this.vimeoOembedData = vimeoOembedData;
    }
}
