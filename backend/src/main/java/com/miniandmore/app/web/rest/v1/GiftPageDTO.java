package com.miniandmore.app.web.rest.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import java.io.Serializable;

/**
 * Public gift page content stored on {@link com.miniandmore.app.domain.SiteSetting}.
 */
public class GiftPageDTO implements Serializable {

    @JsonProperty("gift_page_json")
    private String giftPageJson;

    public static GiftPageDTO from(SiteSettingDTO settings) {
        GiftPageDTO dto = new GiftPageDTO();
        if (settings == null) {
            return dto;
        }
        dto.setGiftPageJson(settings.getGiftPageJson());
        return dto;
    }

    public String getGiftPageJson() {
        return giftPageJson;
    }

    public void setGiftPageJson(String giftPageJson) {
        this.giftPageJson = giftPageJson;
    }
}
