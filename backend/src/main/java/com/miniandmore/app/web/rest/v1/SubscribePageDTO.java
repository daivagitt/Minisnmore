package com.miniandmore.app.web.rest.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.miniandmore.app.service.dto.SiteSettingDTO;
import java.io.Serializable;

/**
 * Public subscribe page content stored on {@link com.miniandmore.app.domain.SiteSetting}.
 */
public class SubscribePageDTO implements Serializable {

    @JsonProperty("subscribe_hero_title")
    private String heroTitle;

    @JsonProperty("subscribe_hero_subtitle")
    private String heroSubtitle;

    @JsonProperty("subscribe_testimonial")
    private String testimonial;

    @JsonProperty("subscribe_badges_json")
    private String badgesJson;

    @JsonProperty("subscribe_plans_json")
    private String plansJson;

    @JsonProperty("subscribe_features_json")
    private String featuresJson;

    public static SubscribePageDTO from(SiteSettingDTO settings) {
        SubscribePageDTO dto = new SubscribePageDTO();
        if (settings == null) {
            return dto;
        }
        dto.setHeroTitle(settings.getSubscribeHeroTitle());
        dto.setHeroSubtitle(settings.getSubscribeHeroSubtitle());
        dto.setTestimonial(settings.getSubscribeTestimonial());
        dto.setBadgesJson(settings.getSubscribeBadgesJson());
        dto.setPlansJson(settings.getSubscribePlansJson());
        dto.setFeaturesJson(settings.getSubscribeFeaturesJson());
        return dto;
    }

    public String getHeroTitle() {
        return heroTitle;
    }

    public void setHeroTitle(String heroTitle) {
        this.heroTitle = heroTitle;
    }

    public String getHeroSubtitle() {
        return heroSubtitle;
    }

    public void setHeroSubtitle(String heroSubtitle) {
        this.heroSubtitle = heroSubtitle;
    }

    public String getTestimonial() {
        return testimonial;
    }

    public void setTestimonial(String testimonial) {
        this.testimonial = testimonial;
    }

    public String getBadgesJson() {
        return badgesJson;
    }

    public void setBadgesJson(String badgesJson) {
        this.badgesJson = badgesJson;
    }

    public String getPlansJson() {
        return plansJson;
    }

    public void setPlansJson(String plansJson) {
        this.plansJson = plansJson;
    }

    public String getFeaturesJson() {
        return featuresJson;
    }

    public void setFeaturesJson(String featuresJson) {
        this.featuresJson = featuresJson;
    }
}
