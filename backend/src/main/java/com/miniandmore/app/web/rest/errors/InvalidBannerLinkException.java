package com.miniandmore.app.web.rest.errors;

import java.io.Serial;

@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class InvalidBannerLinkException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidBannerLinkException() {
        super(
            "Link URL must be an on-site path starting with / (e.g. /shop). External URLs are not allowed.",
            "banner",
            "invalidlink"
        );
    }
}
