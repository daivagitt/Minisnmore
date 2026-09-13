package com.miniandmore.app.web.rest.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import java.io.Serializable;

/**
 * Public about page content stored on {@link com.miniandmore.app.domain.SiteSetting}.
 */
public class AboutPageDTO implements Serializable {

    @JsonProperty("about_page_json")
    private String aboutPageJson;

    public static AboutPageDTO from(SiteSettingDTO settings) {
        AboutPageDTO dto = new AboutPageDTO();
        if (settings == null) {
            return dto;
        }
        dto.setAboutPageJson(settings.getAboutPageJson());
        return dto;
    }

    public String getAboutPageJson() {
        return aboutPageJson;
    }

    public void setAboutPageJson(String aboutPageJson) {
        this.aboutPageJson = aboutPageJson;
    }
}
