package com.miniandmore.app.service;

import java.io.Serial;

public class InvalidBannerLinkException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidBannerLinkException() {
        super(
            "Link URL must be an on-site path starting with / (e.g. /shop). External URLs are not allowed."
        );
    }
}
