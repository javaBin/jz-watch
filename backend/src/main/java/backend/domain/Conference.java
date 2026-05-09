package backend.domain;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("conferences")
public class Conference {
    @Id
    private String id;
    private String slug;
    private String name;
    private Integer year;
    private Boolean completed;
    private Integer totalVideos;
    private Integer importedVideos;

    public Conference(String id, String slug, String name, Integer year) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.year = year;
        this.completed = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Boolean getCompleted() {
        return completed;
    }
    
    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getTotalVideos() {
        return totalVideos;
    }

    public void setTotalVideos(Integer totalVideos) {
        this.totalVideos = totalVideos;
    }

    public Integer getImportedVideos() {
        return importedVideos;
    }

    public void setImportedVideos(Integer importedVideos) {
        this.importedVideos = importedVideos;
    }
}
