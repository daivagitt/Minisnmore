package com.miniandmore.store.web;

import com.miniandmore.store.client.CatalogCoreClient;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StoreCatalogProxyResource {

    private final CatalogCoreClient catalogCoreClient;

    public StoreCatalogProxyResource(CatalogCoreClient catalogCoreClient) {
        this.catalogCoreClient = catalogCoreClient;
    }

    @RequestMapping(
        value = "/**",
        method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH }
    )
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        return catalogCoreClient.forward(request);
    }
}
