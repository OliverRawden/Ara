package tech.rawden.ara.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** JSON shape of {@code installers/latest.json} on the main branch. */
@JsonIgnoreProperties(ignoreUnknown = true)
class ReleaseMetadata {

    public String latestVersion;
    public String releaseDate;
    public String releaseNotes;
    public Map<String, String> downloads;
}